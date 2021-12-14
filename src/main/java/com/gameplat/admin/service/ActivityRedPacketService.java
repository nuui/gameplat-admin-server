package com.gameplat.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gameplat.admin.model.domain.ActivityRedPacket;
import com.gameplat.admin.model.dto.ActivityRedPacketAddDTO;
import com.gameplat.admin.model.dto.ActivityRedPacketQueryDTO;
import com.gameplat.admin.model.dto.ActivityRedPacketUpdateDTO;
import com.gameplat.admin.model.vo.ActivityRedPacketVO;

/**
 * 红包雨业务
 */
public interface ActivityRedPacketService extends IService<ActivityRedPacket> {

    /**
     * 查询红包雨列表
     *
     * @param page
     * @param activityRedPacketQueryDTO
     * @return
     */
    IPage<ActivityRedPacketVO> redPacketList(PageDTO<ActivityRedPacket> page, ActivityRedPacketQueryDTO activityRedPacketQueryDTO);

    /**
     * 新增红包雨配置
     *
     * @param activityRedPacketAddDTO
     */
    void add(ActivityRedPacketAddDTO activityRedPacketAddDTO);

    /**
     * 编辑红包雨配置
     *
     * @param activityRedPacketUpdateDTO
     */
    void edit(ActivityRedPacketUpdateDTO activityRedPacketUpdateDTO);

    /**
     * 更新红包雨状态
     *
     * @param packetId
     */
    void updateStatus(Long packetId);

    /**
     * 批量删除
     *
     * @param ids
     */
    void delete(String ids);
}
