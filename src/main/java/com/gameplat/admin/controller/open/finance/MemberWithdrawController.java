package com.gameplat.admin.controller.open.finance;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gameplat.admin.model.bean.PageExt;
import com.gameplat.admin.model.dto.MemberWithdrawDTO;
import com.gameplat.admin.model.dto.MemberWithdrawQueryDTO;
import com.gameplat.admin.model.vo.MemberWithdrawVO;
import com.gameplat.admin.model.vo.SummaryVO;
import com.gameplat.admin.service.MemberWithdrawService;
import com.gameplat.base.common.exception.ServiceException;
import com.gameplat.common.constant.ServiceName;
import com.gameplat.common.enums.WithdrawStatus;
import com.gameplat.common.model.bean.UserEquipment;
import com.gameplat.log.annotation.Log;
import com.gameplat.log.enums.LogType;
import com.gameplat.model.entity.member.MemberWithdraw;
import com.gameplat.model.entity.pay.PpMerchant;
import com.gameplat.redis.redisson.DistributedLocker;
import com.gameplat.security.SecurityUserHolder;
import com.gameplat.security.context.UserCredential;
import java.math.BigDecimal;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin/finance/memberWithdraw")
public class MemberWithdrawController {

  @Autowired
  private MemberWithdrawService userWithdrawService;
  @Autowired
  private DistributedLocker distributedLocker;

  @PostMapping("/modifyCashStatus")
  @PreAuthorize("hasAuthority('finance:memberWithdraw:modifyCashStatus')")
  @Log(
      module = ServiceName.ADMIN_SERVICE,
      type = LogType.WITHDRAW,
      desc = "'修改提款订单状态为:' + #cashStatus")
  public void modifyCashStatus(
      Long id,
      Integer cashStatus,
      Integer curStatus,
      HttpServletRequest request,
      Long memberId)
      throws Exception {
    String lock_key = "member_rw_" + memberId;
    RLock lock = distributedLocker.lock(lock_key);
    try {
      UserCredential userCredential = SecurityUserHolder.getCredential();
      UserEquipment clientInfo = UserEquipment.create(request);
      userWithdrawService.modify(
          id, cashStatus, curStatus, userCredential, clientInfo);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw e;
    } finally {
      lock.unlock();
    }
  }

  @PostMapping("/batchHandle")
  @PreAuthorize("hasAuthority('finance:memberWithdraw:batchHandle')")
  @Log(
      module = ServiceName.ADMIN_SERVICE,
      type = LogType.WITHDRAW,
      desc = "'批量受理订单:' + #list")
  public String batchHandle(String list,
      HttpServletRequest request)
      throws Exception {
    String lock_key = "member_rw_single";
    distributedLocker.lock(lock_key);
    try {
      if (null == list) {
        throw new ServiceException("批量受理请求参数为空");
      }
      UserCredential userCredential = SecurityUserHolder.getCredential();
      UserEquipment clientInfo = UserEquipment.create(request);
      List<MemberWithdrawDTO> memberWithdrawDTOList = JSONUtil
          .toList(list, MemberWithdrawDTO.class);
      for (MemberWithdrawDTO memberWithdrawDTO : memberWithdrawDTOList) {
        userWithdrawService.modify(
            memberWithdrawDTO.getId(), WithdrawStatus.HANDLED.getValue(),
            memberWithdrawDTO.getCurStatus(), userCredential,
            clientInfo);
      }
      return "成功受理" + memberWithdrawDTOList.size() + "条订单";
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw e;
    } finally {
      distributedLocker.unlock(lock_key);
    }
  }

  @PostMapping("/batchUnHandle")
  @PreAuthorize("hasAuthority('finance:memberWithdraw:batchUnHandle')")
  @Log(
      module = ServiceName.ADMIN_SERVICE,
      type = LogType.WITHDRAW,
      desc = "'批量取消受理订单:' + #list")
  public String batchModifyCashStatus(String list,
      HttpServletRequest request)
      throws Exception {
    String lock_key = "member_rw_single";
    RLock lock = distributedLocker.lock(lock_key);
    try {
      if (null == list) {
        throw new ServiceException("批量受理请求参数为空");
      }
      if (null == list) {
        throw new ServiceException("批量取消受理请求参数为空");
      }
      UserCredential userCredential = SecurityUserHolder.getCredential();
      UserEquipment clientInfo = UserEquipment.create(request);
      List<MemberWithdrawDTO> memberWithdrawDTOList = JSONUtil
          .toList(list, MemberWithdrawDTO.class);
      for (MemberWithdrawDTO memberWithdrawDTO : memberWithdrawDTOList) {
        userWithdrawService.modify(
            memberWithdrawDTO.getId(), WithdrawStatus.UNHANDLED.getValue(),
            memberWithdrawDTO.getCurStatus(), userCredential,
            clientInfo);
      }
      return "成功取消受理" + memberWithdrawDTOList.size() + "条订单";
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw e;
    } finally {
      distributedLocker.unlock(lock_key);
    }
  }

  @PostMapping("/batchWithdraw")
  @PreAuthorize("hasAuthority('finance:memberWithdraw:batchWithdraw')")
  @Log(
      module = ServiceName.ADMIN_SERVICE,
      type = LogType.WITHDRAW,
      desc = "'批量出款订单:' + #list")
  public String batchWithdraw(String list,
      HttpServletRequest request)
      throws Exception {
    String lock_key = "member_rw_single";
    distributedLocker.lock(lock_key);
    try {
      if (null == list) {
        throw new ServiceException("批量受理请求参数为空");
      }
      if (null == list) {
        throw new ServiceException("批量出款请求参数为空");
      }
      UserCredential userCredential = SecurityUserHolder.getCredential();
      UserEquipment clientInfo = UserEquipment.create(request);
      List<MemberWithdrawDTO> memberWithdrawDTOList = JSONUtil
          .toList(list, MemberWithdrawDTO.class);
      for (MemberWithdrawDTO memberWithdrawDTO : memberWithdrawDTOList) {
        userWithdrawService.modify(
            memberWithdrawDTO.getId(), WithdrawStatus.SUCCESS.getValue(),
            memberWithdrawDTO.getCurStatus(), userCredential,
            clientInfo);
      }
      return "成功出款" + memberWithdrawDTOList.size() + "条订单";
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw e;
    } finally {
      distributedLocker.unlock(lock_key);
    }
  }

  @PostMapping("/editDiscount")
  @PreAuthorize("hasAuthority('finance:memberWithdraw:editDiscount')")
  @Log(
      module = ServiceName.ADMIN_SERVICE,
      type = LogType.WITHDRAW,
      desc = "'修改手续费为:' + #afterCounterFee")
  public void updateDiscount(Long id, BigDecimal afterCounterFee, Long memberId) {
    String lock_key = "member_rw_" + memberId;
    distributedLocker.lock(lock_key);
    try {
      userWithdrawService.updateCounterFee(id, afterCounterFee);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw e;
    } finally {
      distributedLocker.unlock(lock_key);
    }
  }

  @PostMapping("/editRemarks")
  @PreAuthorize("hasAuthority('finance:memberWithdraw:editRemarks')")
  @Log(module = ServiceName.ADMIN_SERVICE, type = LogType.WITHDRAW, desc = "'修改备注为:' + #remarks")
  public void updateRemarks(Long id, String remarks, Long memberId) {
      userWithdrawService.updateRemarks(id, remarks);
  }

  @PostMapping("/page")
  @PreAuthorize("hasAuthority('finance:memberWithdraw:view')")
  public PageExt<MemberWithdrawVO, SummaryVO> queryPage(
      Page<MemberWithdraw> page, MemberWithdrawQueryDTO dto) {
    return userWithdrawService.findPage(page, dto);
  }

  @PostMapping("/queryAvailableMerchant")
  @PreAuthorize("hasAuthority('finance:memberWithdraw:queryAvailableMerchant')")
  public List<PpMerchant> queryAvailableMerchant(Long id) {
    return userWithdrawService.queryProxyMerchant(id);
  }

  @PostMapping("/save")
  @PreAuthorize("hasAuthority('finance:memberWithdraw:save')")
  @Log(
      module = ServiceName.ADMIN_SERVICE,
      type = LogType.WITHDRAW,
      desc = "'人工出款memberId =' + #memberId")
  public void save(BigDecimal cashMoney, String cashReason, Integer handPoints, Long memberId)
      throws Exception {
    UserCredential userCredential = SecurityUserHolder.getCredential();
    String lock_key = "withdraw_save_" + userCredential.getUserId();
    distributedLocker.lock(lock_key);
    try {
      userWithdrawService.save(cashMoney, cashReason, handPoints, userCredential, memberId);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      throw e;
    } finally {
      distributedLocker.unlock(lock_key);
    }
  }
}
