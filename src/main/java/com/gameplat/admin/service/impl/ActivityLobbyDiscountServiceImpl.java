package com.gameplat.admin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gameplat.admin.convert.ActivityLobbyDiscountConvert;
import com.gameplat.admin.mapper.ActivityLobbyDiscountMapper;
import com.gameplat.admin.model.domain.ActivityLobbyDiscount;
import com.gameplat.admin.model.vo.ActivityLobbyDiscountVO;
import com.gameplat.admin.service.ActivityLobbyDiscountService;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 活动大厅业务
 *
 * @author kenvin
 */
@Service
public class ActivityLobbyDiscountServiceImpl
    extends ServiceImpl<ActivityLobbyDiscountMapper, ActivityLobbyDiscount>
    implements ActivityLobbyDiscountService {

  @Autowired private ActivityLobbyDiscountConvert activityLobbyDiscountConvert;

  @Override
  public List<ActivityLobbyDiscountVO> listByActivityLobbyId(Long activityLobbyId) {
    if (activityLobbyId == null || activityLobbyId == 0) {
      return new ArrayList<>();
    }

    List<ActivityLobbyDiscountVO> activityLobbyDiscountVOList = new ArrayList<>();
    List<ActivityLobbyDiscount> activityLobbyDiscounts =
        this.lambdaQuery().eq(ActivityLobbyDiscount::getLobbyId, activityLobbyId).list();
    if (CollectionUtils.isNotEmpty(activityLobbyDiscounts)) {
      for (ActivityLobbyDiscount activityLobbyDiscount : activityLobbyDiscounts) {
        activityLobbyDiscountVOList.add(activityLobbyDiscountConvert.toVO(activityLobbyDiscount));
      }
    }
    return activityLobbyDiscountVOList;
  }

  @Override
  public void updateBatchLobbyDiscount(List<ActivityLobbyDiscount> activityLobbyDiscountList) {
    this.updateBatchById(activityLobbyDiscountList);
  }

  @Override
  public boolean saveBatchLobbyDiscount(List<ActivityLobbyDiscount> activityLobbyDiscountList) {
    return this.saveBatch(activityLobbyDiscountList);
  }

  @Override
  public void deleteBatchLobbyDiscount(List<ActivityLobbyDiscount> deleteList) {
    if (CollectionUtils.isNotEmpty(deleteList)) {
      for (ActivityLobbyDiscount activityLobbyDiscount : deleteList) {
        this.removeById(activityLobbyDiscount.getLobbyDiscountId());
      }
    }
  }
}
