package com.gameplat.admin.model.dto;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * @Author: kenvin
 * @Description:
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ActivityLobbyAddDTO implements Serializable {

    private static final long serialVersionUID = 6060013282905693277L;

    @ApiModelProperty(value = "标题")
    private String title;

    @ApiModelProperty(value = "开始时间")
    private Date startTime;

    @ApiModelProperty(value = "结束时间")
    private Date endTime;

    @ApiModelProperty(value = "活动大类（1 活动大厅，2 红包雨，3 转盘）")
    private Integer type;

    @ApiModelProperty(value = "活动类型（1 充值活动，2 游戏活动")
    private Integer activityType;

    @ApiModelProperty(value = "描述")
    private String describe;

    @ApiModelProperty(value = "统计项目（1 累计充值金额，2 累计充值天数，3 连续充值天数，4 单日首充金额，5 首充金额，6 累计彩票打码金额，7 累计彩票打码天数，" +
            "8 连续彩票打码天数，9 单日彩票亏损金额，10 累计体育打码金额，11累计体育打码天数，11 连续体育打码天数，12 单日体育亏损金额）")
    private Integer statisItem;

    @ApiModelProperty(value = "充值类型（1 转账汇款，2 在线支付，3 人工入款）")
    private String payType;

    @ApiModelProperty(value = "统计日期（1 每日，2 每周，3 每月，4 每周X，5 每月X日）")
    private Integer statisDate;

    @ApiModelProperty(value = "详细日期（日）")
    private Integer detailDate;

    @ApiModelProperty(value = "参与层级")
    private String graded;

    @ApiModelProperty(value = "申请方式（1 手动，2 自动）")
    private Integer applyWay;

    @ApiModelProperty(value = "审核方式（1 手动，2 自动）")
    private Integer auditWay;

    @ApiModelProperty(value = "隔天申请（0 否，1 是）（在有效统计时间的次日申请活动彩金)")
    private Integer nextDayApply;

    @ApiModelProperty(value = "多重彩金（0 否，1 是）（可领取所有满足等级要求的活动彩金）")
    private Integer multipleHandsel;

    @ApiModelProperty(value = "账号黑名单")
    private String accountBlacklist;

    @ApiModelProperty(value = "ip黑名单")
    private String ipBlacklist;

    @ApiModelProperty(value = "申请路径")
    private String applyUrl;

    @ApiModelProperty(value = "活动大厅优惠")
    private List<ActivityLobbyDiscountDTO> lobbyDiscount;

    @ApiModelProperty(value = "删除活动大厅优惠id")
    private List<Long> delDiscountIdList;

    @ApiModelProperty(value = "失效活動")
    private Integer failure;

    @ApiModelProperty(value = "领取方式（1 红包，2 通知）")
    private Integer getWay;

    @ApiModelProperty(value = "用户等级")
    private String userLevel;

    @ApiModelProperty(value = "活动状态（0 关闭，1 开启，2 失效）")
    private Integer status;

    @ApiModelProperty(value = "游戏类型（1体育 2真人 3棋牌 4彩票 5电子 6电竞 7动物竞技 8捕鱼）")
    private Integer gameType;

    @ApiModelProperty(value = "游戏列表")
    private String gameList;

    @ApiModelProperty(value = "有效充值金额")
    private BigDecimal rechargeValidAmount;

    @ApiModelProperty(value = "有效体育打码金额")
    private BigDecimal sportValidAmount;

    @ApiModelProperty(value = "指定比赛ID")
    private Long matchId;

    @ApiModelProperty(value = "指定比赛时间")
    private Date matchTime;

    @ApiModelProperty(value = "指定比赛类型")
    private Integer matchType;

    @ApiModelProperty(value = "是否可以重复参加")
    private Integer isRepeat;

    @ApiModelProperty(value = "是否自动派发")
    private Integer isAutoDistribute;

    @ApiModelProperty(value = "申请日期集合")
    private String applyDateList;

    @ApiModelProperty(value = "创建人")
    private String createBy;
}
