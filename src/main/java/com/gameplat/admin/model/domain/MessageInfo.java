package com.gameplat.admin.model.domain;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 站内信和消息
 *
 * @author: kenvin
 * @date: 2021/4/28 15:53
 * @desc:
 */
@Data
@TableName("message_info")
public class MessageInfo implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 主键 */
  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  /** 消息标题 */
  private String title;

  /** 消息内容 */
  private String content;

  /** 总类别:0.默认(3) 1.游戏(2) 2.足球(2) 3.直播(2) 4.系统(1) */
  private Integer category;

  /** 位置: 0.默认(1) 1.推荐(2) 2.首页大厅(2,3) 3.彩票首页(3) 4.体育首页(3) 5.游戏首页(3) */
  private Integer position;

  /** 展示类型: 0.默认(1) 1.滚动(2) 2.文本弹窗(3) 3.图片弹窗(3) */
  private Integer showType;

  /** PC端图片 */
  private String pcImage;

  /** APP端图片 */
  private String appImage;

  /** 弹出次数:0.默认(1,2) 1.只弹一次(3) 2.多次弹出(3) */
  private Integer popsCount;

  /** 推送范围:1.全部会员 2.部分会员 3.在线会员 4.充值层级 5.VIP等级 6.代理线 */
  private Integer pushRange;

  /** 关联账号 */
  private String linkAccount;

  /** 开始时间 */
  private Date beginTime;

  /** 结束时间 */
  private Date endTime;

  /** 升序排序 */
  private Integer sort;

  /** 消息类型（1.系统消息、2.平台公告、3.个人弹窗消息） */
  private Integer type;

  /** 语种 */
  private String language;

  /** 状态：0 禁用 1 启用 */
  private Integer status;

  /** 是否即时消息: 0 否 1 是 */
  private Integer immediateFlag;

  /** 备注 */
  private String remarks;

  /** 创建人 */
  @TableField(fill = FieldFill.INSERT)
  private String createBy;

  /** 创建时间 */
  @TableField(fill = FieldFill.INSERT)
  @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
  private Date createTime;

  /** 更新人 */
  @TableField(fill = FieldFill.INSERT_UPDATE)
  private String updateBy;

  /** 更新时间 */
  @TableField(fill = FieldFill.INSERT_UPDATE)
  private Date updateTime;

  @ApiModelProperty("意见反馈类型 0:活动建议 1:功能建议 2:游戏BUG 3:其他问题")
  private String feedbackType;

  /** 更新人 */
  @ApiModelProperty("意见反馈回复图片内容")
  private String feedbackImage;
}
