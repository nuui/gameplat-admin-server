package com.gameplat.admin.model.bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 推送消息
 *
 * @author robben
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PushMessage implements Serializable {

  private Integer channel;

  private String title;
}
