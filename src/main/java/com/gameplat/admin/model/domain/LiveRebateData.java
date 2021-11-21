package com.gameplat.admin.model.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import lombok.Data;

/**
 * 真人返水详情
 */
@Data
@TableName("live_rebate_data")
public class LiveRebateData implements Serializable {

  /**
   * 主键
   */
  @TableId(type = IdType.AUTO)
  private Long id;

  /**
   * 账号编号
   */
  private Long memberId;

  /**
   * 账号
   */
  private String account;

  /**
   * 用户真实姓名
   */
  private String realName;

  /**
   * 代理路径
   */
  private String userPaths;

  /**
   * 真人游戏
   */
  private String gameCode;

  /**
   * 游戏子类型
   */
  private String gameKind;

  private String statTime;

  /**
   * 添加时间
   */
  @TableField(fill = FieldFill.INSERT)
  @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private Date createTime;

  private BigDecimal betAmount;

  private BigDecimal validAmount;

  private BigDecimal winAmount;

  private Integer betCount;

  private Integer winCount;

  private String firstKind;

}