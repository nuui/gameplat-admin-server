package com.gameplat.admin.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alicp.jetcache.Cache;
import com.alicp.jetcache.anno.CachePenetrationProtect;
import com.alicp.jetcache.anno.CacheRefresh;
import com.alicp.jetcache.anno.Cached;
import com.alicp.jetcache.anno.CreateCache;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gameplat.admin.convert.AuthIpConvert;
import com.gameplat.admin.mapper.SysAuthIpMapper;
import com.gameplat.admin.model.domain.SysAuthIp;
import com.gameplat.admin.model.dto.AuthIpDTO;
import com.gameplat.admin.model.dto.OperAuthIpDTO;
import com.gameplat.admin.model.vo.AuthIpVo;
import com.gameplat.admin.service.SysAuthIpService;
import com.gameplat.base.common.exception.ServiceException;
import com.gameplat.base.common.util.StringUtils;
import com.gameplat.common.constant.CachedKeys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ip白名单 服务实现层
 *
 * @author three
 */
@Service
public class SysAuthIpServiceImpl extends ServiceImpl<SysAuthIpMapper, SysAuthIp>
    implements SysAuthIpService {

  @Autowired private AuthIpConvert authIpConvert;

  @Override
  @SentinelResource(value = "selectAuthIpList")
  public IPage<AuthIpVo> selectAuthIpList(IPage<SysAuthIp> page, AuthIpDTO authIpDTO) {
    return this.lambdaQuery()
        .eq(ObjectUtils.isNotEmpty(authIpDTO.getIp()), SysAuthIp::getAllowIp, authIpDTO.getIp())
        .apply(
            ObjectUtils.isNotEmpty(authIpDTO.getIpType()),
            "FIND_IN_SET ('" + authIpDTO.getIpType() + "',ip_type)")
        .like(
            ObjectUtils.isNotEmpty(authIpDTO.getRemark()),
            SysAuthIp::getRemark,
            authIpDTO.getRemark())
        .page(page)
        .convert(authIpConvert::toVo);
  }

  @Override
  @SentinelResource(value = "insertAuthip")
  public void insertAuthip(OperAuthIpDTO operAuthIpDTO) {
    SysAuthIp authIp = authIpConvert.toEntity(operAuthIpDTO);
    if (isExist(operAuthIpDTO.getIp())) {
      throw new ServiceException("IP已存在");
    }
    if (!this.save(authIp)) {
      throw new ServiceException("保存失败!");
    }
  }

  @Override
  @SentinelResource(value = "updateAuthIp")
  public void updateAuthIp(OperAuthIpDTO operAuthIpDTO) {
    SysAuthIp authIp = authIpConvert.toEntity(operAuthIpDTO);
    if (!this.updateById(authIp)) {
      throw new ServiceException("更新失败!");
    }
  }

  @Override
  @SentinelResource(value = "deleteAuthIp")
  public void deleteAuthIp(Long id) {
    if (!this.removeById(id)) {
      throw new ServiceException("删除失败!");
    }
  }

  @Override
  @SentinelResource(value = "deleteBatch")
  public void deleteBatch(String ids) {
    if (!this.removeByIds(Arrays.asList(StringUtils.split(ids, ",")))) {
      throw new ServiceException("批量删除失败!");
    }
  }

  @Override
  @SentinelResource(value = "checkAuthIpUnique")
  public boolean checkAuthIpUnique(String ip) {
    return !isExist(ip);
  }

  @Override
  @CachePenetrationProtect
  @Cached(name = CachedKeys.ADMIN_AUTH_IP, expire = 3600)
  @CacheRefresh(refresh = 600, stopRefreshAfterLastAccess = 7200)
  @SentinelResource(value = "getAllList")
  public Set<String> getAllList() {
    return this.lambdaQuery().list().stream()
        .map(SysAuthIp::getAllowIp)
        .collect(Collectors.toSet());
  }

  @Override
  public boolean isExist(String ip) {
    Set<String> permittedIpSet =
        this.lambdaQuery().list().stream().map(SysAuthIp::getAllowIp).collect(Collectors.toSet());
    return !permittedIpSet.isEmpty() && permittedIpSet.contains(ip);
  }
}
