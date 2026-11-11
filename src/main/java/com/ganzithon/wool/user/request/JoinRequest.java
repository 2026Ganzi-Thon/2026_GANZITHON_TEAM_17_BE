package com.ganzithon.wool.user.request;

import com.ganzithon.wool.global.json.StrictStringDeserializer;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

// nickname은 선택이다. 없으면 서버가 자동으로 만들고, 있으면 검사한 뒤 그대로 쓴다. (미리 받은 추천 닉네임을 사용자가 고친 값)
// 문자열만 받는다. 숫자나 true 같은 값이 "123", "true"로 바뀌어 통과하지 않도록 전용 역직렬화기를 쓴다.
// 10대는 AgeGroup에 없으므로 받지 않는다.
public record JoinRequest(
        @NotNull Role role,
        @NotNull AgeGroup ageGroup,
        @JsonDeserialize(using = StrictStringDeserializer.class) String nickname
) {
}
