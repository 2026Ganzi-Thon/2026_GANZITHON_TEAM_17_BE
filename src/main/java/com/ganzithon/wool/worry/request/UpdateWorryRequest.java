package com.ganzithon.wool.worry.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.ganzithon.wool.worry.entity.WorryTag;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

public class UpdateWorryRequest {
    @Size(max = 40)
    private String title;
    @Size(max = 1000)
    private String content;
    private WorryTag tag;

    @JsonSetter(value = "title", nulls = Nulls.FAIL)
    public void setTitle(String title) {
        this.title = title;
    }

    @JsonSetter(value = "content", nulls = Nulls.FAIL)
    public void setContent(String content) {
        this.content = content;
    }

    @JsonSetter(value = "tag", nulls = Nulls.FAIL)
    public void setTag(WorryTag tag) {
        this.tag = tag;
    }

    public String title() {
        return title;
    }

    public String content() {
        return content;
    }

    public WorryTag tag() {
        return tag;
    }

    @AssertTrue
    public boolean isValidUpdate() {
        return (title != null || content != null || tag != null)
                && (title == null || !title.isBlank()) && (content == null || !content.isBlank());
    }
}
