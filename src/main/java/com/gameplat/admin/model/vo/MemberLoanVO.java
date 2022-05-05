package com.gameplat.admin.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @author lily
 * @description 借呗
 * @date 2022/3/6
 */
@Data
public class MemberLoanVO implements Serializable {

  @ApiModelProperty("主键")
  private Long id;

  @ApiModelProperty("会员Id")
  private Long memberId;

  @ApiModelProperty("会员账号")
  private String account;

  @ApiModelProperty("VIP等级")
  private Integer vipLevel;

  @ApiModelProperty("账户余额")
  private BigDecimal memberBalance;

  @ApiModelProperty("借呗额度")
  private BigDecimal loanMoney;

  @ApiModelProperty("借款状态  0:未结清  1:已结清  2:已回收")
  private Integer loanStatus;

  @ApiModelProperty("欠款金额")
  private BigDecimal overdraftMoney;

  @ApiModelProperty("借款时间")
  private Date loanTime;

  @ApiModelProperty("欠款金额小计")
  private BigDecimal subtotal;

  @ApiModelProperty("欠款金额总计")
  private BigDecimal total;
}
