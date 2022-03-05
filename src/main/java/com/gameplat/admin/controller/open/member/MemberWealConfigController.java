package com.gameplat.admin.controller.open.member;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.gameplat.admin.model.dto.MemberWealConfigAddDTO;
import com.gameplat.admin.model.dto.MemberWealConfigEditDTO;
import com.gameplat.admin.service.MemberWealConfigService;
import com.gameplat.model.entity.member.MemberWealConfig;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author lily
 * @description VIP轮播图配置
 * @date 2022/1/14
 */
@Api(tags = "VIP权益配置")
@Slf4j
@RestController
@RequestMapping("/api/admin/member/wealConfig")
public class MemberWealConfigController {

  @Autowired private MemberWealConfigService memberWealConfigService;

  /** 增 */
  @PostMapping("/add")
  @ApiOperation(value = "新增权益配置")
  @PreAuthorize("hasAuthority('member:wealConfig:add')")
  public void addWealConfig(@Validated MemberWealConfigAddDTO dto) {
    memberWealConfigService.addWealConfig(dto);
  }

  /** 删 */
  @ApiOperation(value = "删除权益配置")
  @DeleteMapping("/remove/{id}")
  @PreAuthorize("hasAuthority('member:wealConfig:remove')")
  public void removeWealConfig(@PathVariable Long id) {
    memberWealConfigService.removeWealConfig(id);
  }

  /** 改 */
  @PutMapping("/edit")
  @ApiOperation(value = "修改权益配置")
  @PreAuthorize("hasAuthority('member:wealConfig:edit')")
  public void updateBanner(@Validated MemberWealConfigEditDTO dto) {
    memberWealConfigService.updateWealConfig(dto);
  }

  /** 改 */
  @GetMapping("/page")
  @ApiOperation(value = "查询权益列表")
  @PreAuthorize("hasAuthority('member:wealConfig:page')")
  public IPage<MemberWealConfig> page(PageDTO<MemberWealConfig> page, String language) {
    return memberWealConfigService.page(page, language);
  }
}
