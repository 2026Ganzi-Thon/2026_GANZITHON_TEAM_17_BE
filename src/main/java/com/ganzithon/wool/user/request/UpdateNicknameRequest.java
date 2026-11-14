package com.ganzithon.wool.user.request;

import com.ganzithon.wool.global.json.StrictStringDeserializer;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

// 바꿀 닉네임. 문자열만 받는다. (숫자나 true 같은 값이 문자열로 바뀌어 통과하지 않도록 전용 역직렬화기를 쓴다)
// 형식(2~10자 등), 금지어, 중복 검사는 NicknameValidator가 한다.
public record UpdateNicknameRequest(
        @NotNull @JsonDeserialize(using = StrictStringDeserializer.class) String nickname
) {
}
