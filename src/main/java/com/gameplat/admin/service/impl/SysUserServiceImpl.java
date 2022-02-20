package com.gameplat.admin.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alicp.jetcache.anno.Cached;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gameplat.admin.cache.AdminCache;
import com.gameplat.admin.convert.UserConvert;
import com.gameplat.admin.mapper.SysRoleMapper;
import com.gameplat.admin.mapper.SysUserMapper;
import com.gameplat.admin.mapper.SysUserRoleMapper;
import com.gameplat.admin.model.bean.UserSetting;
import com.gameplat.admin.model.domain.SysRole;
import com.gameplat.admin.model.domain.SysUser;
import com.gameplat.admin.model.domain.SysUserRole;
import com.gameplat.admin.model.dto.OperUserDTO;
import com.gameplat.admin.model.dto.UserDTO;
import com.gameplat.admin.model.dto.UserResetPasswordDTO;
import com.gameplat.admin.model.vo.RoleVo;
import com.gameplat.admin.model.vo.UserVo;
import com.gameplat.admin.service.CommonService;
import com.gameplat.admin.service.PasswordService;
import com.gameplat.admin.service.SysUserService;
import com.gameplat.base.common.enums.EnableEnum;
import com.gameplat.base.common.exception.ServiceException;
import com.gameplat.base.common.json.JsonUtils;
import com.gameplat.base.common.util.StringUtils;
import com.gameplat.common.constant.CachedKeys;
import com.gameplat.common.enums.TrueFalse;
import com.gameplat.common.enums.UserTypes;
import com.gameplat.common.lang.Assert;
import com.gameplat.common.model.bean.limit.AdminLoginLimit;
import com.gameplat.security.SecurityUserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(isolation = Isolation.DEFAULT, rollbackFor = Throwable.class)
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser>
    implements SysUserService {

  @Autowired private CommonService commonService;

  @Autowired private SysUserMapper userMapper;

  @Autowired private SysRoleMapper roleMapper;

  @Autowired private SysUserRoleMapper userRoleMapper;

  @Autowired private AdminCache adminCache;

  @Autowired private UserConvert userConvert;

  @Autowired private PasswordService passwordService;

  @Override
  public SysUser getByUsername(String username) {
    return this.lambdaQuery().eq(SysUser::getUserName, username).one();
  }

  @Override
  @SentinelResource(value = "selectUserList")
  public IPage<UserVo> selectUserList(PageDTO<SysUser> page, UserDTO userDTO) {
    LambdaQueryWrapper<SysUser> queryWrapper = Wrappers.lambdaQuery();
    queryWrapper
        .like(
            ObjectUtils.isNotEmpty(userDTO.getAccount()),
            SysUser::getUserName,
            userDTO.getAccount())
        .like(
            ObjectUtils.isNotEmpty(userDTO.getNickName()),
            SysUser::getNickName,
            userDTO.getNickName())
        .like(
            ObjectUtils.isNotEmpty(userDTO.getLoginIp()), SysUser::getLoginIp, userDTO.getLoginIp())
        .eq(
            ObjectUtils.isNotNull(userDTO.getUserType()),
            SysUser::getUserType,
            userDTO.getUserType())
        .eq(ObjectUtils.isNotNull(userDTO.getStatus()), SysUser::getStatus, userDTO.getStatus())
        .eq(ObjectUtils.isNotEmpty(userDTO.getPhone()), SysUser::getPhone, userDTO.getPhone())
        .between(
            ObjectUtils.isNotEmpty(userDTO.getBeginTime()),
            SysUser::getCreateTime,
            userDTO.getBeginTime(),
            userDTO.getEndTime());

    return userMapper.selectUserList(page, queryWrapper).convert(userConvert::toUserVo);
  }

  @Override
  @SentinelResource(value = "insertUser")
  public void insertUser(OperUserDTO dto) {
    Assert.isFalse(this.lambdaQuery().eq(SysUser::getUserName, dto.getAccount()).exists(), "账号已存在");

    AdminLoginLimit limit = commonService.getLoginLimit();
    Assert.notNull(limit, "登录配置信息未配置");

    SysUser user = userConvert.toEntity(dto);
    if (UserTypes.ADMIN.match(dto.getUserType())) {
      // 超级管理员但不是特殊的管理员统一设置roleId=1
      if (StringUtils.isNull(dto.getRoleId())) {
        user.setRoleId(1L);
      }
    }

    // 初始化账号设置信息
    UserSetting setting = new UserSetting();
    user.setSettings(JsonUtils.toJson(setting));

    // 生成密码
    String rsaPassword = passwordService.decrypt(dto.getPassword());
    user.setPassword(passwordService.encode(rsaPassword));
    user.setChangeFlag(limit.getResetPwdSwitch() == 1 ? 0 : 1);

    if (this.save(user)) {
      // 删除用户角色表
      userRoleMapper.deleteUserRoleByUserId(user.getUserId());
      insertUserRole(user.getUserId(), user.getRoleId());
    } else {
      throw new ServiceException("添加用户失败!");
    }
  }

  @Override
  @SentinelResource(value = "updateUser")
  public void updateUser(OperUserDTO dto) {
    SysUser user = userConvert.toEntity(dto);
    if (!this.updateById(user)) {
      throw new ServiceException("修改用户信息失败!");
    }

    // 删除用户角色表
    userRoleMapper.deleteUserRoleByUserId(user.getUserId());
    insertUserRole(user.getUserId(), user.getRoleId());
  }

  @Override
  @SentinelResource(value = "deleteUserById")
  public void deleteUserById(Long id) {
    Assert.isTrue(1 == id, "不允许删除超级管理员用户!");
    Assert.isFalse(id.equals(SecurityUserHolder.getUserId()), "不允许操作自己账号");

    // 删除用户角色表
    userRoleMapper.deleteUserRole(new Long[] {id});
    Assert.isTrue(this.removeById(id), "删除用户失败!");
  }

  @Override
  @SentinelResource(value = "getRoleList")
  public List<RoleVo> getRoleList() {
    List<RoleVo> list = new ArrayList<>();
    LambdaQueryWrapper<SysRole> queryWrapper = Wrappers.lambdaQuery();
    queryWrapper.eq(SysRole::getStatus, EnableEnum.ENABLED.code());
    roleMapper
        .selectList(queryWrapper)
        .forEach(
            item -> {
              RoleVo roleVo = new RoleVo();
              roleVo.setId(item.getRoleId());
              roleVo.setRoleName(item.getRoleName());
              roleVo.setRemark(item.getRemark());
              list.add(roleVo);
            });
    return list;
  }

  @Override
  @SentinelResource(value = "resetUserPassword")
  public void resetUserPassword(UserResetPasswordDTO dto) {
    SysUser user = userConvert.toEntity(dto);
    AdminLoginLimit loginLimit = commonService.getLoginLimit();

    String newPassword = passwordService.decrypt(dto.getPassword());
    user.setPassword(passwordService.encode(newPassword));
    user.setChangeFlag(
        loginLimit.getResetPwdSwitch() == TrueFalse.TRUE.getValue()
            ? TrueFalse.TRUE.getValue()
            : TrueFalse.FALSE.getValue());

    if (this.updateById(user)) {
      // 重置用户密码错误次数
      adminCache.cleanErrorPasswordCount(user.getUserName());
    } else {
      throw new ServiceException("更新用户信息失败!");
    }
  }

  @Override
  @SentinelResource(value = "resetGoogleSecret")
  public void resetGoogleSecret(Long id) {
    SysUser user = Assert.notNull(this.getById(id), "用户不存在!");
    user.setSafeCode(null);
    Assert.isTrue(this.updateById(user), "重置安全码失败!");
  }

  @Override
  public String getSecret(Long id) {
    return this.getById(id).getSafeCode();
  }

  @Override
  public boolean isSecretExist(String secret) {
    return this.lambdaQuery().eq(SysUser::getSafeCode, secret).exists();
  }

  @Override
  @SentinelResource(value = "changeStatus")
  public void changeStatus(Long id, Integer status) {
    Assert.isTrue(!id.equals(SecurityUserHolder.getUserId()), "不允许操作自己账号!");
    SysUser user = Assert.notNull(this.getById(id), "用户不存在!");
    user.setStatus(status);
    Assert.isTrue(this.updateById(user), "修改状态失败!");
  }

  @Override
  public void bindSecret(Long id, String secret) {
    Assert.isTrue(
        this.lambdaUpdate().set(SysUser::getSafeCode, secret).eq(SysUser::getUserId, id).update(),
        "绑定失败!");
  }

  /**
   * 新增用户角色信息
   *
   * @param userId Long
   * @param roleId Long
   */
  private void insertUserRole(Long userId, Long roleId) {
    if (StringUtils.isNull(roleId)) {
      return;
    }

    // 新增用户与角色管理
    List<SysUserRole> list = new ArrayList<>();
    SysUserRole ur = new SysUserRole();
    ur.setUserId(userId);
    ur.setRoleId(roleId);
    list.add(ur);
    userRoleMapper.batchUserRole(list);
  }
}
