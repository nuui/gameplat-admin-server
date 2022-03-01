package com.gameplat.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.IService;
import com.gameplat.admin.model.domain.proxy.DivideDetail;
import com.gameplat.admin.model.dto.DivideDetailQueryDTO;
import com.gameplat.admin.model.vo.DivideDetailVO;

public interface DivideDetailService extends IService<DivideDetail> {
    IPage<DivideDetailVO> queryPage(PageDTO<DivideDetail> page, DivideDetailQueryDTO dto);
}
