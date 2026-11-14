package com.ganzithon.wool.filter.service;

import com.ganzithon.wool.filter.client.SolarClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

// 고민 본문에 자해·극단적 선택을 암시하는 표현이 있는지만 판단한다. (등록은 막지 않고, 호출하는 쪽이 109 안내를 붙인다.)
// Solar가 판단하고, API 키가 없거나 호출이 실패하면 위기 키워드 목록으로 대체한다. 어떤 경우에도 예외를 던지지 않는다.
// Solar가 정상적으로 답했다면 그 판단을 따른다. (정책 POL-SAF-05: LLM 호출이 실패했을 때만 키워드로 대체)
@Slf4j
@Component
public class CrisisDetector {

    // "죽고 싶지 않아요"처럼 키워드 바로 뒤에 "지 않"이 붙으면 부정 표현이므로 위기로 보지 않는다.
    // 문장 전체의 뜻을 읽는 것이 아니라 바로 뒤 글자만 보는 단순한 규칙이다. "죽고 싶지 않은 척했어요"처럼
    // 부정으로 보이지만 실제로는 아닌 대표적인 꼴(척, 체, 듯)만 따로 되살린다. 그 밖의 어색한 문장은 놓칠 수 있고,
    // 이 목록은 Solar가 실패했을 때만 쓰는 대체 수단이라 그 한계를 받아들인다.
    private static final String NEGATION = "지않";
    private static final List<String> NOT_REAL_NEGATION = List.of("은척", "는척", "은체", "는체", "은듯", "는듯");

    private final SolarClient solarClient;
    private final List<String> crisisKeywords = KeywordList.load("filter/crisis-keywords.txt");

    public CrisisDetector(SolarClient solarClient) {
        this.solarClient = solarClient;
    }

    public boolean detect(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        if (solarClient.isConfigured()) {
            try {
                return solarClient.isCrisis(text);
            } catch (RuntimeException e) {
                // 고민 내용과 Solar 요청·응답은 민감하므로 로그에 남기지 않고 실패 종류만 남긴다.
                log.warn("event=crisis_solar_failed fallback=keywords reason={}", e.getClass().getSimpleName());
            }
        }
        return containsCrisisKeyword(text);
    }

    // 위기 안내는 등록을 막지 않고 109 안내만 붙이므로, 오탐의 비용은 작고 미탐의 비용은 크다.
    // 그래서 과거형("죽고 싶었어요")이나 "자살 예방" 같은 표현은 그대로 위기로 보고, 뜻이 분명한 부정 표현만 제외한다.
    private boolean containsCrisisKeyword(String text) {
        String normalized = TextNormalizer.normalize(text);
        for (String keyword : crisisKeywords) {
            int from = 0;
            int index;
            while ((index = normalized.indexOf(keyword, from)) >= 0) {
                if (!isNegated(normalized, index + keyword.length())) {
                    return true;
                }
                from = index + 1;
            }
        }
        return false;
    }

    private static boolean isNegated(String normalized, int afterKeyword) {
        if (!normalized.startsWith(NEGATION, afterKeyword)) {
            return false;
        }
        int afterNegation = afterKeyword + NEGATION.length();
        return NOT_REAL_NEGATION.stream().noneMatch(feigned -> normalized.startsWith(feigned, afterNegation));
    }
}
