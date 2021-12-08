package com.gameplat.admin.model.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 活动类型新增DTO
 *
 * @author kenvin
 */
@Data
@EqualsAndHashCode(callSuper = false)
@ApiModel(value = "ActivityType", description = "活动类型新增DTO")
public class ActivityTypeAddDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "活动类型")
    private String typeCode;

    @ApiModelProperty(value = "活动类型名称")
    private String typeName;

    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "排序")
    private Integer sort;

    @ApiModelProperty(value = "状态,0 无效,1 有效")
    private Integer typeStatus;

    @ApiModelProperty(value = "浮窗状态,0 无效,1 有效")
    private Integer floatStatus;

    @ApiModelProperty(value = "浮窗logo")
    private String floatLogo;

    @ApiModelProperty(value = "浮窗url")
    private String floatUrl;

    @ApiModelProperty(value = "语言")
    private String language;

}