package com.gameplat.admin.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.gameplat.admin.component.WithdrawQueryCondition;
import com.gameplat.admin.constant.WithdrawTypeConstant;
import com.gameplat.admin.convert.MemberWithdrawConvert;
import com.gameplat.admin.enums.BlacklistConstant.BizBlacklistType;
import com.gameplat.admin.enums.CashEnum;
import com.gameplat.admin.enums.ProxyPayStatusEnum;
import com.gameplat.admin.feign.MessageFeignClient;
import com.gameplat.admin.mapper.MemberWithdrawMapper;
import com.gameplat.admin.mapper.MessageMapper;
import com.gameplat.admin.model.bean.AdminLimitInfo;
import com.gameplat.admin.model.bean.DirectCharge;
import com.gameplat.admin.model.bean.ManualRechargeOrderBo;
import com.gameplat.admin.model.bean.ProxyPayMerBean;
import com.gameplat.admin.model.dto.MemberWithdrawDTO;
import com.gameplat.admin.model.dto.MemberWithdrawQueryDTO;
import com.gameplat.admin.model.vo.MemberWithdrawBankVo;
import com.gameplat.admin.model.vo.MemberWithdrawVO;
import com.gameplat.admin.model.vo.RechargeOrderHistoryVO;
import com.gameplat.admin.model.vo.SummaryVO;
import com.gameplat.admin.service.*;
import com.gameplat.admin.util.MoneyUtils;
import com.gameplat.base.common.exception.ServiceException;
import com.gameplat.base.common.json.JsonUtils;
import com.gameplat.base.common.snowflake.IdGeneratorSnowflake;
import com.gameplat.base.common.util.DateUtil;
import com.gameplat.base.common.util.StringUtils;
import com.gameplat.common.constant.CachedKeys;
import com.gameplat.common.constant.NumberConstant;
import com.gameplat.common.constant.SocketEnum;
import com.gameplat.common.enums.*;
import com.gameplat.common.metadata.Pages;
import com.gameplat.common.model.bean.Builder;
import com.gameplat.common.model.bean.UserEquipment;
import com.gameplat.common.model.bean.limit.MemberRechargeLimit;
import com.gameplat.common.model.bean.limit.MemberWithdrawLimit;
import com.gameplat.message.model.MessagePayload;
import com.gameplat.model.entity.member.*;
import com.gameplat.model.entity.message.Message;
import com.gameplat.model.entity.message.MessageDistribute;
import com.gameplat.model.entity.pay.PpInterface;
import com.gameplat.model.entity.pay.PpMerchant;
import com.gameplat.model.entity.sys.SysDictData;
import com.gameplat.model.entity.sys.SysUser;
import com.gameplat.redis.redisson.DistributedLocker;
import com.gameplat.security.SecurityUserHolder;
import com.gameplat.security.context.UserCredential;
import jodd.util.StringUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.redisson.api.RLock;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@Transactional(isolation = Isolation.DEFAULT, rollbackFor = Throwable.class)
public class MemberWithdrawServiceImpl extends ServiceImpl<MemberWithdrawMapper, MemberWithdraw>
    implements MemberWithdrawService {

  @Autowired private MemberWithdrawConvert userWithdrawConvert;

  @Autowired(required = false)
  private MemberWithdrawMapper memberWithdrawMapper;

  @Autowired private MemberWithdrawHistoryService memberWithdrawHistoryService;

  @Autowired private MemberService memberService;

  @Autowired private SysUserService sysUserService;

  @Autowired private LimitInfoService limitInfoService;

  @Autowired private PpInterfaceService ppInterfaceService;

  @Autowired private PpMerchantService ppMerchantService;

  @Autowired private MemberInfoService memberInfoService;

  @Autowired private MemberBillService memberBillService;

  @Autowired private ValidWithdrawService validWithdrawService;

  @Autowired private RechargeOrderService rechargeOrderService;

  @Autowired private BizBlacklistFacade bizBlacklistFacade;

  @Autowired private ConfigService configService;

  @Autowired private MemberRwReportService memberRwReportService;

  @Autowired(required = false)
  private MessageMapper messageMapper;

//  @Autowired private MessageDistributeService messageDistributeService;
  @Autowired private SysDictDataService sysDictDataService;

  @Autowired(required = false)
  private MessageFeignClient client;

  @Autowired
  private DistributedLocker distributedLocker;

  private static boolean verifyPpMerchant(
      MemberWithdraw memberWithdraw, PpMerchant ppMerchant, PpInterface ppInterface) {
    ProxyPayMerBean proxyPayMerBean = ProxyPayMerBean.conver2Bean(ppMerchant.getMerLimits());
    if (memberWithdraw.getCashMoney().compareTo(proxyPayMerBean.getMinLimitCash()) < 0
        || memberWithdraw.getCashMoney().compareTo(proxyPayMerBean.getMaxLimitCash()) > 0) {
      log.info("用户出款金额超出商户出款金额范围，过滤此商户，商户名称为：" + ppMerchant.getName());
      return true;
    }

    if (StringUtils.isNotEmpty(proxyPayMerBean.getUserLever())) {
      if (!StringUtils.contains(
          "," + proxyPayMerBean.getUserLever() + ",",
          String.format("%s" + memberWithdraw.getMemberLevel() + "%s", ",", ","))) {
        log.info("用户层级不在此代付商户设置的层级中，过滤此商户，商户名称为：" + ppMerchant.getName());
        return true;
      }
    }

    /** 虚拟币出款不过滤三方 */
    if (!WithdrawTypeConstant.DIRECT.equals(memberWithdraw.getWithdrawType())
            || !WithdrawTypeConstant.BANK.equals(memberWithdraw.getWithdrawType())) {
      log.info("代付商户虚拟币，过滤此商户，商户名称为：" + ppMerchant.getName());
      return false;
    }

    List<MemberWithdrawBankVo> bankVoList =
            JSONUtil.toList(
                    (JSONArray) JSONUtil.parseObj(ppInterface.getLimtInfo()).get("banks"),
                    MemberWithdrawBankVo.class);
    // 模糊匹配银行名称
    boolean isBankName = true;
    for (MemberWithdrawBankVo ex : bankVoList) {
      if (StringUtils.contains(ex.getName(), memberWithdraw.getBankName())
              || StringUtils.contains(memberWithdraw.getBankName(), ex.getName())) {
        isBankName = false;
        break;
      }
      if (StringUtils.contains(ex.getName(), "邮政")
          && StringUtils.contains(memberWithdraw.getBankName(), "邮政")) {
        isBankName = false;
        break;
      }
    }
    if (isBankName) {
      log.info("代付商户不支持用户银行卡出款，过滤此商户，商户名称为：" + ppMerchant.getName());
      return true;
    }
    return false;
  }

  @Autowired
  private WithdrawQueryCondition condition;

  @Override
  public IPage<MemberWithdrawVO> findPage(Page<MemberWithdraw> page, MemberWithdrawQueryDTO dto) {
    QueryWrapper<MemberWithdraw> queryWrapper = condition.buildQuerySql(dto);
    // 统计受理订单总金额、未受理订单总金额
    return Pages.of(memberWithdrawMapper.findPage(page, queryWrapper), amountSum());
  }

  @Override
  public void updateCounterFee(Long id, BigDecimal afterCounterFee) {
    if (null == afterCounterFee || afterCounterFee.compareTo(BigDecimal.ZERO) <= 0) {
      throw new ServiceException("调整后的手续费不能为空或者为负数，请检查！");
    }
    MemberWithdraw memberWithdraw = this.getById(id);
    BigDecimal approveMoney = memberWithdraw.getCashMoney().subtract(afterCounterFee);
    BigDecimal approveCurrencyCount = BigDecimal.ZERO;
    if (Objects.nonNull(memberWithdraw.getWithdrawType())
        && !memberWithdraw.getWithdrawType().equals(WithdrawTypeConstant.BANK)
        && !memberWithdraw.getWithdrawType().equals(WithdrawTypeConstant.DIRECT)) {
      approveCurrencyCount = approveMoney.divide(memberWithdraw.getCurrencyRate());
    }
    LambdaUpdateWrapper<MemberWithdraw> update = Wrappers.lambdaUpdate();
    update
        .set(MemberWithdraw::getCounterFee, afterCounterFee)
        .set(MemberWithdraw::getApproveMoney, approveMoney)
        .set(MemberWithdraw::getApproveCurrencyCount, approveCurrencyCount)
        .eq(MemberWithdraw::getId, id);
    if (!this.update(new MemberWithdraw(), update)) {
      log.error("更新提现订单手续费：id=" + id + ",调整后手续费afterCounterFee=" + afterCounterFee);
      throw new ServiceException("订单已处理");
    }
  }

  @Override
  public void updateRemarks(Long id, String cashReason) {
    if (null == cashReason) {
      throw new ServiceException("备注信息不能为空!");
    }
    LambdaUpdateWrapper<MemberWithdraw> update = Wrappers.lambdaUpdate();
    update.set(MemberWithdraw::getCashReason, cashReason).eq(MemberWithdraw::getId, id);
    this.update(new MemberWithdraw(), update);
  }

  @Override
  public void modify(
          Long id,
          Integer cashStatus,
          Integer curStatus,
          UserEquipment userEquipment,
          String cashReason) {
    if (null == id || null == cashStatus || null == curStatus || cashStatus.equals(curStatus)) {
      throw new ServiceException("错误的参数.");
    }

    MemberWithdraw memberWithdraw = this.getById(id);
    if (memberWithdraw == null) {
      throw new ServiceException("充值订单不存在或订单已处理");
    }

    // 是否为免提直充
    boolean isDirect = WithdrawTypeConstant.DIRECT.equals(memberWithdraw.getWithdrawType());
    Integer origCashStatus = memberWithdraw.getCashStatus();
    if (!curStatus.equals(origCashStatus)) {
      throw new ServiceException("订单状态已变化,请刷新重试.");
    }

    boolean isFinishedOrder =
        (WithdrawStatus.SUCCESS.getValue() == curStatus
            || WithdrawStatus.CANCELLED.getValue() == curStatus
            || WithdrawStatus.REFUSE.getValue() == curStatus);
    if (isFinishedOrder) {
      throw new ServiceException("出款订单已完成,请确认再试");
    }

    Member member = memberService.getById(memberWithdraw.getMemberId());
    MemberInfo memberInfo = memberInfoService.getById(memberWithdraw.getMemberId());
    if (member == null || memberInfo == null) {
      log.error("用户ID不存在:" + memberWithdraw.getMemberId());
      throw new ServiceException("用户不存在");
    }

    UserCredential credential = SecurityUserHolder.getCredential();
    if (WithdrawStatus.UNHANDLED.getValue() != curStatus) {
      // 验证已受理出款订单是否开启允许其他账户验证
      crossAccountCheck(credential, memberWithdraw);
    }

    // 验证出款流程 ,未受理订单是否可以直接入款
    if (ObjectUtil.equals(WithdrawStatus.SUCCESS.getValue(), cashStatus)) {
      withdrawProcess(memberWithdraw, cashStatus);
    }

    // 校验子账号当天受理会员取款审核额度
    if (null != credential.getUsername()
        && StringUtils.equals(UserTypes.SUBUSER.value(), credential.getUserType())
        && WithdrawStatus.SUCCESS.match(cashStatus)) {
      SysUser sysUser = sysUserService.getByUsername(credential.getUsername());
      AdminLimitInfo adminLimitInfo = JsonUtils.parse(sysUser.getLimitInfo(), AdminLimitInfo.class);
      checkZzhWithdrawAmountAudit(
          adminLimitInfo,
          memberWithdraw.getCashMode(),
          memberWithdraw.getCashMoney(),
          credential.getUsername());
    }
    String approveReason = null;
    if (isDirect) {
      approveReason = "免提直充";
    }
    // 设置会员层级
    memberWithdraw.setMemberLevel(member.getUserLevel());
    memberWithdraw.setCashStatus(cashStatus);
    memberWithdraw.setApproveReason(approveReason);
    boolean toFinishCurrentOrder =
        (WithdrawStatus.SUCCESS.getValue() == cashStatus
            || WithdrawStatus.CANCELLED.getValue() == cashStatus
            || WithdrawStatus.REFUSE.getValue() == cashStatus);
    if (Objects.equals(WithdrawStatus.HANDLED.getValue(), cashStatus)) {
      memberWithdraw.setAcceptAccount(credential.getUsername());
      memberWithdraw.setAcceptTime(new Date());
      updateWithdraw(memberWithdraw, origCashStatus);
      log.info("提现订单受理中 ，订单号为：{}", memberWithdraw.getCashOrderNo());
    } else if (Objects.equals(WithdrawStatus.UNHANDLED.getValue(), cashStatus)) {
      memberWithdraw.setAcceptAccount(credential.getUsername());
      memberWithdraw.setAcceptTime(new Date());
      updateWithdraw(memberWithdraw, origCashStatus);
      log.info("放弃受理提现订单,订单号为：{}", memberWithdraw.getCashOrderNo());
    } else if (Objects.equals(
        ProxyPayStatusEnum.PAY_PROGRESS.getCode(), memberWithdraw.getProxyPayStatus())) {
      memberWithdraw.setAcceptAccount(credential.getUsername());
      memberWithdraw.setAcceptTime(new Date());
      updateWithdraw(memberWithdraw, origCashStatus);
      log.info("提现订单 第三方出款中 ，订单号为：{}", memberWithdraw.getCashOrderNo());
    } else if (toFinishCurrentOrder) {
      String lock_key = String.format(CachedKeys.MEMBER_FINANCE, memberWithdraw.getAccount());
      RLock lock = distributedLocker.lock(lock_key);
      try {
        memberWithdraw.setOperatorAccount(credential.getUsername());
        memberWithdraw.setOperatorTime(new Date());
        updateWithdraw(memberWithdraw, origCashStatus);
        if (WithdrawStatus.SUCCESS.getValue() == cashStatus) {
          // 添加會員出入款的報表記錄,如果是推广用户，则不计入报表
          if (!UserTypes.PROMOTION.value().equals(member.getUserType())) {
            memberRwReportService.addWithdraw(
                    member, memberInfo.getTotalWithdrawTimes(), memberWithdraw);
          }
          // 删除出款验证打码量记录的数据
          validWithdrawService.remove(memberWithdraw.getMemberId(), memberWithdraw.getCreateTime());
          // 免提直充
          if (isDirect) {
            this.directCharge(memberWithdraw, credential, userEquipment);
          }
          // 移除会员提现冻结金额
          memberInfoService.updateFreeze(member.getId(), memberWithdraw.getCashMoney().negate());
          // 计算会员出款次数和金额
          memberInfoService.updateUserWithTimes(
                  member.getId(),
                  memberWithdraw.getCashMoney().negate(),
                  memberWithdraw.getPointFlag());
        } else if (WithdrawStatus.CANCELLED.getValue() == cashStatus) { // 取消出款操作
          if (ObjectUtil.isNotEmpty(cashReason)) {
            memberWithdraw.setApproveReason(cashReason);
            if (null == cashReason) {
              throw new ServiceException("备注信息不能为空!");
            }
            LambdaUpdateWrapper<MemberWithdraw> update = Wrappers.lambdaUpdate();
            update
                    .set(MemberWithdraw::getApproveReason, cashReason)
                    .set(MemberWithdraw::getOperatorTime, new Date())
                    .set(MemberWithdraw::getOperatorAccount, credential.getUsername())
                    .eq(MemberWithdraw::getId, id);

            this.update(new MemberWithdraw(), update);
          }
          // 释放会员提现冻结金额
          memberInfoService.updateFreeze(member.getId(), memberWithdraw.getCashMoney().negate());
          // 释放会员提现金额
          memberInfoService.updateBalance(member.getId(), memberWithdraw.getCashMoney());
          String billContent =
                  String.format(
                          "管理员于%s向用户%s提现失败退回%.3f元,账户余额变更为:%.3f元",
                          DateUtil.getNowTime(),
                          member.getAccount(),
                          memberWithdraw.getCashMoney(),
                          (memberInfo.getBalance().add(memberWithdraw.getCashMoney())));
          MemberBill bill = new MemberBill();
          bill.setBalance(memberInfo.getBalance());
          bill.setOrderNo(memberWithdraw.getCashOrderNo());
          bill.setTranType(TranTypes.WITHDRAW_FAIL.getValue());
          bill.setAmount(memberWithdraw.getCashMoney());
          bill.setContent(billContent);
          bill.setOperator(credential.getUsername());
          memberBillService.save(member, bill);
        } else if (WithdrawStatus.REFUSE.getValue() == cashStatus) {
          String content =
                  String.format(
                          "您于%s提交的取现订单被没收，订单号为%s，金额：%s",
                          DateUtil.getDateToString(
                                  memberWithdraw.getCreateTime(), DateUtil.YYYY_MM_DD_HH_MM_SS),
                          memberWithdraw.getCashOrderNo(),
                          memberWithdraw.getCashMoney());
          MemberBill bill = new MemberBill();
          bill.setBalance(memberInfo.getBalance());
          bill.setOrderNo(memberWithdraw.getCashOrderNo());
          bill.setTranType(TranTypes.WITHDRAW_FAIL.getValue());
          bill.setAmount(BigDecimal.ZERO);
          bill.setContent(content);
          bill.setOperator(credential.getUsername());
          memberBillService.save(member, bill);
          // 移除会员提现冻结金额
          memberInfoService.updateFreeze(member.getId(), memberWithdraw.getCashMoney().negate());
        }
      } finally {
        distributedLocker.unlock(lock);
      }
      // 新增出款记录
      MemberWithdraw withdraw = this.getById(id);
      insertWithdrawHistory(withdraw);
    }
    // 新增消息
    if (ObjectUtil.equals(cashStatus, WithdrawStatus.SUCCESS.getValue())
        || ObjectUtil.equals(cashStatus, WithdrawStatus.CANCELLED.getValue())) {
      if (ObjectUtil.equals(WithdrawStatus.SUCCESS.getValue(), cashStatus)) {
        this.addMessageInfo(memberWithdraw, WithdrawStatus.SUCCESS.getValue());
        MemberWithdrawLimit withradLimit = limitInfoService.getWithradLimit();
        this.sendMessage(
            memberWithdraw.getAccount(),
                SocketEnum.SOCKET_WITHDRAW_SUCCESS,
            withradLimit.getUserApplyLoanAfterHintsMessage());
      } else if (ObjectUtil.equals(cashStatus, WithdrawStatus.CANCELLED.getValue())) {
        this.addMessageInfo(memberWithdraw, WithdrawStatus.CANCELLED.getValue());
        this.sendMessage(
            memberWithdraw.getAccount(),
                SocketEnum.SOCKET_WITHDRAW_CANCEL,
            SocketEnum.SEND_WITHDRAW_FAIL_MESSAGE);
      }
    }
  }

  @Override
  public void batchModify(
          List<MemberWithdrawDTO> dtoList,
          WithdrawStatus status,
          UserEquipment equipment,
          String cashReason) {
    dtoList.forEach(
            e -> modify(e.getId(), status.getValue(), e.getCurStatus(), equipment, cashReason));
  }

  /** 过滤不符合规则的第三方出款商户 */
  @Override
  public List<PpMerchant> queryProxyMerchant(Long id) {
    // 根据体现记录查询用户的层级和出款金额
    MemberWithdraw memberWithdraw = this.getById(id);
    if (null == memberWithdraw) {
      throw new ServiceException("不存在的记录");
    }

    // 获取所有的可用代付商户
    List<PpMerchant> merchantList =
        ppMerchantService.queryAllMerchant(SwitchStatusEnum.ENABLED.getValue());
    if (CollectionUtils.isEmpty(merchantList)) {
      throw new ServiceException("没有可用的代付商户");
    }
    // 根据用户体现信息，过滤相关代付相符
    Iterator<PpMerchant> iterator = merchantList.iterator();
    while (iterator.hasNext()) {
      PpMerchant ppMerchant = iterator.next();
      PpInterface ppInterface = ppInterfaceService.get(ppMerchant.getPpInterfaceCode());

      if (verifyPpMerchant(memberWithdraw, ppMerchant, ppInterface)) {
        iterator.remove();
      }
    }
    return merchantList;
  }

  @Override
  public void save(BigDecimal cashMoney, String cashReason, Integer handPoints, Long memberId) {
    Member member = memberService.getById(memberId); // 更新金额，从数据库中重新获取
    MemberInfo memberInfo = memberInfoService.getById(memberId); // 更新金额，从数据库中重新获取
    // 校验用户状态
    checkUserInfo(member, memberInfo, false);
    // 判断金额是否为负数或者为0
    if (cashMoney.compareTo(BigDecimal.ZERO) <= 0) {
      throw new ServiceException("取款额度不能为0");
    }

    // 检查取款金额是否超过用户余额
    if (cashMoney.compareTo(memberInfo.getBalance()) > 0) {
      throw new ServiceException("取款额度不能超过余额");
    }

    // 校验子账号当天受理人工取款审核额度
    UserCredential credential = SecurityUserHolder.getCredential();
    SysUser sysUser = sysUserService.getByUsername(credential.getUsername());
    AdminLimitInfo adminLimitInfo = JsonUtils.parse(sysUser.getLimitInfo(), AdminLimitInfo.class);
    if (null != adminLimitInfo
        && StringUtils.equals(UserTypes.SUBUSER.value(), credential.getUserType())) {
      checkZzhWithdrawAmountAudit(
          adminLimitInfo, CashEnum.CASH_MODE_HAND.getValue(), cashMoney, credential.getUsername());
    }

    // 下面开始添加后台出款记录
    MemberWithdraw memberWithdraw = new MemberWithdraw();
    memberWithdraw.setCashOrderNo(String.valueOf(IdGeneratorSnowflake.getInstance().nextId()));
    memberWithdraw.setMemberId(memberId);
    memberWithdraw.setAccount(member.getAccount());
    memberWithdraw.setRealName(member.getRealName());
    memberWithdraw.setAccountMoney(memberInfo.getBalance());
    memberWithdraw.setCashMoney(cashMoney);
    memberWithdraw.setCashReason(cashReason);
    memberWithdraw.setCashMode(CashEnum.CASH_MODE_HAND.getValue());
    memberWithdraw.setCashStatus(WithdrawStatus.SUCCESS.getValue());
    memberWithdraw.setCreateTime(new Date());
    memberWithdraw.setCounterFee(BigDecimal.ZERO);
    memberWithdraw.setApproveMoney(cashMoney);
    memberWithdraw.setMemberLevel(member.getUserLevel());
    memberWithdraw.setSuperId(member.getParentId());
    memberWithdraw.setSuperName(member.getParentName());
    memberWithdraw.setSuperPath(member.getSuperPath());
    memberWithdraw.setPoliceFlag(0);
    memberWithdraw.setMacOs(credential.getDeviceType());
    memberWithdraw.setUserAgent(credential.getUserAgent());
    memberWithdraw.setIpAddress(credential.getLoginIp());
    memberWithdraw.setOperatorAccount(credential.getUsername());
    memberWithdraw.setOperatorTime(new Date());
    memberWithdraw.setPointFlag(handPoints);
    memberWithdraw.setMemberType(member.getUserType());
    memberWithdraw.setWithdrawType(WithdrawTypeConstant.MANUAL); // 人工充值设置提现类型为人工充值
    this.save(memberWithdraw);
    // 添加取现历史记录
    insertWithdrawHistory(memberWithdraw);

    // 添加會員出入款的報表記錄,如果是推广用户，则不计入报表
    if (!UserTypes.PROMOTION.value().equals(member.getUserType())) {
      memberRwReportService.addWithdraw(member, memberInfo.getTotalWithdrawTimes(), memberWithdraw);
    }

    // 修改用户金额信息、扣除用户出款金额
    memberInfoService.updateBalanceWithWithdraw(memberId, memberWithdraw.getCashMoney());

    // 添加用户账户金额的变更记录
    String content =
        "管理员"
            + sysUser.getUserName()
            + "于"
            + DateUtil.getNowTime()
            + "向用户"
            + member.getAccount()
            + "成功通过人工提现"
            + String.format("%.3f", cashMoney)
            + "元,账户余额变更为:"
            + String.format("%.3f", (memberInfo.getBalance().subtract(cashMoney)))
            + "元";
    MemberBill bill = new MemberBill();
    bill.setBalance(memberInfo.getBalance());
    bill.setOrderNo(memberWithdraw.getCashOrderNo());
    bill.setTranType(TranTypes.TRANSFER_OUT.getValue());
    bill.setAmount(cashMoney.negate());
    bill.setContent(content);
    bill.setOperator(credential.getUsername());
    memberBillService.save(member, bill);
  }

  /**
   * 免提直充
   *
   * @param memberWithdraw MemberWithdraw
   * @param userCredential UserCredential
   */
  @SneakyThrows
  public void directCharge(
      MemberWithdraw memberWithdraw, UserCredential userCredential, UserEquipment userEquipment) {
    String configValue = configService.getValue(DictDataEnum.DIRECT_CHARGE);
    Optional.ofNullable(configValue).orElseThrow(() -> new ServiceException("免提直充配置异常，请检查配置是否正确。"));
    DirectCharge directCharge = JSON.parseObject(configValue, DirectCharge.class);
    String levels = directCharge.getLevels();
    int pointFlag = directCharge.getPointFlag();
    int dmlFlag = directCharge.getDmlFlag();
    int normalDmlMultiple = directCharge.getNormalDmlMultiple();
    int discountDmlMultiple = directCharge.getDiscountDmlMultiple();
    BigDecimal discountPercentage = directCharge.getDiscountPercentage();
    BigDecimal discountDml = BigDecimal.ZERO;
    BigDecimal discountAmount = BigDecimal.ZERO;
    BigDecimal approveMoney = memberWithdraw.getApproveMoney();
    // 是否直接入款
    boolean skipAuditing = directCharge.getSkipAuditing() == 1;
    // 固定值
    Integer discountType = 8080;
    // 常态打码量
    BigDecimal normalDml = BigDecimal.ZERO;
    String auditRemarks =
        StringUtil.isBlank(directCharge.getAuditRemarks())
            ? "免提直充，出款订单号：" + memberWithdraw.getCashOrderNo()
            : directCharge.getAuditRemarks(); // 审核备注

    if (!StringUtil.isBlank(levels)) {
      String[] levelArr = levels.split(",");
      boolean contains = Arrays.asList(levelArr).contains(memberWithdraw.getMemberLevel() + "");
      if (contains) {
        if (pointFlag == 1) {
          discountAmount =
              (approveMoney.multiply(discountPercentage))
                  .divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
        }
      }
    }

    if (dmlFlag == 1) {
      normalDml =
          new BigDecimal(normalDmlMultiple)
              .multiply(approveMoney)
              .setScale(2, RoundingMode.HALF_UP);
      discountDml =
          discountAmount
              .multiply(new BigDecimal(discountDmlMultiple))
              .setScale(2, RoundingMode.HALF_UP);
    }

    ManualRechargeOrderBo manualRechargeOrderBo =
        Builder.of(ManualRechargeOrderBo::new)
            .with(ManualRechargeOrderBo::setDiscountAmount, discountAmount)
            .with(ManualRechargeOrderBo::setAuditRemarks, auditRemarks)
            .with(ManualRechargeOrderBo::setPointFlag, pointFlag)
            .with(ManualRechargeOrderBo::setDmlFlag, dmlFlag)
            .with(ManualRechargeOrderBo::setSkipAuditing, skipAuditing)
            .with(ManualRechargeOrderBo::setAccount, memberWithdraw.getAccount())
            .with(ManualRechargeOrderBo::setMemberId, memberWithdraw.getMemberId())
            .with(ManualRechargeOrderBo::setAmount, approveMoney)
            .with(ManualRechargeOrderBo::setNormalDml, normalDml)
            .with(ManualRechargeOrderBo::setDiscountDml, discountDml)
            .with(ManualRechargeOrderBo::setDiscountType, discountType)
            .with(ManualRechargeOrderBo::setRemarks, directCharge.getRemarks())
            .build();
    rechargeOrderService.manual(manualRechargeOrderBo, userEquipment);
    log.info(
        "\n免提直充配置:{},\n充值信息： 会员账号：{}，入款金额：{}，优惠金额：{}，常态打码量：{},优惠打码量：{},备注：{},层级：{}",
        directCharge,
        manualRechargeOrderBo.getAccount(),
        manualRechargeOrderBo.getAmount(),
        manualRechargeOrderBo.getDiscountAmount(),
        manualRechargeOrderBo.getNormalDml(),
        manualRechargeOrderBo.getDiscountDml(),
        manualRechargeOrderBo.getAuditRemarks(),
        directCharge.getLevels());

    // 如果是直接入款则添加成长值
    if (skipAuditing) {
      // 判断是否在充值成长值黑名单
      if (bizBlacklistFacade.isUserInBlacklist(
          memberWithdraw.getMemberId(), BizBlacklistType.RECHARGE_GROWTH)) {
        log.info(
            "免提直充出款单号：cashOrderNo={}，会员账号：account={}不添加成长值，原因：该会员位于'充值成长值'黑名单！",
            memberWithdraw.getCashOrderNo(),
            memberWithdraw.getAccount());
      }
    }
  }

  @Override
  public long getUntreatedWithdrawCount() {
    return this.lambdaQuery().eq(MemberWithdraw::getCashStatus, 1).count();
  }

  private void insertWithdrawHistory(MemberWithdraw memberWithdraw) {
    MemberWithdrawHistory memberWithdrawHistory = new MemberWithdrawHistory();
    BeanUtils.copyProperties(memberWithdraw, memberWithdrawHistory);
    memberWithdrawHistoryService.save(memberWithdrawHistory);
  }

  /**
   * 检验子账号出款受理额度
   *
   * @param adminLimitInfo AdminLimitInfo
   * @param cashMode Integer
   */
  private void checkZzhWithdrawAmountAudit(
      AdminLimitInfo adminLimitInfo, Integer cashMode, BigDecimal cashMoney, String userName) {
    if (cashMode == CashEnum.CASH_MODE_USER.getValue()) {
      if (adminLimitInfo.getMaxWithdrawAmount().compareTo(BigDecimal.ZERO) > 0
          && cashMoney.compareTo(adminLimitInfo.getMaxWithdrawAmount()) > 0) {
        String buffer =
            userName
                + "单笔出款受限。受理会员取款额度为："
                + adminLimitInfo.getMaxWithdrawAmount()
                + "元。 超过额度"
                + MoneyUtils.toYuanStr(cashMoney.subtract(adminLimitInfo.getMaxWithdrawAmount()))
                + "元";
        throw new ServiceException(buffer);
      }
    } else if (cashMode == CashEnum.CASH_MODE_HAND.getValue()) {
      if (adminLimitInfo.getMaxManualWithdrawAmount().compareTo(BigDecimal.ZERO) > 0
          && cashMoney.compareTo(adminLimitInfo.getMaxManualWithdrawAmount()) > 0) {
        String buffer =
            userName
                + "单笔人工出款受限。受理人工取款额度为"
                + adminLimitInfo.getMaxManualWithdrawAmount()
                + "元。 超过额度"
                + MoneyUtils.toYuanStr(
                    cashMoney.subtract(adminLimitInfo.getMaxManualWithdrawAmount()))
                + "元";
        throw new ServiceException(buffer);
      }
    }
  }

  private void updateWithdraw(MemberWithdraw memberWithdraw, Integer origCashStatus) {
    LambdaUpdateWrapper<MemberWithdraw> update = Wrappers.lambdaUpdate();
    update
        .set(MemberWithdraw::getCashStatus, memberWithdraw.getCashStatus())
            .set(MemberWithdraw::getAcceptAccount, memberWithdraw.getAcceptAccount())
            .set(MemberWithdraw::getAcceptTime, memberWithdraw.getAcceptTime())
        .set(MemberWithdraw::getApproveReason, memberWithdraw.getCashReason())
        .set(MemberWithdraw::getOperatorAccount, memberWithdraw.getOperatorAccount())
        .set(MemberWithdraw::getOperatorTime, memberWithdraw.getOperatorTime())
        .eq(MemberWithdraw::getId, memberWithdraw.getId())
        .eq(MemberWithdraw::getCashStatus, origCashStatus);
    if (!this.update(update)) {
      log.error(
          "修改提现订单异常：memberWithdraw="
              + memberWithdraw.toString()
              + ",origCashStatus="
              + origCashStatus);
      throw new ServiceException("订单已处理");
    }
  }

  /** 开启出入款订单是否允许其他账户操作配置 校验非超管账号是否原受理人 */
  private void crossAccountCheck(UserCredential userCredential, MemberWithdraw memberWithdraw) {
    if (userCredential != null
        && StringUtils.isNotEmpty(userCredential.getUsername())
        && null != memberWithdraw) {

      MemberRechargeLimit limitInfo = limitInfoService.getRechargeLimit();
      boolean toCheck =
          BooleanEnum.NO.match(limitInfo.getIsHandledAllowOthersOperate())
              && !userCredential.isSuperAdmin();
      if (toCheck) {
        if (!Objects.equals(WithdrawStatus.UNHANDLED.getValue(), memberWithdraw.getCashStatus())
            && !StringUtils.equalsIgnoreCase(
                userCredential.getUsername(), memberWithdraw.getAcceptAccount())) {
          throw new ServiceException("您无权操作此订单:" + memberWithdraw.getCashOrderNo());
        }
      }
    }
  }

  /** 检查订单是否需要先受理 */
  private void withdrawProcess(MemberWithdraw memberWithdraw, Integer cashStatus) {
    MemberWithdrawLimit withradLimit = limitInfoService.getWithradLimit();
    boolean withdrawProcess = BooleanEnum.YES.match(withradLimit.getIsWithdrawProcess());
    if (withdrawProcess
        && memberWithdraw.getCashStatus() != WithdrawStatus.HANDLED.getValue()
        && WithdrawStatus.SUCCESS.getValue() == cashStatus) {
      throw new ServiceException("请先受理此订单:" + memberWithdraw.getCashOrderNo());
    }
  }

  /** 检查用户，封装用户信息 */
  private void checkUserInfo(Member member, MemberInfo memberInfo, boolean checkUserState) {
    // 查询用户是否存在
    if (member == null) {
      throw new ServiceException("用户信息不存在！");
    }
    // 查询用户的扩展信息是否为空
    if (memberInfo == null) {
      throw new ServiceException("用户扩展信息不存在！");
    }
    if (checkUserState) {
      // 查询用户是否正常
      if (!MemberEnums.Status.ENABlED.match(member.getStatus())) {
        throw new ServiceException("用户已经被冻结");
      }
    }
    if (member.getUserType().equals(UserTypes.TEST.value())) {
      throw new ServiceException("用户为试玩会员");
    }
  }

  /**
   * 出款消息
   *
   * @param memberWithdraw 出款信息
   * @param state 状态
   */
  private void addMessageInfo(MemberWithdraw memberWithdraw, Integer state) {
    if (this.verifyMessage() == com.gameplat.common.enums.TrueFalse.FALSE.getValue()) {
      return;
    }
    Message messageInfo = new Message();
    messageInfo.setTitle(title(state));
    messageInfo.setContent(
        context(state, memberWithdraw.getCreateTime(), memberWithdraw.getCashMoney()));
    messageInfo.setCategory(4);
    messageInfo.setPosition(1);
    messageInfo.setShowType(0);
    messageInfo.setPopsCount(0);
    messageInfo.setPushRange(2);
    messageInfo.setLinkAccount(memberWithdraw.getAccount());
    messageInfo.setSort(0);
    messageInfo.setType(1);
    messageInfo.setLanguage("zh-CN");
    messageInfo.setStatus(1);
    messageInfo.setImmediateFlag(0);
    messageInfo.setRemarks("系统消息");
    messageInfo.setCreateBy("system");
    messageInfo.setCreateTime(new Date());
    messageMapper.insert(messageInfo);

  }

  private void sendMessage(String account, String channel, String message) {
    MessagePayload payload = MessagePayload.builder().channel(channel).title(message).build();
    log.info("提现成功=============>开始推送Socket消息,相关参数{}", payload);
    client.sendToUser(account, payload);
  }

  private int verifyMessage() {
    SysDictData sysDictData =
        sysDictDataService.getDictData(
            DictTypeEnum.SYSTEM_PARAMETER_CONFIG.getValue(),
            DictDataEnum.WITHDRAW_PUSH_MSG.getLabel());
    return ObjectUtil.isNull(sysDictData) ? 0 : Convert.toInt(sysDictData.getDictValue());
  }

  private String context(Integer state, Date date, BigDecimal money) {
    String context;
    String dateStr = DateUtil.dateToStr(date, DateUtil.YYYY_MM_DD_HH_MM_SS);
    if (ObjectUtil.equals(3, state)) {
      context = "您于" + dateStr + "提现的" + money.setScale(2, RoundingMode.CEILING) + "已提现成功。";
    } else {
      context = "您于" + dateStr + "提现的" + money.setScale(2, RoundingMode.CEILING) + "已失败。";
    }
    return context;
  }

  private String title(int state) {
    String title;
    if (ObjectUtil.equals(state, 3)) {
      title = "提现成功";
    } else {
      title = "提现失败";
    }

    return title;
  }

  private SummaryVO amountSum() {
    LambdaQueryWrapper<MemberWithdraw> queryHandle = Wrappers.lambdaQuery();
    SummaryVO summaryVO = new SummaryVO();
    queryHandle.eq(MemberWithdraw::getCashStatus, WithdrawStatus.HANDLED.getValue());
    summaryVO.setAllHandledSum(memberWithdrawMapper.summaryMemberWithdraw(queryHandle));

    LambdaQueryWrapper<MemberWithdraw> queryUnHandle = Wrappers.lambdaQuery();
    queryUnHandle.eq(MemberWithdraw::getCashStatus, WithdrawStatus.UNHANDLED.getValue());
    summaryVO.setAllUnhandledSum(memberWithdrawMapper.summaryMemberWithdraw(queryUnHandle));
    return summaryVO;
  }
}
