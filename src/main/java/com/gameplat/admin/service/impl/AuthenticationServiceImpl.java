package com.gameplat.admin.service.impl;

import com.gameplat.admin.feign.MessageFeignClient;
import com.gameplat.admin.model.dto.AdminLoginDTO;
import com.gameplat.admin.model.vo.UserToken;
import com.gameplat.admin.service.*;
import com.gameplat.base.common.exception.ServiceException;
import com.gameplat.common.model.bean.RefreshToken;
import com.gameplat.model.entity.sys.SysUser;
import com.gameplat.security.context.UserCredential;
import com.gameplat.security.manager.JwtTokenAuthenticationManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@Transactional(isolation = Isolation.DEFAULT, rollbackFor = Throwable.class)
public class AuthenticationServiceImpl implements AuthenticationService {

  @Autowired private SysUserService userService;

  @Autowired private JwtTokenAuthenticationManager tokenAuthenticationManager;

  @Autowired private PermissionService permissionService;

  @Autowired private PasswordService passwordService;

  @Autowired private TwoFactorAuthenticationService twoFactorAuthenticationService;

  @Override
  public UserToken login(AdminLoginDTO dto, HttpServletRequest request) {
    String username = dto.getAccount();
    String password = passwordService.decrypt(dto.getPassword());
    boolean authenticated = twoFactorAuthenticationService.isEnabled();

    UserCredential credential =
        tokenAuthenticationManager.authenticate(username, password, request, !authenticated);

    this.loginAfter(credential);

    return this.createLoginSuccessToken(credential);
  }

  @Override
  public RefreshToken refreshToken(String refreshToken) {
    UserCredential credential = tokenAuthenticationManager.refreshToken(refreshToken);
    return RefreshToken.builder()
        .refreshToken(credential.getRefreshToken())
        .accessToken(credential.getAccessToken())
        .tokenExpireIn(credential.getTokenExpireIn())
        .build();
  }

  @Override
  public void logout() {
    tokenAuthenticationManager.logout();
  }

  @Override
  public RefreshToken verify2Fa(UserCredential credential, String code) {
    // 谷歌认证
    twoFactorAuthenticationService.verify2Fa(credential.getUserId(), code);

    // 颁发正式token
    return createAuthenticatedToken(credential);
  }

  /**
   * 颁发正式token
   *
   * @param credential UserCredential
   * @return RefreshToken
   */
  private RefreshToken createAuthenticatedToken(UserCredential credential) {
    tokenAuthenticationManager.createToken(credential);
    return RefreshToken.builder()
        .refreshToken(credential.getRefreshToken())
        .accessToken(credential.getAccessToken())
        .tokenExpireIn(credential.getTokenExpireIn())
        .build();
  }

  private void loginAfter(UserCredential credential) {
    this.updateLoginUserInfo(credential);
  }

  private UserToken createLoginSuccessToken(UserCredential credential) {
    return UserToken.builder()
        .account(credential.getUsername())
        .nickName(credential.getNickname())
        .accessToken(credential.getAccessToken())
        .refreshToken(credential.getRefreshToken())
        .tokenExpireIn(credential.getTokenExpireIn())
        .clientType(credential.getClientType())
        .deviceType(credential.getDeviceType())
        .accessLogToken(permissionService.getAccessLogToken())
        .isEnable2FA(credential.isEnable2FA())
        .build();
  }

  private void updateLoginUserInfo(UserCredential credential) {
    if (!userService.updateById(
        SysUser.builder()
            .userId(credential.getUserId())
            .loginIp(credential.getLoginIp())
            .loginDate(credential.getLoginDate())
            .build())) {
      throw new ServiceException("登录失败，请联系客服！");
    }
  }
}
