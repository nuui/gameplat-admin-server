package com.gameplat.admin.controller.open.thirdParty;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.gameplat.admin.enums.DictTypeEnum;
import com.gameplat.admin.model.domain.SysDictData;
import com.gameplat.admin.model.dto.OperDictDataDTO;
import com.gameplat.admin.model.dto.SysDictDataDTO;
import com.gameplat.admin.model.vo.DictDataVo;
import com.gameplat.admin.service.SysDictDataService;
import com.gameplat.common.exception.ServiceException;
import com.gameplat.common.util.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/thirdParty/rechVirtual")
public class RechVirtualController {

  @Autowired private SysDictDataService dictDataService;

  @GetMapping("/list")
  @PreAuthorize("hasAuthority('thirdParty:rechVirtual:list')")
  public IPage<DictDataVo> list(PageDTO<SysDictData> page, SysDictDataDTO dictData) {
    dictData.setDictType(DictTypeEnum.RECH_VIRTUAL.getKey());
    return dictDataService.selectDictDataList(page, dictData);
  }

  @PutMapping("/edit")
  @PreAuthorize("hasAuthority('thirdParty:rechVirtual:edit')")
  public void update(@RequestBody OperDictDataDTO dictData) {
    if (StringUtils.isNull(dictData.getId())) {
      throw new ServiceException("主键不正确");
    }
    if (StringUtils.isBlank(dictData.getDictValue())) {
      throw new ServiceException("值不能为空");
    }
    dictData.setDictLabel(JSONObject.parseObject(dictData.getDictValue()).getString("configKey"));
    dictData.setDictType(DictTypeEnum.RECH_VIRTUAL.getKey());
    dictDataService.updateDictData(dictData);
  }
}