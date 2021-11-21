package com.gameplat.admin.model.vo.activity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * @Author: lyq
 * @Date: 2020/8/20 15:19
 * @Description:
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MemberLobbyDiscountVO implements Serializable {

    private static final long serialVersionUID = -3883439363075850398L;

    @JsonSerialize(using= ToStringSerializer.class)
    @ApiModelProperty(value = "大厅优惠id")
    private Long lobbyDiscountId;

    @JsonSerialize(using= ToStringSerializer.class)
    @ApiModelProperty(value = "活动大厅id")
    private Long lobbyId;

    @ApiModelProperty(value = "优惠url")
    private String discountUrl;

    @ApiModelProperty(value = "目标值")
    private Integer targetValue;

    @ApiModelProperty(value = "赠送值")
    private Integer presenterValue;

    @ApiModelProperty(value = "赠送打码")
    private BigDecimal presenterDml;

    @ApiModelProperty(value = "提现打码")
    private BigDecimal withdrawDml;
}
