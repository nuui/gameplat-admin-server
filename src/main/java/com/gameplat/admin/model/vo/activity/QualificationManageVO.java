package com.gameplat.admin.model.vo.activity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * @Author: lyq
 * @Date: 2020/8/20 13:52
 * @Description:
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class QualificationManageVO implements Serializable {

    private static final long serialVersionUID = -1015094690715071247L;

    @JsonSerialize(using= ToStringSerializer.class)
    @ApiModelProperty(value = "资格id")
    private Long qualificationId;

    @ApiModelProperty(value = "活动名称")
    private String activityName;

    @ApiModelProperty(value = "活动类型（1 活动大厅，2 红包雨，3 转盘）")
    private Integer activityType;

    @JsonSerialize(using= ToStringSerializer.class)
    @ApiModelProperty(value = "活动ID")
    private Long activityId;

    @JsonSerialize(using= ToStringSerializer.class)
    @ApiModelProperty(value = "用户id")
    private Long userId;

    @ApiModelProperty(value = "会员账号")
    private String username;

    @ApiModelProperty(value = "申请时间")
    private Date applyTime;

    @ApiModelProperty(value = "审核人")
    private String auditPerson;

    @ApiModelProperty(value = "审核时间")
    private Date auditTime;

    @ApiModelProperty(value = "审核备注")
    private String auditRemark;

    @ApiModelProperty(value = "状态（0 无效，1 申请中，2 已审核）")
    private Integer status;

    @ApiModelProperty(value = "活动开始时间")
    private Date activityStartTime;

    @ApiModelProperty(value = "活动结束时间")
    private Date activityEndTime;

    @ApiModelProperty(value = "统计开始时间")
    private Date statisStartTime;

    @ApiModelProperty(value = "统计结束时间")
    private Date statisEndTime;

    @ApiModelProperty(value = "资格状态（1 已使用，2 全部）")
    private Integer qualificationStatus;

    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "创建人")
    private String createBy;

    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    @ApiModelProperty(value = "更新人")
    private String updateBy;

    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    @ApiModelProperty(value = "总抽奖次数")
    private Integer drawNum;

    @ApiModelProperty(value = "使用次数")
    private Integer employNum;

    @ApiModelProperty(value = "最小金额")
    private Integer minMoney;

    @ApiModelProperty(value = "最大金额")
    private Integer maxMoney;

    @ApiModelProperty(value = "删除（0 已删除，1 未删除）")
    private Integer deleteFlag;

    @ApiModelProperty(value = "使用时间")
    private Date employTime;

    @ApiModelProperty(value = "与活动派发关联id")
    private String qualificationActivityId;

    @ApiModelProperty(value = "唯一标识")
    private String soleIdentifier;

    @ApiModelProperty(value = "提现打码量")
    private Integer withdrawDml;

    @ApiModelProperty(value = "奖励详情")
    private String awardDetail;

    @ApiModelProperty(value = "领取方式（1 直接发放，2 福利中心）")
    private Integer getWay;

}