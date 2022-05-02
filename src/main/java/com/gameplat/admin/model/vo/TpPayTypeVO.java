package com.gameplat.admin.model.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class TpPayTypeVO implements Serializable {

  private Long id;

  private String payType;

  private String tpInterfaceCode;

  private String name;

  private String code;
}
