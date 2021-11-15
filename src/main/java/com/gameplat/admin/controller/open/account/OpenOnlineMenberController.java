package com.gameplat.admin.controller.open.account;

import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.gameplat.admin.model.dto.OnlineUserDTO;
import com.gameplat.admin.model.vo.OnlineUserVo;
import com.gameplat.admin.model.vo.UserToken;
import com.gameplat.admin.service.OnlineMenberService;
import com.gameplat.common.constant.ServiceName;
import com.gameplat.common.exception.ServiceException;
import com.gameplat.common.util.StringUtils;
import com.gameplat.log.annotation.Log;
import com.gameplat.log.enums.LogType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 在线会员管理
 *
 * @author three
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/account/online")
public class OpenOnlineMenberController {

  private final OnlineMenberService menberService;

  @GetMapping("/list")
  public OnlineUserVo onlineList(PageDTO<UserToken> page, OnlineUserDTO userDTO) {
    return menberService.selectOnlineList(page, userDTO);
  }

  @PostMapping("/kick")
  @PreAuthorize("hasAuthority('account:online:kick')")
  @Log(
      module = ServiceName.ADMIN_SERVICE,
      type = LogType.ADMIN,
      desc = "'将【'+#userDTO.userName+'】踢下线' ")
  public void kickUser(OnlineUserDTO userDTO) {
    if (StringUtils.isBlank(userDTO.getUserName())) {
      throw new ServiceException("缺少账号名");
    }
    if (StringUtils.isNull(userDTO.getClientType())) {
      throw new ServiceException("缺少账号标示");
    }
    menberService.kickUser(userDTO);
  }

  @PutMapping("/kickAll")
  @PreAuthorize("hasAuthority('account:online:kickAll')")
  @Log(module = ServiceName.ADMIN_SERVICE, type = LogType.ADMIN, desc = "踢出所有在线账号")
  public void kickAllUser(@RequestBody String sign) {
    menberService.kickAllUser(sign);
  }
}
