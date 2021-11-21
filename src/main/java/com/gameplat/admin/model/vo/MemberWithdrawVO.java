package com.gameplat.admin.model.vo;

import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

@Data
public class MemberWithdrawVO {

  public Long id;

  private String cashOrderNo;

  private String account;

  private String realName;

  private BigDecimal accountMoney;

  private BigDecimal cashMoney;

  private String bankName;

  private String bankCard;

  private String bankAddress;

  private Integer cashStatus;

  private BigDecimal approveMoney;

  private String memberLevel;

  private String superName;

  private String ipAddress;

  private Integer proxyPayStatus;

  private String memberMemo;

  private String withdrawType;

  private BigDecimal currencyRate;

  private String currencyCount;

  private String approveCurrencyCount;

  private Date createTime;

  private BigDecimal counterFee;

  private String cashReason;

  private Long memberId;

}