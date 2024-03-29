package com.gameplat.admin.service.impl;

import cn.afterturn.easypoi.excel.ExcelExportUtil;
import cn.afterturn.easypoi.excel.entity.ExportParams;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DateTime;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gameplat.admin.config.SysTheme;
import com.gameplat.admin.mapper.GameBetDailyReportMapper;
import com.gameplat.admin.model.bean.ActivityStatisticItem;
import com.gameplat.admin.model.dto.GameBetDailyReportQueryDTO;
import com.gameplat.admin.model.vo.*;
import com.gameplat.admin.service.GameBetDailyReportService;
import com.gameplat.admin.service.GamePlatformService;
import com.gameplat.admin.service.MemberService;
import com.gameplat.base.common.constant.ContextConstant;
import com.gameplat.base.common.exception.ServiceException;
import com.gameplat.base.common.util.DateUtil;
import com.gameplat.base.common.util.DateUtils;
import com.gameplat.base.common.util.StringUtils;
import com.gameplat.common.enums.GameKindEnum;
import com.gameplat.common.enums.GamePlatformEnum;
import com.gameplat.common.enums.SettleStatusEnum;
import com.gameplat.common.enums.UserTypes;
import com.gameplat.common.lang.Assert;
import com.gameplat.common.util.MathUtils;
import com.gameplat.model.entity.game.GameBetDailyReport;
import com.gameplat.model.entity.game.GamePlatform;
import com.gameplat.model.entity.member.Member;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletResponse;

import jodd.bean.BeanCopy;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.HttpAsyncResponseConsumerFactory;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.ParsedSum;
import org.elasticsearch.search.aggregations.metrics.SumAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(isolation = Isolation.DEFAULT, rollbackFor = Throwable.class)
public class GameBetDailyReportServiceImpl
    extends ServiceImpl<GameBetDailyReportMapper, GameBetDailyReport>
    implements GameBetDailyReportService {

  @Autowired public RestHighLevelClient restHighLevelClient;
  @Autowired private MemberService memberService;
  @Autowired private GameBetDailyReportMapper gameBetDailyReportMapper;
  @Autowired private SysTheme sysTheme;
  @Autowired private GamePlatformService gamePlatformService;

  @Override
  public PageDtoVO queryPage(Page<GameBetDailyReport> page, GameBetDailyReportQueryDTO dto) {
    PageDtoVO<GameBetDailyReport> pageDtoVO = new PageDtoVO();
    if (StringUtils.isNotBlank(dto.getSuperAccount())) {
      Member member = memberService.getByAccount(dto.getSuperAccount()).orElse(null);
      Assert.notNull(member, "用户不存在");
      dto.setUserPaths(member.getSuperPath());
      // 是否代理账号
      if (member.getUserType().equals(UserTypes.AGENT.value())) {
        dto.setAccount(null);
      }
    }
    QueryWrapper<GameBetDailyReport> queryWrapper = Wrappers.query();
    fillQueryWrapper(dto, queryWrapper);
    queryWrapper.orderByDesc(Lists.newArrayList("stat_time", "id"));

    Page<GameBetDailyReport> result = gameBetDailyReportMapper.selectPage(page, queryWrapper);

    QueryWrapper<GameBetDailyReport> queryOne = Wrappers.query();
    queryOne.select(
        "sum(bet_amount) as bet_amount,sum(valid_amount) as valid_amount,sum(win_amount) as win_amount");
    fillQueryWrapper(dto, queryOne);
    GameBetDailyReport gameBetDailyReport = gameBetDailyReportMapper.selectOne(queryOne);
    Map<String, Object> otherData = new HashMap<>();
    otherData.put("totalData", gameBetDailyReport);
    pageDtoVO.setPage(result);
    pageDtoVO.setOtherData(otherData);
    return pageDtoVO;
  }

  private void fillQueryWrapper(
      GameBetDailyReportQueryDTO dto, QueryWrapper<GameBetDailyReport> queryWrapper) {
    queryWrapper.eq(ObjectUtils.isNotEmpty(dto.getAccount()), "account", dto.getAccount());
    if (StringUtils.isNotEmpty(dto.getUserPaths())) {
      queryWrapper.likeRight("user_paths", dto.getUserPaths());
    }
    if (StringUtils.isNotEmpty(dto.getPlatformCode())) {
      queryWrapper.in("platform_code", Arrays.asList(dto.getPlatformCode().split(",")));
    }
    if (ObjectUtils.isNotEmpty(dto.getGameKindList())) {
      queryWrapper.in("game_kind", dto.getGameKindList());
    }
    queryWrapper.eq(ObjectUtils.isNotEmpty(dto.getGameType()), "game_type", dto.getGameType());
    queryWrapper.apply(
        ObjectUtils.isNotEmpty(dto.getBeginTime()),
        "stat_time >= STR_TO_DATE({0}, '%Y-%m-%d')",
        dto.getBeginTime());
    queryWrapper.apply(
        ObjectUtils.isNotEmpty(dto.getEndTime()),
        "stat_time <= STR_TO_DATE({0}, '%Y-%m-%d')",
        dto.getEndTime());
  }

  @Override
  public void saveGameBetDailyReport(String statTime, GamePlatform gamePlatform) {
    log.info(
        "{}[{}],statTime:[{}]> Start save game_bet_daily_report",
        gamePlatform.getName(),
        gamePlatform.getCode(),
        statTime);
    // 获取某一游戏平台当天的统计数据 结算时间为传入时间， 已结算的
    BoolQueryBuilder builder = QueryBuilders.boolQuery();
    builder.must(QueryBuilders.termQuery("tenant.keyword", sysTheme.getTenantCode()));
    builder.must(QueryBuilders.termQuery("settle", SettleStatusEnum.YES.getValue()));
    builder.must(QueryBuilders.termQuery("platformCode.keyword", gamePlatform.getCode()));
    Date statDate = DateUtils.parseDate(statTime, DateUtils.DATE_PATTERN);
    DateTime beginTime = cn.hutool.core.date.DateUtil.beginOfDay(statDate);
    DateTime endTime = cn.hutool.core.date.DateUtil.endOfDay(statDate);
    builder.filter(
        QueryBuilders.rangeQuery("settleTime").gte(beginTime.getTime()).lte(endTime.getTime()));

    // 统计条数
    CountRequest countRequest =
        new CountRequest(ContextConstant.ES_INDEX.BET_RECORD_ + sysTheme.getTenantCode());
    countRequest.query(builder);
    try {
      RequestOptions.Builder optionsBuilder = RequestOptions.DEFAULT.toBuilder();
      optionsBuilder.setHttpAsyncResponseConsumerFactory(
          new HttpAsyncResponseConsumerFactory.HeapBufferedResponseConsumerFactory(31457280));
      CountResponse countResponse = restHighLevelClient.count(countRequest, optionsBuilder.build());
      long sumCount = countResponse.getCount();
      if (sumCount > 0) {
        log.info(
            "{}[{}],statTime:[{}] > game_bet_daily_report bet record data size:[{}]",
            gamePlatform.getName(),
            gamePlatform.getCode(),
            statTime,
            sumCount);
        // 先删除统计数据
        LambdaUpdateWrapper<GameBetDailyReport> updateWrapper = Wrappers.lambdaUpdate();
        updateWrapper
            .eq(GameBetDailyReport::getPlatformCode, gamePlatform.getCode())
            .eq(GameBetDailyReport::getStatTime, statTime);
        int deleted = gameBetDailyReportMapper.delete(updateWrapper);
        log.info(
            "{}[{}],statTime:[{}] > game_bet_daily_report delete exists data size:[{}]",
            gamePlatform.getName(),
            gamePlatform.getCode(),
            statTime,
            deleted);
        log.info(
            "{}[{}],statTime:[{}]> Start save game_bet_daily_report",
            gamePlatform.getName(),
            gamePlatform.getCode(),
            statTime);
        // 查询出所有数据
        List<GameBetDailyReport> list = new ArrayList<>();
        SearchRequest searchRequest =
            new SearchRequest(ContextConstant.ES_INDEX.BET_RECORD_ + sysTheme.getTenantCode());
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        TermsAggregationBuilder accountGroup =
            AggregationBuilders.terms("accountGroup").field("account.keyword").size((int) sumCount);
        TermsAggregationBuilder gameKindGroup =
            AggregationBuilders.terms("gameKindGroup").field("gameKind.keyword");

        SumAggregationBuilder sumBetAmount =
            AggregationBuilders.sum("betAmount").field("betAmount");
        SumAggregationBuilder sumValidAmount =
            AggregationBuilders.sum("validAmount").field("validAmount");
        SumAggregationBuilder sumWinAmount =
            AggregationBuilders.sum("winAmount").field("winAmount");
        SumAggregationBuilder sumLoseWin = AggregationBuilders.sum("loseWin").field("loseWin");

        gameKindGroup.subAggregation(sumBetAmount);
        gameKindGroup.subAggregation(sumValidAmount);
        gameKindGroup.subAggregation(sumWinAmount);
        gameKindGroup.subAggregation(sumLoseWin);
        accountGroup.subAggregation(gameKindGroup);

        searchSourceBuilder.size(0);
        searchSourceBuilder.query(builder);
        searchSourceBuilder.aggregation(accountGroup);
        searchRequest.source(searchSourceBuilder);
        log.info("resetGameBetDailyReport DSL语句为：{}", searchRequest.source().toString());
        SearchResponse searchResponse =
            restHighLevelClient.search(searchRequest, optionsBuilder.build());

        Terms accountTerms = searchResponse.getAggregations().get("accountGroup");
        for (Terms.Bucket bucket : accountTerms.getBuckets()) {
          String account = bucket.getKeyAsString();
          log.info("resetGameBetDailyReport account:{} ", account);
          MemberInfoVO memberInfo = memberService.getMemberInfo(account);
          Member member =
              memberService
                  .getByAccount(account)
                  .orElseThrow(() -> new ServiceException("未找到对应会员信息"));
          Terms gameKindTerms = bucket.getAggregations().get("gameKindGroup");
          for (Terms.Bucket bucket2 : gameKindTerms.getBuckets()) {
            String gameKind = bucket2.getKeyAsString();
            long count = bucket2.getDocCount();
            BigDecimal betAmount =
                MathUtils.divide1000(
                    ((ParsedSum) bucket2.getAggregations().get("betAmount")).getValue());
            BigDecimal validAmount =
                MathUtils.divide1000(
                    ((ParsedSum) bucket2.getAggregations().get("validAmount")).getValue());
            BigDecimal winAmount =
                MathUtils.divide1000(
                    ((ParsedSum) bucket2.getAggregations().get("winAmount")).getValue());
            long loseWin =
                Convert.toLong(((ParsedSum) bucket2.getAggregations().get("loseWin")).getValue());
            // 保存生成数据
            GameBetDailyReport gameBetDailyReport = new GameBetDailyReport();
            gameBetDailyReport.setMemberId(memberInfo.getId());
            gameBetDailyReport.setAccount(memberInfo.getAccount());
            gameBetDailyReport.setRealName(memberInfo.getRealName());
            gameBetDailyReport.setSuperId(member.getParentId());
            gameBetDailyReport.setSuperAccount(member.getParentName());
            gameBetDailyReport.setUserPaths(memberInfo.getSuperPath());
            gameBetDailyReport.setUserType(memberInfo.getUserType());
            gameBetDailyReport.setPlatformCode(gameKind.split("_")[0]);
            gameBetDailyReport.setGameKind(gameKind);
            gameBetDailyReport.setGameType(gameKind.split("_")[1]);
            gameBetDailyReport.setBetAmount(betAmount);
            gameBetDailyReport.setValidAmount(validAmount);
            gameBetDailyReport.setWinAmount(winAmount);
            gameBetDailyReport.setBetCount(count);
            gameBetDailyReport.setWinCount(loseWin);
            gameBetDailyReport.setStatTime(statTime);
            list.add(gameBetDailyReport);
          }
        }
        gameBetDailyReportMapper.insertGameBetDailyReport(list);
        log.info(
            "{}[{}],statTime:[{}] > game_bet_daily_report generate data size:[{}]",
            gamePlatform.getName(),
            gamePlatform.getCode(),
            statTime,
            list.size());
      } else {
        log.info(
            "{}[{}],statTime:[{}]> no data save to game_bet_daily_report",
            gamePlatform.getName(),
            gamePlatform.getCode(),
            statTime);
      }
      log.info(
          "{}[{}],statTime:[{}]> End save game_bet_daily_report",
          gamePlatform.getName(),
          gamePlatform.getCode(),
          statTime);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  @Override
  public List<GameReportVO> queryReportList(GameBetDailyReportQueryDTO dto) {
    return gameBetDailyReportMapper.queryReportList(dto);
  }

  @Override
  public void exportGameKindReport(GameBetDailyReportQueryDTO dto, HttpServletResponse response) {

    log.info("请求导出游戏大类数据，请求参数{}", dto);
    List<GameReportVO> result = gameBetDailyReportMapper.queryReportList(dto);
    String title = String.format("%s至%s游戏大类数据", dto.getBeginTime(), dto.getEndTime());
    ExportParams exportParams = new ExportParams(title, "游戏大类数据");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename = gameKindReport.xls");

    List<GameKindReportVO> kindReportList = new ArrayList<>();
    result.forEach(o ->{
      GameKindReportVO reportVO = new GameKindReportVO();
      BeanUtils.copyProperties(o, reportVO);
      reportVO.setCompanyAmount(reportVO.getCompanyAmount().negate());
      kindReportList.add(reportVO);
    });

    try (Workbook workbook = ExcelExportUtil.exportExcel(exportParams, GameKindReportVO.class, kindReportList)) {
      workbook.write(response.getOutputStream());
    } catch (IOException e) {
      log.error("请求导出游戏投注日报表报错", e);
    }
  }

  @Override
  public PageDtoVO<GameBetReportVO> queryBetReportList(
      Page<GameBetDailyReportQueryDTO> page, GameBetDailyReportQueryDTO dto) {
    Page<GameBetReportVO> gameBetReportVOPage =
        gameBetDailyReportMapper.querybetReportList(page, dto);
    PageDtoVO<GameBetReportVO> pageDtoVO = new PageDtoVO<>();
    Map<String, Object> map = gameBetDailyReportMapper.querySumReport(dto);
    pageDtoVO.setPage(gameBetReportVOPage);
    pageDtoVO.setOtherData(map);
    return pageDtoVO;
  }

  @Override
  public List<ActivityStatisticItem> getGameReportInfo(Map map) {
    List<ActivityStatisticItem> activityStatisticItemVOList =
      gameBetDailyReportMapper.getGameReportInfo(map);
    // 连续体育打码天数和连续彩票打码天数需要返回活动期间内用户的打码日期集合，用于后续业务计算最大的连续打码天数
    if (map.get("statisItem") != null && StringUtils.isNotEmpty(activityStatisticItemVOList)) {
      if ((Integer) map.get("statisItem") == 8) {
        List<ActivityStatisticItem> gameDmlDateList =
          gameBetDailyReportMapper.findGameDmlDateList(map);
        if (ObjectUtils.isEmpty(gameDmlDateList)) {
          throw new ServiceException("没有有效的充值天数");
        }
        // 将逗号分隔的日期String转成List<Date>
        for (ActivityStatisticItem gameDmlDate : gameDmlDateList) {
          if (StringUtils.isNotEmpty(gameDmlDate.getGameCountDates())) {
            List<String> dateList = Arrays.asList(gameDmlDate.getGameCountDates().split(","));
            // 去重
            List<String> list = dateList.stream().distinct().collect(Collectors.toList());
            Collections.sort(list);
            List<Date> dates = new ArrayList<>();
            for (String date : list) {
              dates.add(DateUtil.strToDate(date, DateUtil.YYYY_MM_DD));
            }
            gameDmlDate.setGameCountDateList(dates);
          }
        }

        for (ActivityStatisticItem gameDmlDate : gameDmlDateList) {
          for (ActivityStatisticItem activityStatisticItemVO : activityStatisticItemVOList) {
            if (activityStatisticItemVO.getUserName().equals(gameDmlDate.getUserName())) {
              activityStatisticItemVO.setGameCountDateList(gameDmlDate.getGameCountDateList());
            }
          }
        }

        activityStatisticItemVOList = activityStatisticItemVOList.stream().filter(item ->
          ObjectUtils.isNotEmpty(item.getGameCountDateList())).collect(Collectors.toList());
      }
    }
    return activityStatisticItemVOList;
  }

  @Override
  public List<GameReportVO> queryGamePlatformReport(GameBetDailyReportQueryDTO dto) {
    if (StringUtils.isBlank(dto.getBeginTime())) {
      String beginTime = DateUtil.getDateToString(new Date());
      dto.setBeginTime(beginTime);
    }
    if (StringUtils.isBlank(dto.getEndTime())) {
      String endTime = DateUtil.getDateToString(new Date());
      dto.setEndTime(endTime);
    }
    if (StringUtils.isNotBlank(dto.getSuperAccount())) {
      Member member = memberService.getAgentByAccount(dto.getSuperAccount()).orElse(null);
      Assert.notNull(member, "代理不存在");
      dto.setUserPaths(member.getSuperPath());
    }
    return gameBetDailyReportMapper.queryGamePlatformReport(dto);
  }

  // 获取达到有效投注金额的会员账号
  @Override
  public List<String> getSatisfyBetAccount(String minBetAmount, String startTime, String endTime) {

    return gameBetDailyReportMapper.getSatisfyBetAccount(minBetAmount, startTime, endTime);
  }

  @Override
  public List<String> getWealVipValid(Integer type, String startTime, String endTime) {
    return gameBetDailyReportMapper.getWealVipValid(type, startTime, endTime);
  }

  @Override
  public void exportGamePlatformReport(
      GameBetDailyReportQueryDTO dto, HttpServletResponse response) {
    log.info("请求导出游戏平台数据参数：{}", JSONUtil.toJsonStr(dto));
    List<GameReportVO> gamePlatformReportList = this.queryGamePlatformReport(dto);
    Map<String, String> gamePlatformMap =
        gamePlatformService.list().stream()
            .collect(Collectors.toMap(GamePlatform::getCode, GamePlatform::getName));

    List<GameReportPlatformVO> reportPlatforms = new ArrayList<>();
    gamePlatformReportList.forEach(o ->{
      GameReportPlatformVO reportPlatform = new GameReportPlatformVO();
      BeanUtils.copyProperties(o, reportPlatform);
      reportPlatform.setPlatformName(gamePlatformMap.get(o.getPlatformCode()));
      reportPlatform.setCompanyAmount(reportPlatform.getCompanyAmount().negate());
      reportPlatforms.add(reportPlatform);
    });

    ExportParams exportParams =
        new ExportParams(
            String.format("%s至%s游戏平台数据", dto.getBeginTime(), dto.getEndTime()), "游戏平台数据");
    response.setHeader(
        HttpHeaders.CONTENT_DISPOSITION, "attachment;filename = gamePlatformReport.xls");
    try (Workbook workbook =
        ExcelExportUtil.exportExcel(exportParams, GameReportPlatformVO.class, reportPlatforms)) {
      workbook.write(response.getOutputStream());
    } catch (IOException e) {
      log.error("导出游戏投注记录报错", e);
    }
  }

  @Override
  public void exportGameBetDailyReport(HttpServletResponse response, GameBetDailyReportQueryDTO dto) throws Exception {
    log.info("请求导出游戏投注日报表参数：{}", dto);
    if (StringUtils.isNotBlank(dto.getSuperAccount())) {
      Member member = memberService.getByAccount(dto.getSuperAccount()).orElse(null);
      if (ObjectUtils.isEmpty(member)) {
        throw new Exception("用户不存在");
      }
      dto.setUserPaths(member.getSuperPath());
      // 是否代理账号
      if (UserTypes.AGENT.value().equals(member.getUserType())) {
        dto.setAccount(null);
      }
    }
    QueryWrapper<GameBetDailyReport> queryWrapper = Wrappers.query();
    fillQueryWrapper(dto, queryWrapper);
    queryWrapper.orderByDesc(Lists.newArrayList("stat_time", "id"));

    List<GameBetDailyReport> result = gameBetDailyReportMapper.selectList(queryWrapper);

    List<GameBetDailyReportVO> reportVOList = new ArrayList<>();
    result.forEach(o ->{
      GameBetDailyReportVO reportVO = new GameBetDailyReportVO();
      org.springframework.beans.BeanUtils.copyProperties(o, reportVO);
      reportVO.setGameKindName(GameKindEnum.getDescByCode(o.getGameKind()));
      reportVO.setPlatformName(GamePlatformEnum.getName(o.getPlatformCode()));
      reportVOList.add(reportVO);
    });
    String title = String.format("%s至%s游戏投注日报表数据", dto.getBeginTime(), dto.getEndTime());
    ExportParams exportParams = new ExportParams(title, "游戏平台数据");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename = gameBetDailyReport.xls");

    try (Workbook workbook = ExcelExportUtil.exportExcel(exportParams, GameBetDailyReportVO.class, reportVOList)) {
      workbook.write(response.getOutputStream());
    } catch (IOException e) {
      log.error("请求导出游戏投注日报表报错", e);
    }
  }



}
