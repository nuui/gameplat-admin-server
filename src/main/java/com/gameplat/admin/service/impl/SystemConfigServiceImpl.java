package com.gameplat.admin.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.alicp.jetcache.anno.CacheInvalidate;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.gameplat.admin.convert.AgentContacaConfigConvert;
import com.gameplat.admin.model.dto.AgentContactDTO;
import com.gameplat.admin.model.dto.EmailTestDTO;
import com.gameplat.admin.model.dto.OperDictDataDTO;
import com.gameplat.admin.service.SysDictDataService;
import com.gameplat.admin.service.SystemConfigService;
import com.gameplat.base.common.context.GlobalContextHolder;
import com.gameplat.base.common.json.JsonUtils;
import com.gameplat.common.constant.CachedKeys;
import com.gameplat.common.enums.DefaultEnums;
import com.gameplat.common.enums.DictDataEnum;
import com.gameplat.common.enums.DictTypeEnum;
import com.gameplat.common.util.Convert;
import com.gameplat.model.entity.AgentContacaConfig;
import com.gameplat.model.entity.sys.SysDictData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(isolation = Isolation.DEFAULT, rollbackFor = Throwable.class)
public class SystemConfigServiceImpl implements SystemConfigService {

  @Autowired private SysDictDataService dictDataService;

  @Autowired private AgentContacaConfigConvert agentContacaConfigConvert;

  @Override
  public void updateAgentContact(AgentContactDTO dto) {
    SysDictData dictData =
        dictDataService.getDictData(
            DictTypeEnum.AGENT_CONTACT_CONFIG.getValue(), DictDataEnum.AGENT_CONTACT.getLabel());
    List<AgentContacaConfig> contactList = null;
    if (ObjectUtil.isNotEmpty(dictData) && ObjectUtil.isNotEmpty(dictData.getDictValue())) {
      contactList =
              Optional.of(dictData)
                      .map(SysDictData::getDictValue)
                      .map(c -> JsonUtils.parse(c, new TypeReference<List<AgentContacaConfig>>() {
                      }))
                      .orElse(Collections.emptyList());
    }
    AgentContacaConfig agentContacaConfig = agentContacaConfigConvert.toEntity(dto);
    List<AgentContacaConfig> list = new ArrayList<>();
    boolean flag = false;
    if (CollectionUtils.isNotEmpty(contactList)) {
      for (AgentContacaConfig config : contactList) {
        if (config.getId().equals(dto.getId())) {
          flag = true;
          agentContacaConfig.setUpdateBy(GlobalContextHolder.getContext().getUsername());
          agentContacaConfig.setUpdateTime(String.valueOf(System.currentTimeMillis()));
          list.add(agentContacaConfig);
        } else { // 新增
          list.add(config);
        }
      }
      if (!flag) {
        long id = System.currentTimeMillis();
        agentContacaConfig.setId(id);
        agentContacaConfig.setCreateBy(GlobalContextHolder.getContext().getUsername());
        agentContacaConfig.setCreateTime(String.valueOf(System.currentTimeMillis()));
        list.add(agentContacaConfig);
      }
    } else {
      long id = System.currentTimeMillis();
      agentContacaConfig.setId(id);
      agentContacaConfig.setCreateBy(GlobalContextHolder.getContext().getUsername());
      agentContacaConfig.setCreateTime(String.valueOf(System.currentTimeMillis()));
      list.add(agentContacaConfig);
    }

    // 修改
    dictDataService.updateDictData(
            OperDictDataDTO.builder()
                    .id(dictData.getId())
                    .dictLabel(dictData.getDictLabel())
                    .dictType(dictData.getDictType())
                    .dictValue(JsonUtils.toJson(list))
                    .build());
  }

  @Override
  public void delAgentContact(String id) {

    SysDictData dictData =
        dictDataService.getDictData(
            DictTypeEnum.AGENT_CONTACT_CONFIG.getValue(), DictDataEnum.AGENT_CONTACT.getLabel());

    List<AgentContacaConfig> contactList =
        Optional.of(dictData)
            .map(SysDictData::getDictValue)
            .map(c -> JsonUtils.parse(c, new TypeReference<List<AgentContacaConfig>>() {}))
            .orElse(Collections.emptyList());
    if (CollectionUtils.isNotEmpty(contactList)) {
      log.info("删除前的数据大小：{}，删除id：{}", contactList.size(), id);
      List<AgentContacaConfig> list =
              contactList.stream().filter(ex -> !Convert.toStr(ex.getId()).equals(id)).collect(Collectors.toList());
      log.info("删除后的数据大小：{}，删除id：{}", list.size(), id);
      dictDataService.updateDictData(
          OperDictDataDTO.builder()
              .id(dictData.getId())
              .dictLabel(dictData.getDictLabel())
              .dictType(dictData.getDictType())
              .dictValue(JsonUtils.toJson(list))
              .build());
    }
  }

  @Override
  @CacheInvalidate(name = CachedKeys.DICT_DATA_CACHE, key = "#dictType")
  public void updateConfig(String dictType, List<SysDictData> dictDataList) {
    List<SysDictData> dictData = dictDataService.getDictDataByType(dictType);
    dictData.forEach(
        e -> {
          e.setIsDefault(DefaultEnums.NO.value());
          dictDataService.saveOrUpdate(this.replaceConfig(dictDataList, e));
        });
  }

  @Override
  public List<SysDictData> findList(String dictType) {
    return dictDataService.getDictDataByType(dictType);
  }

  @Override
  public void updateConfig(String type, Map<String, Object> params) {
    params.forEach(
        (label, value) ->
            dictDataService.updateByTypeAndLabel(
                SysDictData.builder()
                    .dictType(type)
                    .dictLabel(label)
                    .dictValue(Objects.nonNull(value) ? value.toString() : null)
                    .build()));
  }

  @Async
  @Override
  public void testSendEmail(EmailTestDTO dto) {
    // EmailSender sender = EmailSender.buildWithConfig(this.findEmailConfig());
    // sender.send(dto.getSubject(), dto.getContent(), dto.getTo());
  }

  private SysDictData replaceConfig(List<SysDictData> dictDataList, SysDictData entity) {
    return dictDataList.stream()
        .filter(c -> c.getId().equals(entity.getId()))
        .findAny()
        .orElse(entity);
  }
}
