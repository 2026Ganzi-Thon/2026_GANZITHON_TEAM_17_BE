package com.ganzithon.wool.feedback.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ganzithon.wool.global.json.StrictStringDeserializer;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import jakarta.validation.constraints.AssertTrue;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateFollowUpRequest(@JsonDeserialize(using = StrictStringDeserializer.class) String content, WorryVisibility visibility) {
    @JsonIgnore @AssertTrue(message = "후일담은 1자 이상 1000자 이하여야 합니다.")
    public boolean isContentValid() {
        return content != null && !content.isBlank() && content.codePointCount(0, content.length()) <= 1000;
    }
}
