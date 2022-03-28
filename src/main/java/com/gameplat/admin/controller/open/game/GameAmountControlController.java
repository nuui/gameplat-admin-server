package com.gameplat.admin.controller.open.game;

import com.gameplat.admin.model.vo.GameAmountControlVO;
import com.gameplat.admin.service.GameAmountControlService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/admin/game/gameAmountControl")
public class GameAmountControlController {

  @Autowired private GameAmountControlService gameAmountControlService;

  @GetMapping("/list")
  @PreAuthorize("hasAuthority('game:gameAmountControl:list')")
  public List<GameAmountControlVO> selectGameAmountList() {
    return gameAmountControlService.selectGameAmountList();
  }
}
