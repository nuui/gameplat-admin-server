package com.gameplat.admin.service.impl;

import cn.hutool.core.util.NumberUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gameplat.admin.constant.MemberServiceKeyConstant;
import com.gameplat.admin.enums.ActivityDistributeEnum;
import com.gameplat.admin.enums.MemberWealRewordEnums;
import com.gameplat.admin.enums.PushMessageEnum;
import com.gameplat.admin.mapper.ActivityDistributeMapper;
import com.gameplat.admin.model.dto.MessageInfoAddDTO;
import com.gameplat.admin.model.vo.MemberInfoVO;
import com.gameplat.admin.service.*;
import com.gameplat.base.common.exception.ServiceException;
import com.gameplat.common.constant.CachedKeys;
import com.gameplat.common.enums.BooleanEnum;
import com.gameplat.common.enums.TranTypes;
import com.gameplat.model.entity.ValidWithdraw;
import com.gameplat.model.entity.activity.ActivityDistribute;
import com.gameplat.model.entity.activity.ActivityQualification;
import com.gameplat.model.entity.member.MemberBill;
import com.gameplat.model.entity.member.MemberWealReword;
import com.gameplat.redis.redisson.DistributedLocker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.MessageFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @author aBen
 * @date 2021/12/12 21:06
 * @desc
 */
@Service
@Transactional(isolation = Isolation.DEFAULT, rollbackFor = Throwable.class)
public class ActivityDistributeWayService
    extends ServiceImpl<ActivityDistributeMapper, ActivityDistribute> {

  @Autowired private MemberWealRewordService memberWealRewordService;

  @Autowired private DistributedLocker distributedLocker;

  @Autowired private MemberInfoService memberInfoService;

  @Lazy @Autowired private ActivityQualificationService activityQualificationService;

  @Autowired private ValidWithdrawService validWithdrawService;

  @Autowired private MessageInfoService messageInfoService;

  @Autowired private MemberBillService memberBillService;

  @Autowired private MemberService memberService;

  @Transactional(rollbackFor = Throwable.class)
  public void directRelease(ActivityDistribute activityDistribute, MemberWealReword wealReword) {
    // 账户资金锁
    String lockKey = String.format(CachedKeys.MEMBER_FINANCE, activityDistribute.getUsername());
    try {
      // 获取资金锁（等待6秒，租期120秒）
      boolean flag = distributedLocker.tryLock(lockKey, TimeUnit.SECONDS, 6, 120);
      // 6秒获取不到资金锁，派发下一个会员
      if (!flag) {
        throw new ServiceException("获取资金锁失败");
      }
      ActivityDistribute activityDistribute1 = this.getById(activityDistribute.getDistributeId());
      if (activityDistribute1.getStatus()
          != ActivityDistributeEnum.ActivityDistributeStatus.SETTLEMENT.getValue()) {
        throw new ServiceException("该记录状态不是未结算状态");
      }
      // 初始化参数
      String sourceId = activityDistribute.getQualificationActivityId().toString();
      Integer status = null;
      String remark = null;
      BigDecimal amount = null;
      // 获取会员信息
      MemberInfoVO memberInfoVO = memberService.getMemberInfo(wealReword.getUserName());
      amount = memberInfoVO.getBalance() == null ? BigDecimal.ZERO : memberInfoVO.getBalance();
      // 更新用户的金额
      memberInfoService.updateBalance(
          activityDistribute.getUserId(), activityDistribute.getDiscountsMoney());

      // 账号余额更新成功,修改派发状态
      ActivityDistribute distribute = new ActivityDistribute();
      distribute.setDistributeId(activityDistribute.getDistributeId());
      distribute.setStatus(ActivityDistributeEnum.ActivityDistributeStatus.SETTLED.getValue());
      distribute.setSettlementTime(new Date());
      this.updateById(distribute);

      // 修改资格使用时间和次数
      ActivityQualification activityQualification = new ActivityQualification();
      activityQualification.setQualificationActivityId(
          activityDistribute.getQualificationActivityId());
      activityQualification.setEmployNum(1);
      activityQualification.setEmployTime(new Date());
      activityQualificationService.updateQualificationStatus(activityQualification);

      // 现金流水信息
      status = 3;
      remark =
          MessageFormat.format(
              "会员:{0},活动派发:{1},受理成功",
              activityDistribute.getUsername(), activityDistribute.getDiscountsMoney());
      if (NumberUtil.isGreater(
          activityDistribute.getWithdrawDml(), BigDecimal.ZERO)) {
        // 新增打码量记录
        ValidWithdraw validWithdraw = new ValidWithdraw();
        validWithdraw.setAccount(activityDistribute.getUsername());
        validWithdraw.setMemberId(activityDistribute.getUserId());
        validWithdraw.setRechId(sourceId);
        validWithdraw.setDiscountMoney(
            activityDistribute.getDiscountsMoney().setScale(2, RoundingMode.HALF_UP));
        validWithdraw.setDiscountDml(
            activityDistribute.getWithdrawDml().setScale(2, RoundingMode.HALF_UP));
        validWithdraw.setMormDml(BigDecimal.ZERO);

        validWithdraw.setType(0);
        validWithdraw.setStatus(BooleanEnum.NO.value());

        validWithdraw.setRechMoney(BigDecimal.ZERO);
        validWithdraw.setRemark(getDistributeRemark(activityDistribute));
        deleteByUserId(activityDistribute.getUserId());
        updateTypeByUserId(activityDistribute.getUserId(), validWithdraw.getCreateTime());
        validWithdrawService.save(validWithdraw);
      }

      // 添加流水记录
      MemberBill memberBill = new MemberBill();
      memberBill.setMemberId(activityDistribute.getUserId());
      memberBill.setAccount(memberInfoVO.getAccount());
      memberBill.setMemberPath(memberInfoVO.getSuperPath());
      memberBill.setTranType(TranTypes.PROMOTION_BONUS.getValue());
      memberBill.setOrderNo(sourceId);
      memberBill.setAmount(
          activityDistribute.getDiscountsMoney().setScale(2, RoundingMode.HALF_UP));
      memberBill.setBalance(memberInfoService.getById(activityDistribute.getUserId()).getBalance());
      memberBill.setRemark(remark);
      memberBill.setContent(remark);
      memberBill.setOperator("system");
      memberBillService.save(memberBill);

      // 插入福利中心记录(已领取)
      wealReword.setStatus(MemberWealRewordEnums.MemberWealRewordStatus.COMPLETED.getValue());
      wealReword.setUserType(memberInfoVO.getUserType());
      wealReword.setParentId(memberInfoVO.getParentId().longValue());
      wealReword.setParentName(memberInfoVO.getParentName());
      wealReword.setAgentPath(memberInfoVO.getSuperPath());
      memberWealRewordService.save(wealReword);

      // 通知 发个人消息
      MessageInfoAddDTO message = new MessageInfoAddDTO();
      message.setContent(getInformationContent(activityDistribute));
      message.setTitle("活动派发");
      message.setPushRange(PushMessageEnum.UserRange.SOME_MEMBERS.getValue());
      message.setLinkAccount(activityDistribute.getUsername());
      message.setCategory(PushMessageEnum.MessageCategory.SYS_SEND.getValue());
      message.setPosition(PushMessageEnum.Location.LOCATION_DEF.getValue());
      message.setShowType(PushMessageEnum.MessageShowType.SHOW_DEF.value());
      message.setPopsCount(PushMessageEnum.PopCount.POP_COUNT_DEF.getValue());
      message.setType(PushMessageEnum.MessageType.SYSTEM_INFORMATION.value());
      message.setCreateBy("System");
      messageInfoService.insertMessage(message);
    } finally {
      // 释放资金锁
      distributedLocker.unlock(lockKey);
    }
  }

  @Transactional
  public void welfareCenter(ActivityDistribute activityDistribute, MemberWealReword wealReword) {
    wealReword.setStatus(MemberWealRewordEnums.MemberWealRewordStatus.UNACCALIMED.getValue());
    memberWealRewordService.save(wealReword);

    // 账变成功,修改派发状态
    ActivityDistribute distribute = new ActivityDistribute();
    distribute.setDistributeId(activityDistribute.getDistributeId());
    distribute.setStatus(ActivityDistributeEnum.ActivityDistributeStatus.SETTLED.getValue());
    distribute.setSettlementTime(new Date());
    this.updateById(distribute);

    // 修改资格使用时间和次数
    ActivityQualification activityQualification = new ActivityQualification();
    activityQualification.setQualificationActivityId(
        activityDistribute.getQualificationActivityId());
    activityQualification.setEmployNum(1);
    activityQualification.setEmployTime(new Date());
    activityQualificationService.updateQualificationStatus(activityQualification);

    // 通知 发个人消息
    MessageInfoAddDTO message = new MessageInfoAddDTO();
    message.setContent(getInformationContent(activityDistribute));
    message.setTitle("活动派发");
    message.setPushRange(PushMessageEnum.UserRange.SOME_MEMBERS.getValue());
    message.setLinkAccount(activityDistribute.getUsername());
    message.setCategory(PushMessageEnum.MessageCategory.SYS_SEND.getValue());
    message.setPosition(PushMessageEnum.Location.LOCATION_DEF.getValue());
    message.setShowType(PushMessageEnum.MessageShowType.SHOW_DEF.value());
    message.setPopsCount(PushMessageEnum.PopCount.POP_COUNT_DEF.getValue());
    message.setType(PushMessageEnum.MessageType.SYSTEM_INFORMATION.value());
    message.setCreateBy("System");
    messageInfoService.insertMessage(message);
  }

  /**
   * 派发说明
   *
   * @param distributeVO
   * @return
   */
  private String getDistributeRemark(ActivityDistribute distributeVO) {
    StringBuilder str =
        new StringBuilder("用户:")
            .append(distributeVO.getUsername())
            .append(",活动派发:")
            .append(distributeVO.getDiscountsMoney())
            .append(",打码量要求:")
            .append(distributeVO.getWithdrawDml())
            .append("。");
    return str.toString();
  }

  /**
   * 生成消息字符串
   *
   * @param distributeVO
   * @return
   */
  private String getInformationContent(ActivityDistribute distributeVO) {
    StringBuilder str = new StringBuilder();
    if (ActivityDistributeEnum.ActivityDistributeGetWayEnum.DIRECT_RELEASE.getValue()
        == distributeVO.getGetWay()) {
      str.append(distributeVO.getUsername())
          .append("奖励已派发,金额:")
          .append(distributeVO.getDiscountsMoney())
          .append("。");
    } else if (ActivityDistributeEnum.ActivityDistributeGetWayEnum.WELFARE_CENTER.getValue()
        == distributeVO.getGetWay()) {
      str.append(distributeVO.getUsername())
          .append(",奖励已发送到福利中心,请到福利中心领取,金额:")
          .append(distributeVO.getDiscountsMoney())
          .append("。");
    }
    return str.toString();
  }

  private void deleteByUserId(Long memberId) {
    LambdaQueryWrapper<ValidWithdraw> query = Wrappers.lambdaQuery();
    query.eq(ValidWithdraw::getMemberId, memberId).eq(ValidWithdraw::getStatus, 1);
    this.validWithdrawService.remove(query);
  }

  private void updateTypeByUserId(Long memberId, Date createTime) {
    this.validWithdrawService.lambdaUpdate()
      .set(ValidWithdraw::getType, 1)
      .set(ValidWithdraw::getEndTime, createTime)
      .eq(ValidWithdraw::getMemberId, memberId)
      .eq(ValidWithdraw::getType, 0)
      .update(new ValidWithdraw());
  }
}
