package com.gameplat.admin.convert;

import com.gameplat.admin.model.domain.Game;
import com.gameplat.admin.model.dto.OperGameDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GameConvert {
    Game toEntity(OperGameDTO dto);
}