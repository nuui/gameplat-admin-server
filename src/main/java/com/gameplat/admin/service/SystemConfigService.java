package com.gameplat.admin.service;

import com.gameplat.admin.model.dto.AgentContacaDTO;
import com.gameplat.admin.model.dto.EmailTestDTO;
import com.gameplat.admin.model.dto.OperSystemConfigDTO;
import com.gameplat.admin.model.vo.AgentContacaVO;
import com.gameplat.common.model.bean.EmailConfig;
import com.gameplat.model.entity.sys.SysDictData;

import java.util.List;

public interface SystemConfigService {

  List<AgentContacaVO> findAgentContacaList();

  void updateAgentContaca(AgentContacaDTO agentContacaDTO);

  void delAgentContaca(Long id);

  List<SysDictData> findList(String dictType);

  void updateConfig(String dictType, List<SysDictData> dictDataList);

  void configDataEdit(OperSystemConfigDTO dto);

  EmailConfig findEmailConfig();

  void updateEmail(EmailConfig emailConfig);

  void testSendEmail(EmailTestDTO dto);
}
