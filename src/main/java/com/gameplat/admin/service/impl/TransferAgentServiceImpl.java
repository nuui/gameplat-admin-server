package com.gameplat.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.gameplat.admin.config.SysTheme;
import com.gameplat.admin.mapper.MemberMapper;
import com.gameplat.admin.mapper.TransferAgentMapper;
import com.gameplat.admin.model.bean.GameBetRecordSearchBuilder;
import com.gameplat.admin.model.vo.AgentInfoVo;
import com.gameplat.admin.model.vo.BetRecord;
import com.gameplat.admin.service.TransferAgentService;
import com.gameplat.base.common.constant.ContextConstant;
import com.gameplat.common.util.MathUtils;
import com.gameplat.elasticsearch.service.IBaseElasticsearchService;
import com.gameplat.model.entity.game.GameBetRecord;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.FieldSortBuilder;
import org.elasticsearch.search.sort.SortBuilder;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TransferAgentServiceImpl implements TransferAgentService {

  @Autowired private TransferAgentMapper transferAgentMapper;

  @Autowired private IBaseElasticsearchService baseElasticsearchService;

  @Autowired private SysTheme sysTheme;

  @Resource private ElasticsearchRestTemplate elasticsearchTemplate;

  @Autowired private MemberMapper memberMapper;

  @Autowired private StringRedisTemplate stringRedisTemplate;

  @Override
  @Async
  public void transferData(
      String lockKey, String account, String originSuperPath, String newSuperPath) {
    try {
      log.info("转移代理开始转移数据--旧的代理路径{}---新的代理路径{}", originSuperPath, newSuperPath);
      // 1.转移活动
      Integer integer = transferAgentMapper.transferActivityRecord(account, originSuperPath);
      log.info("转移活动数据完毕{}条", integer);
      // 2.资金流水
      Integer integer1 = transferAgentMapper.transferMemberBill(account, originSuperPath);
      log.info("转移资金流水完毕{}条", integer1);
      // 3.游戏日报
      Integer integer2 = transferAgentMapper.transferGameReport(account, originSuperPath);
      log.info("转移游戏日报完毕{}条", integer2);
      // 4.会员日报
      Integer integer3 = transferAgentMapper.transferMemberReport(account, originSuperPath);
      log.info("转移会员日报完毕{}条", integer3);
      // 5.充值订单
      Integer integer4 = transferAgentMapper.transferRechargeRecord(account, originSuperPath);
      log.info("转移充值订单完毕{}条", integer4);
      // 6.提现订单
      Integer integer5 = transferAgentMapper.transferWithdrawRecord(account, originSuperPath);
      log.info("转移提现订单完毕{}条", integer5);
      // 7.返水订单
      Integer integer6 = transferAgentMapper.transferGameRebateReport(account, originSuperPath);
      log.info("转移返水订单完毕{}条", integer6);
      // 8.VIP福利记录
      Integer integer7 = transferAgentMapper.transferWealRecord(account, originSuperPath);
      log.info("转移VIP福利记录完毕{}条", integer7);
      // 9.充提记录
      Integer integer8 = transferAgentMapper.transferRwRecord(account, originSuperPath);
      log.info("转移充提记录完毕{}条", integer8);
      log.info("转移代理基础数据完毕--旧的代理路径{}---新的代理路径{}", originSuperPath, newSuperPath);
      // 9.转移ES注单
      this.transferEsBetRecord(originSuperPath);
    } catch (Exception e) {
      log.info("转代理转移数据失败{}-{}-{}", account, originSuperPath, newSuperPath);
    } finally {
      stringRedisTemplate.delete(lockKey);
    }
  }

  public void transferEsBetRecord(String originSuperPath) {
    List<GameBetRecord> resultList = new ArrayList();
      do {
        String tenantCode = sysTheme.getTenantCode();
        String indexName = ContextConstant.ES_INDEX.BET_RECORD_ + tenantCode;
        BoolQueryBuilder builder = QueryBuilders.boolQuery();
      builder.must(QueryBuilders.matchPhraseQuery("superPath", "*" + originSuperPath + "*"));
      SortBuilder<FieldSortBuilder> sortBuilder =
          SortBuilders.fieldSort(GameBetRecordSearchBuilder.convertTimeType(1))
              .order(SortOrder.DESC);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(builder);
      searchSourceBuilder.sort(sortBuilder);
      searchSourceBuilder.size(1000);
        SearchRequest searchRequest = new SearchRequest(indexName);
        searchRequest.source(searchSourceBuilder);
        log.info("转代理查询用户注单 DSL语句为：{}", searchRequest.source().toString());
        resultList =
            baseElasticsearchService.searchDocList(
                indexName, searchSourceBuilder, GameBetRecord.class);
        if (CollectionUtil.isNotEmpty(resultList)) {
          log.info("开始更新ES注单记录的代理路径={}", originSuperPath);
          Map<String, AgentInfoVo> agentInfoVoMap = new HashMap<>();
          List<BetRecord> listRecord = new ArrayList<>();
          for (GameBetRecord gameBetRecord : resultList) {
            if (StrUtil.isBlank(gameBetRecord.getAccount())) {
              continue;
            }
            AgentInfoVo agentInfoVo = agentInfoVoMap.get(gameBetRecord.getAccount());
            if (BeanUtil.isEmpty(agentInfoVo)) {
              AgentInfoVo queryInfo = memberMapper.queryAgentInfo(gameBetRecord.getAccount());
              if (BeanUtil.isEmpty(queryInfo)) {
                continue;
              }
              agentInfoVoMap.put(gameBetRecord.getAccount(), queryInfo);
              gameBetRecord.setParentName(queryInfo.getParentName());
              gameBetRecord.setSuperPath(queryInfo.getAgentPath());
            } else {
              gameBetRecord.setParentName(agentInfoVo.getParentName());
              gameBetRecord.setSuperPath(agentInfoVo.getAgentPath());
            }

            BetRecord betRecord = new BetRecord();
            BeanUtil.copyProperties(gameBetRecord, betRecord);
          Long betAmountLong =
              MathUtils.multiply1000(
                  betRecord.getBetAmount() != null ? betRecord.getBetAmount().toString() : "0");
          Long validAmountLong =
              MathUtils.multiply1000(
                  betRecord.getValidAmount() != null ? betRecord.getValidAmount().toString() : "0");
          Long winAmountLong =
              MathUtils.multiply1000(
                  betRecord.getWinAmount() != null ? betRecord.getWinAmount().toString() : "0");
          betRecord.setBetAmount(BigDecimal.valueOf(betAmountLong));
          betRecord.setValidAmount(BigDecimal.valueOf(validAmountLong));
          betRecord.setWinAmount(BigDecimal.valueOf(winAmountLong));
            listRecord.add(betRecord);
          }

          if (CollectionUtil.isNotEmpty(listRecord)) {
            elasticsearchTemplate.save(
                listRecord, IndexCoordinates.of(ContextConstant.ES_INDEX.BET_RECORD_ + tenantCode));
          }
        }
      } while (CollectionUtil.isNotEmpty(resultList));
  }
}
