package com.gameplat.admin.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.gameplat.admin.enums.CashEnum;
import com.gameplat.admin.enums.ProxyPayStatusEnum;
import com.gameplat.admin.feign.PaymentCenterFeign;
import com.gameplat.admin.model.bean.AdminLimitInfo;
import com.gameplat.admin.model.bean.ProxyPayMerBean;
import com.gameplat.admin.model.bean.ReturnMessage;
import com.gameplat.admin.model.vo.MemberWithdrawBankVo;
import com.gameplat.admin.service.*;
import com.gameplat.admin.util.MoneyUtils;
import com.gameplat.base.common.exception.ServiceException;
import com.gameplat.base.common.json.JsonUtils;
import com.gameplat.base.common.util.StringUtils;
import com.gameplat.base.common.web.Result;
import com.gameplat.basepay.pay.bean.NameValuePair;
import com.gameplat.basepay.proxypay.thirdparty.ProxyCallbackContext;
import com.gameplat.basepay.proxypay.thirdparty.ProxyDispatchContext;
import com.gameplat.basepay.proxypay.thirdparty.ProxyPayBackResult;
import com.gameplat.common.constant.WithdrawTypeConstant;
import com.gameplat.common.enums.*;
import com.gameplat.common.model.bean.limit.MemberRechargeLimit;
import com.gameplat.common.model.bean.limit.MemberWithdrawLimit;
import com.gameplat.model.entity.member.Member;
import com.gameplat.model.entity.member.MemberInfo;
import com.gameplat.model.entity.member.MemberWithdraw;
import com.gameplat.model.entity.member.MemberWithdrawHistory;
import com.gameplat.model.entity.pay.PpInterface;
import com.gameplat.model.entity.pay.PpMerchant;
import com.gameplat.model.entity.sys.SysUser;
import com.gameplat.security.context.UserCredential;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Service
@Transactional(isolation = Isolation.DEFAULT, rollbackFor = Throwable.class)
public class ProxyPayServiceImpl implements ProxyPayService {

  @Autowired private MemberWithdrawService memberWithdrawService;

  @Autowired private MemberWithdrawHistoryService memberWithdrawHistoryService;

  @Autowired private MemberService memberService;

  @Autowired private SysUserService sysUserService;

  @Autowired private LimitInfoService limitInfoService;

  @Autowired private PpInterfaceService ppInterfaceService;

  @Autowired private PpMerchantService ppMerchantService;

  @Autowired private MemberInfoService memberInfoService;

  @Autowired private PaymentCenterFeign paymentCenterFeign;

  @Autowired private ValidWithdrawService validWithdrawService;

  @Autowired private MemberRwReportService memberRwReportService;

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
    List<MemberWithdrawBankVo> bankVoList =
            JSONUtil.toList(
                    (JSONArray) JSONUtil.parseObj(ppInterface.getLimtInfo()).get("banks"),
                    MemberWithdrawBankVo.class);
    // 模糊匹配银行名称
    /** 模糊匹配银行名称 */
    boolean isBankName = true;
    if (!WithdrawTypeConstant.BANK.equals(memberWithdraw.getWithdrawType())) {
      isBankName = false;
    }
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

  @Override
  public void proxyPay(
      Long id,
      Long ppMerchantId,
      String asyncCallbackUrl,
      String sysPath,
      UserCredential userCredential)
      throws Exception {
    MemberWithdraw memberWithdraw = memberWithdrawService.getById(id);
    log.info("三方代付出款订单号==={}", memberWithdraw.getCashOrderNo());
    if (null == memberWithdraw) {
      throw new ServiceException("不存在的记录");
    }

    Member member = memberService.getById(memberWithdraw.getMemberId());
    if (member == null) {
      throw new ServiceException("用户不存在");
    }

    MemberInfo memberInfo = memberInfoService.getById(memberWithdraw.getMemberId());
    if (memberInfo == null) {
      throw new ServiceException("用户扩展信息不存在");
    }

    // 校验已受理出款是否允许其他人操作
    crossAccountCheck(userCredential, memberWithdraw);

    // 校验子账号当天取款审核额度
    SysUser sysUser = sysUserService.getByUsername(userCredential.getUsername());
    AdminLimitInfo adminLimitInfo = JsonUtils.parse(sysUser.getLimitInfo(), AdminLimitInfo.class);
    if (null != adminLimitInfo
        && StringUtils.equals(UserTypes.SUBUSER.value(), sysUser.getUserType())) {
      checkZzhWithdrawAmountAudit(
          adminLimitInfo,
          memberWithdraw.getCashMode(),
          memberWithdraw.getCashMoney(),
          userCredential.getUsername());
    }

    // 第三方代付商户
    PpMerchant ppMerchant = ppMerchantService.getById(ppMerchantId);
    if (!Optional.ofNullable(ppMerchant).isPresent()) {
      throw new ServiceException("请确认出款商户是否存在！");
    }

    // 代付接口信息
    PpInterface ppInterface = ppInterfaceService.get(ppMerchant.getPpInterfaceCode());
    verifyPpInterface(ppInterface);
    if (verifyPpMerchant(memberWithdraw, ppMerchant, ppInterface)) {
      throw new ServiceException("出款商户限制，请选择其他方式出款！");
    }

    // 封装第三方代付接口调用信息
    ProxyDispatchContext context = new ProxyDispatchContext();
    MemberWithdrawLimit withdrawLimit = this.limitInfoService.get(LimitEnums.MEMBER_WITHDRAW_LIMIT);
    String callbackDomain = withdrawLimit.getCallbackDomain();
    String domain = StringUtils.isNotEmpty(callbackDomain) ? callbackDomain : asyncCallbackUrl;
    if(!domain.endsWith("/")){
      domain += "/";
    }
    String asyncUrl =
            domain + "api/admin/callback/proxyPayAsyncCallback";
    String syncUrl =
            domain + "api/admin/callback/fixedProxyPayAsyncCallback";
    context.setAsyncCallbackUrl(asyncUrl + "/" + memberWithdraw.getCashOrderNo());
    // 固定回调地址
    context.setSyncCallbackUrl(syncUrl + "/" + ppMerchant.getPpInterfaceCode());
    context.setSysPath(sysPath);
    // 设置第三方接口信息
    fillProxyDispatchContext(context, ppInterface, memberWithdraw);
    // 设置银行编码,名称
    context.setBankCode(getProxyPayBankCode(memberWithdraw, ppMerchant, ppInterface));
    context.setBankName(memberWithdraw.getBankName());
    // 设置设置虚拟币汇率、数量
    context.setCurrencyRate(memberWithdraw.getCurrencyRate());
    context.setCurrencyCount(memberWithdraw.getCurrencyCount());
    // 设置第三方商户参数
    context.setMerchantParameters(JSONObject.parseObject(ppMerchant.getParameters(), Map.class));

    // 设置客户请求地址
    context.setUserIpAddress(userCredential.getLoginIp());
    // 设置第三方商户出款数据
    buildWithdrawWithProxyMsg(memberWithdraw, ppMerchant, ppInterface);
    /** 第三方订单提交成功，将订单状态改为已出款 */
    memberWithdraw.setApproveReason(ppMerchant.getName() + "代付出款");
    memberWithdraw.setOperatorTime(new Date());
    memberWithdraw.setOperatorAccount(userCredential.getUsername());
    updateProxyWithdrawStatus(
        memberWithdraw, WithdrawStatus.SUCCESS.getValue(), member, memberInfo);

    /** 请求第三方代付接口 */
    log.info("进入第三方出入款中心出款订单号: {}", memberWithdraw.getCashOrderNo());
    String resultStr =
        paymentCenterFeign.onlineProxyPay(
            context, memberWithdraw.getPpInterface(), memberWithdraw.getPpInterfaceName());
    log.info("代付请求中心响应{}", resultStr);
    Result<ProxyPayBackResult> result = JSONUtil.toBean(resultStr, Result.class);
    if (!Objects.equals(ProxyPayStatusEnum.PAY_SUCCESS.getName(), result.getData())) {
      throw new ServiceException("请求代付失败！！！请立即联系第三方核实再出款！！！");
    }
    /*if (!result.isSucceed() || 0 != result.getCode()) {
      throw new ServiceException("请求代付返回结果提示:" + result.getMessage() + "！！！请立即联系第三方核实再出款！！！");
    }
    ProxyPayBackResult proxyPayBackResult = result.getData();
    *//** 设置虚拟货币真实出款汇率、数量 *//*
    if (null != proxyPayBackResult && null != proxyPayBackResult.getApproveCurrencyRate()
        || null != proxyPayBackResult.getApproveCurrencyCount()) {
      memberWithdrawService
          .lambdaUpdate()
          .set(MemberWithdraw::getApproveCurrencyRate, proxyPayBackResult.getApproveCurrencyRate())
          .set(
              MemberWithdraw::getApproveCurrencyCount, proxyPayBackResult.getApproveCurrencyCount())
          .eq(MemberWithdraw::getId, memberWithdraw.getId())
          .update();
    }*/
    memberWithdrawService.updateById(memberWithdraw);

    // 移除会员提现冻结金额
    memberInfoService.updateFreeze(memberInfo.getMemberId(), memberWithdraw.getCashMoney().negate());
    // 计算会员出款次数和金额
    memberInfoService.updateUserWithTimes(
            memberInfo.getMemberId(), memberWithdraw.getCashMoney().negate(), memberWithdraw.getPointFlag());

    /** 不需要异步通知，直接将状态改成第三方出款完成 */
    if (0 != ppInterface.getAsynNotifyStatus()) {
      updateStatus(
          memberWithdraw,
          WithdrawStatus.SUCCESS.getValue(),
          ProxyPayStatusEnum.PAY_SUCCESS.getCode());
      /** 更新代付商户出款次数和金额 */
      updatePpMerchant(ppMerchant, memberWithdraw.getCashMoney());
      log.info("第三方出款成功,出款商户为:{} ,出款订单信息为：{}", memberWithdraw.getPpMerchantName(), memberWithdraw);
    }
  }

  @Override
  public ReturnMessage queryProxyOrder(Long id, Long ppMerchantId) throws Exception {
    MemberWithdraw memberWithdraw = memberWithdrawService.getById(id);
    if (memberWithdraw == null) {
      throw new ServiceException("不存在的记录");
    }
    // 第三方代付商户
    PpMerchant ppMerchant = ppMerchantService.getById(ppMerchantId);
    // 代付接口信息
    PpInterface ppInterface = ppInterfaceService.get(ppMerchant.getPpInterfaceCode());
    verifyPpInterface(ppInterface);
    // 封装第三方代付接口调用信息
    ProxyDispatchContext context = new ProxyDispatchContext();
    context.setName(ppInterface.getName());
    context.setVersion(ppInterface.getOrderQueryVersion());
    context.setCharset(ppInterface.getCharset());
    context.setDispatchType(ppInterface.getDispatchType());
    context.setDispatchUrl(ppInterface.getOrderQueryUrl());
    context.setDispatchMethod(ppInterface.getOrderQueryMethod());
    fillProxyDispatchContext(context, ppInterface, memberWithdraw);
    // 设置第三方商户参数
    context.setMerchantParameters(JSONObject.parseObject(ppMerchant.getParameters(), Map.class));
    // 设置银行编码,名称
    context.setBankCode(getProxyPayBankCode(memberWithdraw, ppMerchant, ppInterface));
    context.setBankName(memberWithdraw.getBankName());
    /** 请求第三方代付接口 */
    log.info("进入第三方出入款中心查询订单号: {}", memberWithdraw.getCashOrderNo());
    String resultStr =
        paymentCenterFeign.onlineQueryProxyPay(
            context, ppInterface.getCode(), ppInterface.getName());
    Result<ReturnMessage> result = JSONUtil.toBean(resultStr, Result.class);
    if (!result.isSucceed() || 0 != result.getCode()) {
      throw new ServiceException("查询第三方代付异常:" + result.getMessage() + "！！！请立即联系第三方核实再出款！！！");
    }
    return result.getData();
  }

  @Override
  public String proxyPayAsyncCallback(
      String orderNo,
      String url,
      String method,
      List<NameValuePair> headers,
      String ipAddress,
      Map<String, String> callbackParameters,
      String requestBody)
      throws Exception {
    log.info("出款回调订单号={}", orderNo);
    MemberWithdraw memberWithdraw =
        memberWithdrawService.lambdaQuery().eq(MemberWithdraw::getCashOrderNo, orderNo).one();
    /** 校验体现订单信息 */
    if (memberWithdraw == null) {
      throw new ServiceException("充值订单不存在或订单已处理");
    }
    String beanName = getProxyInterfaceCode(memberWithdraw);
    ProxyCallbackContext proxyCallbackContext = getProxyCallbackContent(memberWithdraw);
    proxyCallbackContext.setUrl(url);
    proxyCallbackContext.setMethod(method);
    proxyCallbackContext.setHeaders(headers);
    proxyCallbackContext.setIp(ipAddress);
    proxyCallbackContext.setCallbackParameters(callbackParameters);
    proxyCallbackContext.setRequestBody(requestBody);
    String resultStr =
        paymentCenterFeign.asyncCallbackProxyPay(
            proxyCallbackContext, beanName, memberWithdraw.getPpInterfaceName());
    log.info("代付查询请求中心响应{}", resultStr);
    Result result = JSONUtil.toBean(resultStr, Result.class);
    ProxyPayBackResult proxyPayBackResult = JSONObject.parseObject(JSONObject.toJSONString(result.getData()), ProxyPayBackResult.class);
    if (!result.isSucceed() || 0 != result.getCode()) {
      throw new ServiceException("第三方代付异步回调异常:" + proxyPayBackResult.getMessage() + "！！！请立即联系第三方核实再出款！！！");
    }
    if (memberWithdraw.getCashStatus() == WithdrawStatus.CANCELLED.getValue()
        || memberWithdraw.getCashStatus() == WithdrawStatus.REFUSE.getValue()
        || memberWithdraw.getCashStatus() == WithdrawStatus.SUCCESS.getValue()
        || null == memberWithdraw.getProxyPayStatus()
        || memberWithdraw.getProxyPayStatus() == ProxyPayStatusEnum.PAY_SUCCESS.getCode()) {
      log.info(
          "第三方出款订单"
              + memberWithdraw.getCashOrderNo()
              + "已经被处理了,响应第三方需要的信息:"
              + proxyPayBackResult.getResponseMsg());
      return proxyPayBackResult.getResponseMsg();
    }

    Member info = memberService.getById(memberWithdraw.getMemberId());
    if (info == null) {
      throw new ServiceException("用户不存在");
    }

    int orignCashStatus = memberWithdraw.getCashStatus();
    if (!proxyPayBackResult.isSuccess()) {
      /** 第三方出款失败，将代付状态改变 */
      memberWithdraw.setApproveReason("第三方出款失败");
      memberWithdraw.setProxyPayDesc("第三方出款失败");
      updateStatus(memberWithdraw, orignCashStatus, ProxyPayStatusEnum.PAY_FAIL.getCode());
      log.info("第三方出款订单 ：{} ！出款失败信息： {}", memberWithdraw.getCashOrderNo(), result.getMessage());
      return proxyPayBackResult.getResponseMsg();
    }

    /** 第三方出款成功 */
    memberWithdraw.setApproveReason("第三方出款成功");
    memberWithdraw.setProxyPayDesc("第三方出款成功");
    memberWithdraw.setProxyPayStatus(ProxyPayStatusEnum.PAY_SUCCESS.getCode());

    if (WithdrawStatus.SUCCESS.getValue() != orignCashStatus) {
      memberWithdraw.setCashStatus(WithdrawStatus.SUCCESS.getValue());
    }
    updateStatus(memberWithdraw, orignCashStatus, ProxyPayStatusEnum.PAY_SUCCESS.getCode());
    /** 更新代付商户出款次数和金额 */
    PpMerchant ppMerchant = ppMerchantService.getById(memberWithdraw.getPpMerchantId());
    updatePpMerchant(ppMerchant, memberWithdraw.getCashMoney());
    log.info("第三方出款成功,出款商户为:{} ,出款订单信息为：{}", memberWithdraw.getPpMerchantName(), memberWithdraw);
    return proxyPayBackResult.getResponseMsg();
  }

  /**
   * 在线固定地址代付异步回调处理
   * 注意使用syncCallbackUrl字段作为回调地址
   * 回调传递ppOrderNo为我们系统订单号
   * */
  @Override
  public String fixedProxyPayAsyncCallback(
      String interfaceCode,
      String url,
      String method,
      List<NameValuePair> headers,
      String ipAddress,
      Map<String, String> callbackParameters,
      String requestBody)
      throws Exception {
    log.info("出款固定地址回调商户==={}", interfaceCode);
    PpInterface ppInterface = ppInterfaceService.get(interfaceCode);
    if (ppInterface == null) {
      throw new Exception("第三方代付接口已关闭");
    }
    List<PpMerchant> list =
        ppMerchantService.lambdaQuery().eq(PpMerchant::getPpInterfaceCode, interfaceCode).list();
    if (CollectionUtils.isEmpty(list)) {
      throw new Exception("第三方代付接口已关闭");
    }
    PpMerchant ppMerchant = list.get(0);
    ProxyCallbackContext proxyCallbackContext = new ProxyCallbackContext();
    proxyCallbackContext.setUrl(url);
    proxyCallbackContext.setMethod(method);
    proxyCallbackContext.setHeaders(headers);
    proxyCallbackContext.setIp(ipAddress);
    proxyCallbackContext.setCallbackParameters(callbackParameters);
    proxyCallbackContext.setRequestBody(requestBody);
    proxyCallbackContext.setMerchantParameters(JSONObject.parseObject(ppMerchant.getParameters(), Map.class));
    String resultStr =
        paymentCenterFeign.asyncCallbackProxyPay(
            proxyCallbackContext, interfaceCode, ppMerchant.getName());
    log.info("代付查询请求中心响应{}", resultStr);
    Result result = JSONUtil.toBean(resultStr, Result.class);
    ProxyPayBackResult proxyPayBackResult = JSONObject.parseObject(JSONObject.toJSONString(result.getData()), ProxyPayBackResult.class);
    if (!result.isSucceed() || 0 != result.getCode()) {
      throw new ServiceException(
          "第三方代付异步回调异常:" + proxyPayBackResult.getMessage() + "！！！请立即联系第三方核实再出款！！！");
    }
    String ppOrderNo = proxyPayBackResult.getPpOrderNo();
    if (StringUtils.isEmpty(ppOrderNo)) {
      throw new ServiceException("充值订单不存在或订单已处理");
    }
    log.info("代付回调订单号==={}", ppOrderNo);
    MemberWithdraw memberWithdraw =
        memberWithdrawService.lambdaQuery().eq(MemberWithdraw::getCashOrderNo, ppOrderNo).one();
    /** 校验体现订单信息 */
    if (memberWithdraw == null) {
      throw new ServiceException("充值订单不存在或订单已处理");
    }
    /*String beanName = getProxyInterfaceCode(memberWithdraw);
    proxyCallbackContext = getProxyCallbackContent(memberWithdraw);*/
    if (memberWithdraw.getCashStatus() == WithdrawStatus.CANCELLED.getValue()
        || memberWithdraw.getCashStatus() == WithdrawStatus.REFUSE.getValue()
        || memberWithdraw.getCashStatus() == WithdrawStatus.SUCCESS.getValue()
        || null == memberWithdraw.getProxyPayStatus()
        || memberWithdraw.getProxyPayStatus() == ProxyPayStatusEnum.PAY_SUCCESS.getCode()) {
      log.info(
          "第三方出款订单"
              + memberWithdraw.getCashOrderNo()
              + "已经被处理了,响应第三方需要的信息:"
              + proxyPayBackResult.getResponseMsg());
      return proxyPayBackResult.getResponseMsg();
    }

    Member info = memberService.getById(memberWithdraw.getMemberId());
    if (info == null) {
      throw new ServiceException("用户不存在");
    }

    int orignCashStatus = memberWithdraw.getCashStatus();
    if (!proxyPayBackResult.isSuccess()) {
      /** 第三方出款失败，将代付状态改变 */
      memberWithdraw.setApproveReason("第三方出款失败");
      memberWithdraw.setProxyPayDesc("第三方出款失败");
      updateStatus(memberWithdraw, orignCashStatus, ProxyPayStatusEnum.PAY_FAIL.getCode());
      log.info("第三方出款订单 ：{} ！出款失败信息： {}", memberWithdraw.getCashOrderNo(), result.getMessage());
      return proxyPayBackResult.getResponseMsg();
    }

    /** 第三方出款成功 */
    memberWithdraw.setApproveReason("第三方出款成功");
    memberWithdraw.setProxyPayDesc("第三方出款成功");
    memberWithdraw.setProxyPayStatus(ProxyPayStatusEnum.PAY_SUCCESS.getCode());

    if (WithdrawStatus.SUCCESS.getValue() != orignCashStatus) {
      memberWithdraw.setCashStatus(WithdrawStatus.SUCCESS.getValue());
    }
    updateStatus(memberWithdraw, orignCashStatus, ProxyPayStatusEnum.PAY_SUCCESS.getCode());
    /** 更新代付商户出款次数和金额 */
    ppMerchant = ppMerchantService.getById(memberWithdraw.getPpMerchantId());
    updatePpMerchant(ppMerchant, memberWithdraw.getCashMoney());
    log.info("第三方出款成功,出款商户为:{} ,出款订单信息为：{}", memberWithdraw.getPpMerchantName(), memberWithdraw);
    return proxyPayBackResult.getResponseMsg();
  }

  /** 开启出入款订单是否允许其他账户操作配置 校验非超管账号是否原受理人 */
  private void crossAccountCheck(UserCredential userCredential, MemberWithdraw memberWithdraw)
      throws ServiceException {
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

  private void verifyPpInterface(PpInterface ppInterface) {
    if (ppInterface == null) {
      throw new ServiceException("第三方代付接口已关闭。");
    }
    if (ppInterface.getStatus() != SwitchStatusEnum.ENABLED.getValue()) {
      throw new ServiceException("第三方代付接口已关闭。");
    }
  }

  /**
   * 检验子账号出款受理额度
   *
   * @param adminLimitInfo AdminLimitInfo
   * @param cashMode Integer
   */
  public void checkZzhWithdrawAmountAudit(
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

  private void fillProxyDispatchContext(
      ProxyDispatchContext context, PpInterface ppInterface, MemberWithdraw memberWithdraw) {
    context.setName(ppInterface.getName());
    context.setVersion(ppInterface.getVersion());
    context.setCharset(ppInterface.getCharset());
    context.setDispatchType(ppInterface.getDispatchType());
    context.setDispatchUrl(ppInterface.getDispatchUrl());
    context.setDispatchMethod(ppInterface.getDispatchMethod());
    context.setBankAccountNo(memberWithdraw.getBankCard());
    context.setBankCity(memberWithdraw.getBankAddress());
    context.setProxyAmount(memberWithdraw.getApproveMoney());
    context.setProxyOrderNo(memberWithdraw.getCashOrderNo());
    context.setOrderTime(memberWithdraw.getCreateTime());
    context.setUserAccount(memberWithdraw.getAccount());
    if(StringUtils.isNotEmpty(memberWithdraw.getRealName())){
      context.setUserRealName(memberWithdraw.getRealName());
    }
  }

  private String getProxyPayBankCode(
      MemberWithdraw memberWithdraw, PpMerchant ppMerchant, PpInterface ppinterface) {
    if (null == ppMerchant || !SwitchStatusEnum.ENABLED.match(ppMerchant.getStatus())) {
      throw new ServiceException("第三方代付商户已关闭");
    }

    String bankCode = "";
    List<MemberWithdrawBankVo> bankVoList =
            JSONUtil.toList(
                    (JSONArray) JSONUtil.parseObj(ppinterface.getLimtInfo()).get("banks"),
                    MemberWithdrawBankVo.class);
    for (MemberWithdrawBankVo ex : bankVoList) {
      if (StringUtils.contains(ex.getName(), memberWithdraw.getBankName())
              || StringUtils.contains(memberWithdraw.getBankName(), ex.getName())) {
        bankCode = ex.getCode();
        break;
      }
      if (StringUtils.contains(ex.getName(), "邮政")
              && StringUtils.contains(memberWithdraw.getBankName(), "邮政")) {
        bankCode = ex.getCode();
        break;
      }
      if (StringUtils.contains(ex.getName().toLowerCase(), memberWithdraw.getWithdrawType().toLowerCase())
              || StringUtils.contains(memberWithdraw.getWithdrawType().toLowerCase(), ex.getName().toLowerCase())) {
        bankCode = ex.getCode();
        break;
      }
    }

    if (StringUtils.isBlank(bankCode)) {
      throw new ServiceException("银行编码错误，请检查银行配置信息！");
    }
    return bankCode;
  }

  private void buildWithdrawWithProxyMsg(
      MemberWithdraw memberWithdraw, PpMerchant ppMerchant, PpInterface ppInterface) {
    memberWithdraw.setProxyPayDesc("第三方出款中。。。");
    memberWithdraw.setProxyPayStatus(ProxyPayStatusEnum.PAY_PROGRESS.getCode());
    memberWithdraw.setPpInterface(ppInterface.getCode());
    memberWithdraw.setPpInterfaceName(ppInterface.getName());
    memberWithdraw.setPpMerchantId(ppMerchant.getId());
    memberWithdraw.setPpMerchantName(ppMerchant.getName());
  }

  /**
   * 第三方出款成功，改变出款状态
   *
   * @param memberWithdraw
   */
  public void updateProxyWithdrawStatus(
      MemberWithdraw memberWithdraw, Integer cashStatus, Member member, MemberInfo memberInfo)
      throws Exception {
    // 修改订单状态
    LambdaUpdateWrapper<MemberWithdraw> updateMemberWithdraw = Wrappers.lambdaUpdate();
    updateMemberWithdraw
        .set(MemberWithdraw::getCashStatus, cashStatus)
        .set(MemberWithdraw::getApproveReason, memberWithdraw.getApproveReason())
        .set(MemberWithdraw::getOperatorAccount, memberWithdraw.getOperatorAccount())
        .set(MemberWithdraw::getOperatorTime, memberWithdraw.getOperatorTime())
        .set(MemberWithdraw::getProxyPayDesc, memberWithdraw.getProxyPayDesc())
        .set(MemberWithdraw::getProxyPayStatus, memberWithdraw.getProxyPayStatus())
        .set(MemberWithdraw::getPpInterface, memberWithdraw.getPpInterface())
        .set(MemberWithdraw::getPpInterfaceName, memberWithdraw.getPpInterfaceName())
        .set(MemberWithdraw::getPpMerchantId, memberWithdraw.getPpMerchantId())
        .set(MemberWithdraw::getPpMerchantName, memberWithdraw.getPpMerchantName())
        .eq(MemberWithdraw::getId, memberWithdraw.getId())
        .eq(MemberWithdraw::getCashStatus, memberWithdraw.getCashStatus());
    if (!memberWithdrawService.update(updateMemberWithdraw)) {
      log.error(
          "修改提现订单异常：UserWithdraw="
              + memberWithdraw.toString()
              + ",origCashStatus="
              + memberWithdraw.getCashStatus());
      throw new ServiceException("订单已处理");
    }

    if (!UserTypes.PROMOTION.value().equals(member.getUserType())) {
      memberRwReportService.addWithdraw(member, memberInfo.getTotalWithdrawTimes(), memberWithdraw);
    }

    // 删除出款验证打码量记录的数据
    validWithdrawService.remove(memberWithdraw.getMemberId(), memberWithdraw.getCreateTime());

    // 添加取现历史记录
    memberWithdraw.setCashStatus(cashStatus);
    MemberWithdrawHistory memberWithdrawHistory = new MemberWithdrawHistory();
    BeanUtils.copyProperties(memberWithdraw, memberWithdrawHistory);
    memberWithdrawHistoryService.save(memberWithdrawHistory);
  }

  private void updateStatus(
      MemberWithdraw memberWithdraw, Integer withdrawStatus, Integer proxyPayStatus) {
    boolean updateStatus =
        memberWithdrawService
            .lambdaUpdate()
            .set(
                ObjectUtils.isNotNull(memberWithdraw.getCashStatus()),
                MemberWithdraw::getCashStatus,
                memberWithdraw.getCashStatus())
            .set(MemberWithdraw::getApproveReason, memberWithdraw.getApproveReason())
            .set(MemberWithdraw::getProxyPayDesc, memberWithdraw.getProxyPayDesc())
            .set(MemberWithdraw::getProxyPayStatus, proxyPayStatus)
            .eq(MemberWithdraw::getId, memberWithdraw.getId())
            .eq(MemberWithdraw::getCashStatus, withdrawStatus)
            .update();
    if (!updateStatus) {
      log.error(
          "修改提现订单异常：MemberWithdraw="
              + memberWithdraw.toString()
              + ",origCashStatus="
              + withdrawStatus);
      throw new ServiceException("订单已处理");
    }
    /** 历史提现订单记录 */
    boolean updateHistoryStatus =
        memberWithdrawHistoryService
            .lambdaUpdate()
            .set(
                ObjectUtils.isNotNull(memberWithdraw.getCashStatus()),
                MemberWithdrawHistory::getCashStatus,
                memberWithdraw.getCashStatus())
            .set(MemberWithdrawHistory::getApproveReason, memberWithdraw.getApproveReason())
            .set(MemberWithdrawHistory::getProxyPayDesc, memberWithdraw.getProxyPayDesc())
            .set(MemberWithdrawHistory::getProxyPayStatus, proxyPayStatus)
            .eq(MemberWithdrawHistory::getId, memberWithdraw.getId())
            .eq(MemberWithdrawHistory::getCashStatus, withdrawStatus)
            .update();
    if (!updateHistoryStatus) {
      log.error(
          "修改提现订单异常：UserWithdraw="
              + memberWithdraw.toString()
              + ",origCashStatus="
              + withdrawStatus);
      throw new ServiceException("历史订单不存在");
    }
  }

  private void updatePpMerchant(PpMerchant ppMerchant, BigDecimal cashMomey) {
    boolean updateMerchant =
        ppMerchantService
            .lambdaUpdate()
            .set(PpMerchant::getProxyTimes, ppMerchant.getProxyTimes() + 1)
            .set(PpMerchant::getProxyAmount, ppMerchant.getProxyAmount().add(cashMomey))
            .eq(PpMerchant::getId, ppMerchant.getId())
            .update();
    if (!updateMerchant) {
      log.error("修改代付商户出款次数和金额异常：ppMerchant=" + ppMerchant.toString());
      throw new ServiceException("修改代付商户出款次数和金额异常");
    }
  }

  private String getProxyInterfaceCode(MemberWithdraw memberWithdraw) throws Exception {
    PpInterface ppInterface = ppInterfaceService.get(memberWithdraw.getPpInterface());
    if (ppInterface == null || StringUtils.isEmpty(ppInterface.getCode())) {
      throw new Exception("第三方代付接口已关闭");
    }
    return ppInterface.getCode();
  }

  private ProxyCallbackContext getProxyCallbackContent(MemberWithdraw memberWithdraw)
      throws Exception {

    PpInterface ppInterface = ppInterfaceService.get(memberWithdraw.getPpInterface());
    if (ppInterface == null) {
      throw new Exception("第三方代付接口已关闭");
    }
    PpMerchant ppMerchant = ppMerchantService.getById(memberWithdraw.getPpMerchantId());
    if (ppMerchant == null) {
      throw new Exception("第三方代付商户已关闭");
    }

    ProxyCallbackContext context = new ProxyCallbackContext();
    context.setName(ppInterface.getName());
    context.setCashOrderNo(memberWithdraw.getCashOrderNo());
    context.setCharset(ppInterface.getCharset());
    context.setVersion(ppInterface.getVersion());
    context.setProxyAmount(memberWithdraw.getCashMoney());
    context.setMerchantParameters(JSONObject.parseObject(ppMerchant.getParameters(), Map.class));
    return context;
  }
}
