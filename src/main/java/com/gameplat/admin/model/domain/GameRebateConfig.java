package com.gameplat.admin.model.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.Data;

@Data
@TableName("game_rebate_config")
public class GameRebateConfig implements Serializable {

  @TableId(type = IdType.AUTO)
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