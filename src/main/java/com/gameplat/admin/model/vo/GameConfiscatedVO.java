package com.gameplat.admin.model.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class GameConfiscatedVO implements Serializable {
  private String platformCode;

  /** 0 : 成功 1 : 失败 */
  private Integer status;

  private String platformName;

  private BigDecimal balance;

  private String errorMsg;
}