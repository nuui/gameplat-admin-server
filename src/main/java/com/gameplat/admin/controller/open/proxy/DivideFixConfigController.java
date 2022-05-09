package com.gameplat.admin.controller.open.proxy;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.gameplat.admin.model.dto.DivideConfigDTO;
import com.gameplat.admin.service.DivideFixConfigService;
import com.gameplat.base.common.exception.ServiceException;
import com.gameplat.base.common.util.StringUtils;
import com.gameplat.common.constant.ServiceName;
import com.gameplat.log.annotation.Log;
import com.gameplat.log.enums.LogType;
import com.gameplat.model.entity.proxy.DivideFixConfig;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** @Description : 固定比例分红模式 @Author : cc @Date : 2022/4/2 */
@Api(tags = "固定比例分红模式")
@RestController
@RequestMapping("/api/admin/divide/fix")
public class DivideFixConfigController {

  @Autowired private DivideFixConfigService fixConfigService;

  /**
   * 固定分红比例分页列表
   *
   * @param page
   * @param queryObj
   * @return
   */
  @GetMapping("/pageList")
  @PreAuthorize("hasAuthority('agent:bonusFixconfig:view')")
  public IPage<DivideFixConfig> list(PageDTO<DivideFixConfig> page, DivideConfigDTO queryObj) {
    LambdaQueryWrapper<DivideFixConfig> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper
        .eq(
            StrUtil.isNotBlank(queryObj.getUserName()),
            DivideFixConfig::getUserName,
            queryObj.getUserName())
        .orderByDesc(DivideFixConfig::getCreateTime);
    return fixConfigService.page(page, queryWrapper);
  }

  /**
   * 添加
   *
   * @param divideConfigDTO
   */
  @PostMapping("/add")
  @ApiOperation(value = "新增固定比例分红配置")
  @PreAuthorize("hasAuthority('agent:bonusFixconfig:add')")
  @Log(module = ServiceName.ADMIN_SERVICE, type = LogType.AGENT, desc = "新增固定比例分红配置")
  public void add(@Validated @RequestBody DivideConfigDTO divideConfigDTO) {
    fixConfigService.add(divideConfigDTO.getUserName(), "zh-CN");
  }

  /**
   * 编辑前获取固定比例配置
   *
   * @param dto
   * @return
   */
  @GetMapping("/getFixConfigForEdit")
  public Map<String, Object> getFixConfigForEdit(DivideConfigDTO dto) {
    return fixConfigService.getFixConfigForEdit(dto.getUserName(), "zh-CN");
  }

  /**
   * 编辑固定分红配置
   *
   * @param dto
   */
  @PostMapping("/edit")
  @ApiOperation(value = "编辑固定比例分红配置")
  @PreAuthorize("hasAuthority('agent:bonusFixconfig:edit')")
  @Log(module = ServiceName.ADMIN_SERVICE, type = LogType.AGENT, desc = "新增固定比例分红配置")
  public void edit(@Validated @RequestBody DivideConfigDTO dto) {
    fixConfigService.edit(dto, "zh-CN");
  }

  /**
   * 删除
   *
   * @param ids
   */
  @ApiOperation(value = "删除固定分红配置")
  @PostMapping("/delete")
  @PreAuthorize("hasAuthority('agent:bonusFixconfig:remove')")
  public void remove(@RequestBody String ids) {
    if (StringUtils.isBlank(ids)) {
      throw new ServiceException("ids不能为空");
    }
    fixConfigService.remove(ids);
  }
}
