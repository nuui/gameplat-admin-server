package com.gameplat.admin.controller.open.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.gameplat.admin.model.domain.SysBannerInfo;
import com.gameplat.admin.model.dto.SysBannerInfoAddDTO;
import com.gameplat.admin.model.dto.SysBannerInfoEditDTO;
import com.gameplat.admin.model.vo.SysBannerInfoVO;
import com.gameplat.admin.service.SysBannerInfoService;
import com.gameplat.base.common.exception.ServiceException;
import com.gameplat.base.common.util.StringUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import springfox.documentation.annotations.ApiIgnore;


/**
 * banner管理
 *
 * @author kenvin
 */
@Api(tags = "banner管理")
@Slf4j
@RestController
@RequestMapping("/api/admin/system/banner")
public class OpenBannerController {

    @Autowired
    private SysBannerInfoService sysBannerInfoService;

    /**
     * banner列表
     *
     * @param page
     * @param language
     * @return
     */
    @ApiOperation(value = "banner列表")
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('system:banner:list')")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "current", value = "分页参数：当前页", defaultValue = "1"),
            @ApiImplicitParam(name = "size", value = "每页条数"),
    })
    public IPage<SysBannerInfoVO> list(@ApiIgnore PageDTO<SysBannerInfo> page, String language) {
        return sysBannerInfoService.list(page, language);
    }

    /**
     * 新增banner
     *
     * @param sysBannerInfoAddDTO
     */
    @ApiOperation(value = "新增banner")
    @PostMapping("/add")
    @PreAuthorize("hasAuthority('system:banner:add')")
    public void add(@RequestBody SysBannerInfoAddDTO sysBannerInfoAddDTO) {
        sysBannerInfoService.add(sysBannerInfoAddDTO);
    }

    /**
     * 编辑banner
     *
     * @param sysBannerInfoEditDTO
     */
    @ApiOperation(value = "编辑banner")
    @PostMapping("/edit")
    @PreAuthorize("hasAuthority('system:banner:add')")
    public void edit(@RequestBody SysBannerInfoEditDTO sysBannerInfoEditDTO) {
        sysBannerInfoService.edit(sysBannerInfoEditDTO);
    }

    /**
     * 删除banner
     *
     * @param ids
     */
    @ApiOperation(value = "删除banner")
    @DeleteMapping("/delete")
    @PreAuthorize("hasAuthority('system:banner:remove')")
    public void delete(@RequestBody String ids) {
        if (StringUtils.isBlank(ids)) {
            throw new ServiceException("ids不能为空");
        }
        sysBannerInfoService.delete(ids);
    }


}