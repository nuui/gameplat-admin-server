package com.gameplat.admin.model.vo;

import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class SysSmsAreaVO implements Serializable {

  private Long id;
  /**
   * 编码
   */
  private String code;
  /**
   * 国家/地区
   */
  private String name;

  /**
   * 状态 0 禁用 1 启用
   */
  private String status;

}
