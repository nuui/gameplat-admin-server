package com.gameplat.admin.enums;

public enum LiveRebateReportStatus {

  UNACCEPTED(0),// 未发放
  ACCEPTED(1),// 已发放
  REJECTED(2), // 已拒发
  ROLLBACKED(3); //已回收

  private int value;

  LiveRebateReportStatus(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
