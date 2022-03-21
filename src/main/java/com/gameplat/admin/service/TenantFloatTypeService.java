package com.gameplat.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gameplat.admin.model.vo.TenantFloatTypeVo;
import com.gameplat.model.entity.setting.TenantFloatSetting;
import com.gameplat.model.entity.setting.TenantFloatType;

import java.util.List;

public interface TenantFloatTypeService extends IService<TenantFloatType> {

  /**
   * 查询游戏浮窗类型列表
   *
   * @param tenantFloatTypeVo 游戏浮窗类型
   * @return 游戏浮窗类型集合
   */
  List<TenantFloatTypeVo> selectSysFloatTypeList(TenantFloatTypeVo tenantFloatTypeVo);

  /**
   * 新增游戏浮窗类型
   *
   * @param tenantFloatSetting 游戏浮窗类型
   */
  void insertSysFloat(TenantFloatSetting tenantFloatSetting);

  void updateFloat(TenantFloatSetting tenantFloatSetting);

  void updateBatch(List<TenantFloatSetting> tenantFloatSettings);

  void updateFloatType(TenantFloatType tenantFloatType);

  void updateShowPosition(String showPositions);
}
