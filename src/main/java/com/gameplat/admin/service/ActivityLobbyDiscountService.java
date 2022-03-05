package com.gameplat.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.gameplat.admin.model.vo.ActivityLobbyDiscountVO;
import com.gameplat.model.entity.activity.ActivityLobbyDiscount;

import java.util.List;

/**
 * 活动大厅业务查询
 *
 * @author kenvin
 */
public interface ActivityLobbyDiscountService extends IService<ActivityLobbyDiscount> {

  /**
   * 通过大厅id查询优惠信息列表
   *
   * @param activityLobbyId
   * @return
   */
  List<ActivityLobbyDiscountVO> listByActivityLobbyId(Long activityLobbyId);

  /**
   * 批量更新
   *
   * @param activityLobbyDiscountList
   */
  void updateBatchLobbyDiscount(List<ActivityLobbyDiscount> activityLobbyDiscountList);

  /**
   * 批量保存
   *
   * @param activityLobbyDiscountList
   * @return
   */
  boolean saveBatchLobbyDiscount(List<ActivityLobbyDiscount> activityLobbyDiscountList);

  /**
   * 批量删除
   *
   * @param deleteList
   */
  void deleteBatchLobbyDiscount(List<ActivityLobbyDiscount> deleteList);
}
