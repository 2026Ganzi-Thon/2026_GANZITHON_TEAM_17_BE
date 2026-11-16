package com.ganzithon.wool.feedback.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ganzithon.wool.feedback.entity.Thanks;
import com.ganzithon.wool.feedback.entity.ThanksStamp;
import com.ganzithon.wool.global.json.StrictStringDeserializer;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateThanksRequest(
        @NotNull ThanksStamp stamp,
        @JsonDeserialize(using = StrictStringDeserializer.class) String message
) {
    @JsonIgnore
    @AssertTrue(message = "한마디는 255자 이하여야 합니다.")
    public boolean isMessageValid() {
        return message == null || message.codePointCount(0, message.length()) <= Thanks.MESSAGE_MAX_LENGTH;
    }
}
