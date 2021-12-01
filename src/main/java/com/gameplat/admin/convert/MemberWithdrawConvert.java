package com.gameplat.admin.convert;

import com.gameplat.admin.model.domain.MemberWithdraw;
import com.gameplat.admin.model.dto.MemberWithdrawQueryDTO;
import com.gameplat.admin.model.vo.MemberWithdrawVO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MemberWithdrawConvert {

  MemberWithdrawVO toVo(MemberWithdraw entity);

  MemberWithdraw toEntity(MemberWithdrawQueryDTO dto);

}
