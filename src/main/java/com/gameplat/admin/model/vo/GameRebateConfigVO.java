package com.gameplat.admin.model.vo;

import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class GameRebateConfigVO implements Serializable {

  private Long id;
  /**
   * 用户层级
   */
  private String userLevel;

  /**
   * 投注额阈值
   */
  private BigDecimal money;

  /**
   * 返点具体配置
   */
  private String json;

  private String expand;
}