package com.gameplat.admin.model.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * ip分析
 * @author lily
 *
 */
@Data
public class IpAnalysisDTO {

	@ApiModelProperty(value = "用户名")
	private String username;

	@ApiModelProperty(value = "登录ip")
	private String loginIp;

	@ApiModelProperty(value = "开始时间")
	private String beginTime;

	@ApiModelProperty(value = "结束时间")
	private String endTime;

	@ApiModelProperty(value = "用户id")
	private Integer userId;

  @ApiModelProperty(value = "类型")
	private Integer type;

}
