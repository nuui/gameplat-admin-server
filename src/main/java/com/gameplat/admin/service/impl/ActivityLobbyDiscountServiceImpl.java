package com.gameplat.admin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gameplat.admin.convert.ActivityLobbyDiscountConvert;
import com.gameplat.admin.mapper.ActivityLobbyDiscountDao;
import com.gameplat.admin.model.domain.ActivityLobbyDiscount;
import com.gameplat.admin.model.vo.ActivityLobbyDiscountVO;
import com.gameplat.admin.service.ActivityLobbyDiscountService;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ActivityLobbyDiscountServiceImpl extends ServiceImpl<ActivityLobbyDiscountDao, ActivityLobbyDiscount>
        implements ActivityLobbyDiscountService {

    @Autowired
    private ActivityLobbyDiscountConvert activityLobbyDiscountConvert;


    @Override
    public int saveBatch(List<ActivityLobbyDiscount> activityLobbyDiscounts) {
        return this.saveBatch(activityLobbyDiscounts);
    }

    @Override
    public List<ActivityLobbyDiscountVO> listByActivityLobbyId(Long activityLobbyId) {
        if (activityLobbyId == null || activityLobbyId == 0) {
            return new ArrayList<>();
        }

        List<ActivityLobbyDiscountVO> activityLobbyDiscountVOList = new ArrayList<>();
        List<ActivityLobbyDiscount> activityLobbyDiscounts = this.lambdaQuery()
                .eq(ActivityLobbyDiscount::getLobbyId, activityLobbyId).list();
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
    public void saveBatchLobbyDiscount(List<ActivityLobbyDiscount> activityLobbyDiscountList) {
        this.saveBatch(activityLobbyDiscountList);
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
