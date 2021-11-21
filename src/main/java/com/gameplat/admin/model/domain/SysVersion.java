package com.gameplat.admin.model.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 系统版本记录表 sys_version
 * @author three
 */
@Data
@TableName("sys_version")
public class SysVersion {

  private Long id;
  private String code;
  private String versionDesc;
  private Integer status;
  private Date  createTime;
  private Date updateTime;
}