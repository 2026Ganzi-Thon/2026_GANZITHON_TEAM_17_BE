package com.ganzithon.wool.filter.service;

// 금지어·위기 키워드를 비교할 때만 쓰는 사본을 만든다. 사용자 글 원문은 절대 고치지 않는다.
// 글자와 숫자만 남기고(공백, 특수문자 제거) 소문자로 바꾼다. 세 메서드 모두 같은 코드포인트 단위 구현을 쓴다.
final class TextNormalizer {

    private TextNormalizer() {
    }

    static String normalize(String text) {
        return text == null ? "" : build(text, true).text();
    }

    // 정규화한 글자가 원문에서 몇 번째 위치(UTF-16 인덱스)였는지 함께 돌려준다.
    // (공백으로 끊어 쓴 단어와 우연히 이어 붙은 단어를 구분할 때 쓴다.)
    static Normalized normalizeWithOrigin(String text) {
        return build(text, true);
    }

    // 숫자까지 지운 사본. "씨1발"처럼 글자 사이에 숫자를 끼워 넣은 우회를 잡을 때만 쓴다.
    static Normalized normalizeLettersWithOrigin(String text) {
        return build(text, false);
    }

    // 코드포인트 단위로 훑되, 결과 문자열의 각 char가 원문의 어느 위치에서 왔는지 origin에 기록한다.
    // (보조 평면 문자는 char 두 개가 같은 원문 위치를 가리킨다.)
    private static Normalized build(String text, boolean keepDigits) {
        StringBuilder normalized = new StringBuilder(text.length());
        int[] origin = new int[text.length()];
        int i = 0;
        while (i < text.length()) {
            int codePoint = text.codePointAt(i);
            int width = Character.charCount(codePoint);
            boolean keep = Character.isLetter(codePoint) || (keepDigits && Character.isDigit(codePoint));
            if (keep) {
                int lowered = Character.toLowerCase(codePoint);
                int before = normalized.length();
                normalized.appendCodePoint(lowered);
                for (int k = before; k < normalized.length(); k++) {
                    origin[k] = i;
                }
            }
            i += width;
        }
        return new Normalized(normalized.toString(), origin);
    }

    record Normalized(String text, int[] origin) {
    }
}
