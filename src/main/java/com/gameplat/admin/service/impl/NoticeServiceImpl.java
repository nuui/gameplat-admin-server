package com.gameplat.admin.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gameplat.admin.convert.NoticeConvert;
import com.gameplat.admin.mapper.NoticeMapper;
import com.gameplat.admin.model.domain.Notice;
import com.gameplat.admin.model.domain.SysDictData;
import com.gameplat.admin.model.dto.NoticeAddDTO;
import com.gameplat.admin.model.dto.NoticeEditDTO;
import com.gameplat.admin.model.dto.NoticeQueryDTO;
import com.gameplat.admin.model.dto.NoticeUpdateStatusDTO;
import com.gameplat.admin.model.vo.NoticeDictDataVO;
import com.gameplat.admin.model.vo.NoticeVO;
import com.gameplat.admin.model.vo.ValueDataVO;
import com.gameplat.admin.service.NoticeService;
import com.gameplat.admin.service.SysDictDataService;
import com.gameplat.base.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author lily
 * @description 公告信息业务处理层
 * @date 2021/11/16
 */

@Service
@RequiredArgsConstructor
public class NoticeServiceImpl extends ServiceImpl<NoticeMapper, Notice> implements NoticeService {

    @Autowired private NoticeConvert noticeConvert;
    @Autowired private SysDictDataService sysDictDataService;

    @Override
    public IPage<NoticeVO> selectNoticeList(IPage<Notice> page, NoticeQueryDTO noticeQueryDTO) {
        LambdaQueryChainWrapper<Notice> queryWrapper =
                this.lambdaQuery()
                    .eq(ObjectUtils.isNotEmpty(noticeQueryDTO.getNoticeType()), Notice::getNoticeType, noticeQueryDTO.getNoticeType())
                    .eq(ObjectUtils.isNotEmpty(noticeQueryDTO.getStatus()), Notice::getStatus, noticeQueryDTO.getStatus())
                 .like(ObjectUtils.isNotEmpty(noticeQueryDTO.getNoticeTitle()), Notice::getNoticeTitle, noticeQueryDTO.getNoticeTitle());

        return queryWrapper.page(page).convert(noticeConvert::toVo);
    }

    @Override
    public void updateNotice(NoticeEditDTO noticeEditDTO) {
        if (ObjectUtils.isEmpty(noticeEditDTO.getId())){
            throw new ServiceException("id不能为空!");
        }
        Notice notice = noticeConvert.toEntity(noticeEditDTO);
        if (!this.updateById(notice)){
            throw new ServiceException("更新公告信息失败!");
        }
    }

    @Override
    public void deleteNotice(Integer id) {
        if (ObjectUtils.isEmpty(id)) {
            throw new ServiceException("id不能为空!");
        }
        if (!this.removeById(id)){
            throw new ServiceException("删除公告信息失败！");
        }
    }

    @Override
    public void disableStatus(NoticeUpdateStatusDTO noticeUpdateStatusDTO) {
        if (ObjectUtils.isEmpty(noticeUpdateStatusDTO.getId())) {
            throw new ServiceException("id不能为空!");
        }
        Notice notice = noticeConvert.toEntity(noticeUpdateStatusDTO);
        if (!this.updateById(notice)){
            throw new ServiceException("禁用失败！");
        }

    }

    @Override
    public void enableStatus(NoticeUpdateStatusDTO noticeUpdateStatusDTO) {
        if (ObjectUtils.isEmpty(noticeUpdateStatusDTO.getId())) {
            throw new ServiceException("id不能为空!");
        }
        Notice notice = noticeConvert.toEntity(noticeUpdateStatusDTO);
        if (!this.updateById(notice)){
            throw new ServiceException("启用失败！");
        }
    }

    @Override
    public NoticeDictDataVO getDictData() {
        SysDictData notice_category = sysDictDataService.getDictDataByType("NOTICE_CATEGORY");
        List<String> types = new ArrayList<>();
        types.add("NOTICE_CATEGORY");
        types.add("NOTICE_TOTAL_CATEGORY");
        types.add("NOTICE_TYPE");
        types.add("PUSH_MESSAGE_TYPE");
        List<SysDictData> dictDataByTypes = sysDictDataService.getDictDataByTypes(types);

        NoticeDictDataVO vo = new NoticeDictDataVO();
        for (SysDictData dictDataByType : dictDataByTypes) {
            if(dictDataByType.getDictType().equals("NOTICE_TYPE")){
                vo.setNoticeType(JSONObject.parseArray(dictDataByType.getDictValue()));
            }
            if(dictDataByType.getDictType().equals("NOTICE_CATEGORY")){
                vo.setNoticeCategory(JSONObject.parseArray(dictDataByType.getDictValue()));
            }
            if(dictDataByType.getDictType().equals("NOTICE_TOTAL_CATEGORY")){
                vo.setNoticeTotalCategory(JSONObject.parseArray(dictDataByType.getDictValue()));
            }
            if(dictDataByType.getDictType().equals("PUSH_MESSAGE_TYPE")){
                vo.setPushMessageType(JSONObject.parseArray(dictDataByType.getDictValue()));
            }
        }
        return vo;
    }

    @Override
    public void insertNotice(NoticeAddDTO noticeAddDTO) {
        Notice notice = noticeConvert.toEntity(noticeAddDTO);
        if (!this.save(notice)) {
            throw new ServiceException("添加公告信息失败！");
        }

    }

}
