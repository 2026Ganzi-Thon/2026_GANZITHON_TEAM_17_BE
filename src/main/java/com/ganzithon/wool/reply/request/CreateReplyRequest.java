package com.ganzithon.wool.reply.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ganzithon.wool.global.json.StrictStringDeserializer;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.entity.ReplyType;
import com.ganzithon.wool.upload.service.LetterImageUrl;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

// TEXT·VOICE는 content만, LETTER는 imageUrl만 받는다. type과 맞지 않는 필드가 있거나 필요한 필드가 없으면 400이다.
// 문자열만 받는다. 숫자나 true가 문자열로 바뀌어 통과하지 않도록 전용 역직렬화기를 쓴다.
// content는 원문 그대로 저장하므로 앞뒤 공백을 지우지 않는다. 공백뿐인 글만 거부한다.
public record CreateReplyRequest(
        @NotNull ReplyType type,
        @JsonDeserialize(using = StrictStringDeserializer.class) String content,
        @JsonDeserialize(using = StrictStringDeserializer.class) String imageUrl
) {

    @JsonIgnore
    @AssertTrue(message = "type에 맞는 content 또는 imageUrl만 보내야 합니다.")
    public boolean isValidForType() {
        if (type == null) {
            return true; // type 누락은 @NotNull이 알린다.
        }
        if (type == ReplyType.LETTER) {
            // 손편지 업로드(REPLY-004)가 돌려준 주소만 받는다. 임의의 외부 주소를 답장에 담지 못하게 한다.
            return content == null && LetterImageUrl.isValid(imageUrl);
        }
        return imageUrl == null && content != null && !content.isBlank()
                && length(content) <= Reply.CONTENT_MAX_LENGTH;
    }

    // DB 길이는 문자(코드포인트) 단위다. 이모지가 둘로 세어지지 않게 String.length()를 쓰지 않는다.
    private static int length(String text) {
        return text.codePointCount(0, text.length());
    }
}
