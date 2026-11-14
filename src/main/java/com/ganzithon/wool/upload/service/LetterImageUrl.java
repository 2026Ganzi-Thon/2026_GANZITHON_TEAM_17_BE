package com.ganzithon.wool.upload.service;

import java.util.regex.Pattern;

// 손편지 업로드(REPLY-004)가 돌려주는 주소의 형태. 답장 작성(REPLY-005)은 이 형태의 주소만 받는다.
public final class LetterImageUrl {

    public static final String DIRECTORY = "letters";

    private static final Pattern FORMAT = Pattern.compile(
            "/files/" + DIRECTORY + "/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:jpg|png|webp)");

    private LetterImageUrl() {
    }

    public static boolean isValid(String url) {
        return url != null && FORMAT.matcher(url).matches();
    }
}
