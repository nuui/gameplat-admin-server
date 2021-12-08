package com.gameplat.admin.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gameplat.admin.convert.MemberGrowthStatisConvert;
import com.gameplat.admin.enums.LanguageEnum;
import com.gameplat.admin.mapper.MemberGrowthStatisMapper;
import com.gameplat.admin.model.domain.MemberGrowthLevel;
import com.gameplat.admin.model.domain.MemberGrowthStatis;
import com.gameplat.admin.model.dto.MemberGrowthStatisDTO;
import com.gameplat.admin.model.vo.MemberGrowthConfigVO;
import com.gameplat.admin.model.vo.MemberGrowthLevelVO;
import com.gameplat.admin.model.vo.MemberGrowthStatisVO;
import com.gameplat.admin.service.MemberGrowthLevelService;
import com.gameplat.admin.service.MemberGrowthStatisService;
import com.gameplat.base.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author lily
 * @description vip等级汇总业务处理层
 * @date 2021/11/24
 */

@Service
@RequiredArgsConstructor
public class MemberGrowthStatisServiceImpl extends ServiceImpl<MemberGrowthStatisMapper, MemberGrowthStatis> implements MemberGrowthStatisService {

    @Autowired
    private MemberGrowthStatisConvert statisConvert;
    @Autowired
    private MemberGrowthLevelService growthLevelService;
    @Autowired
    private MemberGrowthStatisMapper growthStatisMapper;

    @Override
    public IPage<MemberGrowthStatisVO> findStatisList(PageDTO<MemberGrowthStatis> page, MemberGrowthStatisDTO dto) {
    return
            this.lambdaQuery()
                    .like(ObjectUtils.isNotEmpty(dto.getUserName()), MemberGrowthStatis::getUserName, dto.getUserName())
                    .ge(ObjectUtils.isNotEmpty(dto.getStartTime()), MemberGrowthStatis::getCreateTime, dto.getStartTime())
                    .le(ObjectUtils.isNotEmpty(dto.getEndTime()), MemberGrowthStatis::getCreateTime, dto.getEndTime())
                    .orderByDesc(MemberGrowthStatis::getCreateTime)
                    .page(page)
                    .convert(statisConvert::toVo);
    }

    @Override
    public List<MemberGrowthStatis> findList(MemberGrowthStatisDTO dto) {
        return this.lambdaQuery()
                .like(ObjectUtils.isNotEmpty(dto.getUserName()), MemberGrowthStatis::getUserName, dto.getUserName())
                .ge(ObjectUtils.isNotEmpty(dto.getStartTime()), MemberGrowthStatis::getCreateTime, dto.getStartTime())
                .le(ObjectUtils.isNotEmpty(dto.getEndTime()), MemberGrowthStatis::getCreateTime, dto.getEndTime())
                .orderByDesc(MemberGrowthStatis::getCreateTime)
                .list();
    }


    /**
     * 处理随成长值 升级发消息相关操作
     */
    @Override
    public Integer dealUpLevel(Integer afterGrowth, MemberGrowthConfigVO memberGrowthConfig) {
        //todo 1.先获取所有成长值等级
        Integer limitLevel = memberGrowthConfig.getLimitLevel();
        if (limitLevel == null){
            limitLevel = 50;
        }
        List<MemberGrowthLevelVO> levels = growthLevelService.findList(limitLevel + 1, LanguageEnum.app_zh_CN.getCode());
        MemberGrowthLevelVO maxGrowthLevel = levels.get(levels.size() - 1);
        //如果比最大等级所需升级成长值还要大  则直接返回最大等级
        if (afterGrowth >= maxGrowthLevel.getGrowth()) {
            return maxGrowthLevel.getLevel();
        }
        for (int i = 0; i < levels.size(); i++) {
            if (afterGrowth < levels.get(i).getGrowth()) {
                return levels.get(i).getLevel();
            }
        }
        throw new ServiceException("计算成长等级失败！");
    }

    @Override
    public void insertOrUpdate(MemberGrowthStatis userGrowthStatis) {
        if(growthStatisMapper.insertOrUpdate(userGrowthStatis) <= 0){
            throw new ServiceException("操作失败");
        }
    }
}