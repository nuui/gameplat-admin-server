package com.gameplat.admin.service.impl;

import com.gameplat.admin.cache.AdminCache;
import com.gameplat.admin.constant.RsaConstant;
import com.gameplat.admin.enums.ErrorPasswordLimit;
import com.gameplat.admin.model.domain.SysUser;
import com.gameplat.admin.model.dto.AdminLoginDTO;
import com.gameplat.admin.model.vo.UserToken;
import com.gameplat.admin.service.*;
import com.gameplat.base.common.exception.ServiceException;
import com.gameplat.base.common.util.GoogleAuthenticator;
import com.gameplat.base.common.util.IPUtils;
import com.gameplat.base.common.util.StringUtils;
import com.gameplat.common.compent.kaptcha.KaptchaProducer;
import com.gameplat.common.enums.BooleanEnum;
import com.gameplat.common.enums.LimitEnums;
import com.gameplat.common.model.bean.RefreshToken;
import com.gameplat.common.model.bean.limit.AdminLoginLimit;
import com.gameplat.security.context.UserCredential;
import com.gameplat.security.service.JwtTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

@Slf4j
@Service
@Transactional(isolation = Isolation.DEFAULT, rollbackFor = Throwable.class)
public class AuthenticationServiceImpl implements AuthenticationService {

  @Autowired private LimitInfoService limitInfoService;

  @Autowired private SysUserService userService;

  @Autowired private SysAuthIpService authIpService;

  @Autowired private KaptchaProducer kaptchaProducer;

  @Autowired private AdminCache adminCache;

  @Autowired private JwtTokenService jwtTokenService;

  @Autowired private PermissionService permissionService;

  @Autowired private PasswordService passwordService;

  @Override
  public UserToken login(AdminLoginDTO dto, HttpServletRequest request) {
    // 获取管理员登录限制信息
    AdminLoginLimit limit =
        limitInfoService.getLimitInfo(LimitEnums.ADMIN_LOGIN_CONFIG, AdminLoginLimit.class);
    if (null == limit) {
      throw new ServiceException("登录配置信息未配置");
    }

    // 是否开启后台白名单
    String requestIp = IPUtils.getIpAddress(request);
    if (BooleanEnum.YES.match(limit.getIpWhiteListSwitch())) {
      if (!authIpService.isPermitted(requestIp)) {
        throw new ServiceException("当前IP不允许登录：" + requestIp);
      }
    }

    // 是否开启验证码
    if (BooleanEnum.YES.match(limit.getLoginCaptchaSwitch())) {
      kaptchaProducer.validate(dto.getDeviceId(), dto.getValiCode());
    }

    // 判断密码错误次数
    int errorPasswordCount = adminCache.getErrorPasswordCount(dto.getAccount());
    this.checkPasswordErrorCount(limit, errorPasswordCount);

    // 校验安全码
    SysUser user = userService.getByUsername(dto.getAccount());
    if (null == user) {
      throw new UsernameNotFoundException("用户不存在或密码不正确！");
    }

    if (BooleanEnum.YES.match(limit.getGoogleAuthSwitch())) {
      this.checkGoogleAuthCode(user.getSafeCode(), dto.getGoogleCode());
    }

    // 解密密码并登陆
    String password = passwordService.decode(dto.getPassword(), RsaConstant.PRIVATE_KEY);
    UserCredential credential = jwtTokenService.signIn(request, user.getUserName(), password);

    // 删除密码错误次数记录
    adminCache.cleanErrorPasswordCount(user.getUserName());

    // 更新用户登录信息
    user.setLoginIp(requestIp);
    user.setLoginDate(new Date());
    userService.updateById(user);

    return UserToken.builder()
        .nickName(user.getNickName())
        .accessToken(credential.getAccessToken())
        .refreshToken(credential.getRefreshToken())
        .tokenExpireIn(credential.getTokenExpireIn())
        .clientType(credential.getClientType())
        .deviceType(credential.getDeviceType())
        .account(user.getUserName())
        .accessLogToken(permissionService.getAccessLogToken())
        .build();
  }

  @Override
  public RefreshToken refreshToken(String refreshToken) {
    UserCredential credential = jwtTokenService.refreshToken(refreshToken);

    return RefreshToken.builder()
        .refreshToken(credential.getRefreshToken())
        .accessToken(credential.getAccessToken())
        .tokenExpireIn(credential.getTokenExpireIn())
        .build();
  }

  @Override
  public void logout() {
    jwtTokenService.logout();
  }

  private void checkPasswordErrorCount(AdminLoginLimit limit, int errorPasswordCount) {
    boolean checkErrorCount =
        ErrorPasswordLimit.ADMIN_RELIEVE.match(limit.getPwdErrorCount())
            || (ErrorPasswordLimit.DEFAULT.getKey().equals(limit.getPwdErrorCount())
                && errorPasswordCount >= limit.getPwdErrorCount());
    if (checkErrorCount) {
      throw new ServiceException("密码错误次数超限!");
    }
  }

  /**
   * 校验谷歌验证码是否正确
   *
   * @param safeCode String
   * @param googleCode String
   */
  private void checkGoogleAuthCode(String safeCode, String googleCode) {
    if (StringUtils.isBlank(safeCode)) {
      throw new ServiceException("安全码未设置");
    }

    if (StringUtils.isBlank(googleCode)) {
      throw new ServiceException("安全码不能为空!");
    }

    if (!GoogleAuthenticator.authcode(googleCode, safeCode)) {
      throw new ServiceException("安全码不正确!");
    }
  }
}
