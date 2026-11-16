package com.ganzithon.wool.reply.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ganzithon.wool.global.json.StrictStringDeserializer;
import com.ganzithon.wool.reply.entity.Reply;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

// 글·음성 답장의 새 내용. 작성할 때와 같은 규칙이다: 1~1,000자(이모지는 한 글자), 공백만 있으면 안 되고 문자열만 받는다.
// 원문 그대로 저장하므로 앞뒤 공백을 지우지 않는다.
public record UpdateReplyRequest(
        @NotNull @JsonDeserialize(using = StrictStringDeserializer.class) String content
) {

    @JsonIgnore
    @AssertTrue(message = "content는 공백이 아닌 1자 이상 1,000자 이하여야 합니다.")
    public boolean isValidContent() {
        // content 누락은 @NotNull이 알린다.
        return content == null
                || (!content.isBlank() && content.codePointCount(0, content.length()) <= Reply.CONTENT_MAX_LENGTH);
    }
}
