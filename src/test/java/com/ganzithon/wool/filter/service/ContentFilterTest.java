package com.ganzithon.wool.filter.service;

import com.ganzithon.wool.filter.exception.FilterErrorCode;
import com.ganzithon.wool.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentFilterTest {

    private final ContentFilter contentFilter = new ContentFilter();

    // ---------- 금지어 ----------

    @ParameterizedTest
    @ValueSource(strings = {"씨발", "씨 발", "씨-발", "씨.발!!", "오늘 정말 개새끼 같아요", "ㅅㅂ", "너 씨 발", "너 씨 팔 진짜"})
    void 금지어는_띄어쓰기와_특수문자로_숨겨도_잡는다(String text) {
        assertThat(contentFilter.containsBannedWord(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"오늘 힘든 하루였어요", "입사 8개월 차인데 일이 안 맞아요", "고마워요 선배님", "그러니 미안해요", ""})
    void 평범한_글은_금지어로_걸리지_않는다(String text) {
        assertThat(contentFilter.containsBannedWord(text)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "아저씨 발이 너무 커서 맞는 신발이 없대요",
            "아저씨 팔이 아프다고 하셨어요",
            "아침 7시 팔이 저려서 깼어요",
            "열 시 팔 분에 만나기로 했어요"})
    void 서로_다른_단어가_공백을_지우면_이어_붙는_경우는_금지어로_보지_않는다(String text) {
        assertThat(contentFilter.containsBannedWord(text)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ㅋㅋㅋㅋ 진짜 웃겨요", "ㅇㅇ 알겠어요 ㅎㅎ", "ㅠㅠ 너무 속상해요", "ㅜㅜ", "ㄱㅅ 해요"})
    void 자주_쓰는_초성_표현은_금지어로_걸리지_않는다(String text) {
        assertThat(contentFilter.containsBannedWord(text)).isFalse();
    }

    // ---------- 전화번호 ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "010-1234-5678", "010 1234 5678", "01012345678", "010.1234.5678",
            "02-123-4567", "031 123 4567",
            "070-1234-5678", "07012345678",
            "+82-10-1234-5678", "+82 10 1234 5678", "+82-2-123-4567", "0082-10-1234-5678",
            "010/1234/5678", "(010) 1234-5678"})
    void 전화번호는_구분자와_형식이_달라도_잡는다(String text) {
        assertThat(contentFilter.containsPhoneNumber(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026년 11월 8일", "본문은 1000자까지 쓸 수 있어요", "월급이 2500000원이에요", "12345", "3년 차", "2026.11.08 10:30", "82점을 받았어요"})
    void 전화번호가_아닌_숫자는_걸리지_않는다(String text) {
        assertThat(contentFilter.containsPhoneNumber(text)).isFalse();
    }

    // ---------- 링크·외부 연락 ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/abc",
            "www.naver.com 에서 봐요",
            "open.kakao.com/o/gAbC123",
            "오픈채팅 주소 알려드릴게요",
            "오픈채팅 링크는 여기 있어요",
            "카톡 아이디 abc123 로 연락주세요",
            "카카오톡 ID는 hello",
            "텔레그램 계정 abc123",
            "라인 아이디 abc123",
            "저한테 메일 주세요 hello@example.com",
            "인스타 @my_account"})
    void 링크와_외부_연락_수단은_잡는다(String text) {
        assertThat(contentFilter.containsExternalContact(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.dev", "https://example.co", "https://example.shop", "example.site", "mysite.online/abc", "hello.info"})
    void 흔하지_않은_최상위_도메인도_잡는다(String text) {
        assertThat(contentFilter.containsExternalContact(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"hxxps://example.com", "example[.]com", "example(dot)com", "카 톡 아이디 abc123", "오픈 채팅 주소 알려드릴게요"})
    void 일부러_깨서_쓴_링크와_이름도_잡는다(String text) {
        assertThat(contentFilter.containsExternalContact(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "카톡으로 연락이 안 와요",
            "라인이 예뻐서 고민이에요",
            "친구가 인스타를 너무 자주 봐요",
            "회사 메일이 쌓여서 힘들어요",
            "오픈채팅이 뭔가요?",
            "오픈채팅으로 상담받을 수 있나요?",
            "도면의 라인 번호를 확인해주세요.",
            "작업 라인 번호가 3번입니다.",
            "카톡 보냈는데 읽지를 않아요"})
    void 단어만_나온_평범한_문장은_외부_연락으로_걸리지_않는다(String text) {
        assertThat(contentFilter.containsExternalContact(text)).isFalse();
    }

    // ---------- 2차 리뷰 보완 ----------

    @ParameterizedTest
    @ValueSource(strings = {"씨1발", "씨123발", "개1새2끼", "씨 1 발", "너 씨12발 진짜", "씨１발"})
    void 금지어_글자_사이에_숫자를_끼워도_잡는다(String text) {
        assertThat(contentFilter.containsBannedWord(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "아저씨 2발이 필요하대요",
            "아저씨2 발이 아파요",
            "2026년 11월 8일에 입사했어요",
            "월급이 2500000원이에요",
            "3개월 차인데 새끼 고양이를 키워요",
            "1시 팔 분에 만나요"})
    void 숫자가_들어간_평범한_글은_금지어로_걸리지_않는다(String text) {
        assertThat(contentFilter.containsBannedWord(text)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"050-1234-5678", "0505-1234-5678", "050 5123 4567", "064-123-4567", "064 1234 5678",
            "010:1234:5678", "010 1234 5678", "010　1234　5678", "010 1234 5678"})
    void 평생번호_제주_지역번호_콜론과_유니코드_공백_구분_전화번호도_잡는다(String text) {
        assertThat(contentFilter.containsPhoneNumber(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-08", "2026.11.08 10:30", "12:30:45", "1234-5678-9012", "0505 몇 번이에요", "100-200-3000"})
    void 날짜_시간_일반_숫자는_전화번호로_걸리지_않는다(String text) {
        assertThat(contentFilter.containsPhoneNumber(text)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"line id", "LINE ID abc", "라인 id", "dm id", "DM 계정 abc", "저는 line id가 abc예요", "카톡 id 알려드려요"})
    void 영문_플랫폼_이름과_id는_독립된_단어일_때_잡는다(String text) {
        assertThat(contentFilter.containsExternalContact(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "online id를 만들었어요",
            "deadline id를 못 지켰어요",
            "headline id 문서를 봤어요",
            "admin id 권한이 없어요",
            "line identity를 확인해요",
            "dm identity 문제예요",
            "카톡 identity가 궁금해요",
            "kakao idea를 냈어요"})
    void 다른_영문_단어의_일부인_line_dm_id는_외부_연락으로_걸리지_않는다(String text) {
        assertThat(contentFilter.containsExternalContact(text)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "오픈채팅 링크", "오픈채팅방 링크", "오픈채팅 주소", "오픈채팅방 주소", "오픈 채팅방 링크",
            "카카오톡 오픈채팅으로 연락 주세요", "카톡으로 연락해 주세요", "카톡으로 연락주세요", "오픈채팅방으로 연락 바랍니다"})
    void 오픈채팅방_링크와_주소_연락_요청_표현도_잡는다(String text) {
        assertThat(contentFilter.containsExternalContact(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"오픈채팅방이 뭔가요?", "오픈채팅방에서 상담받을 수 있나요?", "카톡으로 연락해서 물어봤어요", "카톡으로 연락이 와서 속상해요"})
    void 오픈채팅방_일반_언급과_지난_일_이야기는_외부_연락으로_걸리지_않는다(String text) {
        assertThat(contentFilter.containsExternalContact(text)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"example[.]com", "example [.] com", "example[.] com", "example (dot) com", "example {dot} com",
            "hxxps://example[.]com", "hxxps://example [.] com", "example  [.]  com"})
    void 점을_깨서_쓴_링크는_앞뒤_공백이_있어도_잡는다(String text) {
        assertThat(contentFilter.containsExternalContact(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"그 사람 (dot) 같은 점이 있어요", "여기 [.] 에 점을 찍어요", "오늘 정말 힘들었어요 (dot) 그래도 괜찮아요"})
    void 점_표기가_있어도_도메인이_아니면_걸리지_않는다(String text) {
        assertThat(contentFilter.containsExternalContact(text)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"example.ai", "example.tech", "example.cloud", "example.design", "example.com.au", "https://my.example.ai/page", "shop.studio"})
    void 주요_신규_최상위_도메인과_국가_도메인도_잡는다(String text) {
        assertThat(contentFilter.containsExternalContact(text)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"AI 기술이 궁금해요", "3.5 정도 나왔어요", "오늘 힘들었다.그래서 쉬었어요", "사진.jpg 를 올렸어요", "회의록.pdf 확인했어요"})
    void 도메인이_아닌_마침표_표기는_걸리지_않는다(String text) {
        assertThat(contentFilter.containsExternalContact(text)).isFalse();
    }

    // ---------- 규칙별 적용 대상 ----------

    @Test
    void 긴_글은_금지어_전화번호_링크를_모두_검사한다() {
        assertErrorCode(() -> contentFilter.validateLongText("씨발"), FilterErrorCode.BANNED_WORD);
        assertErrorCode(() -> contentFilter.validateLongText("010-1234-5678"), FilterErrorCode.PHONE_NUMBER);
        assertErrorCode(() -> contentFilter.validateLongText("https://example.com"), FilterErrorCode.EXTERNAL_CONTACT);
    }

    @Test
    void 짧은_글은_링크를_검사하지_않는다() {
        assertThatCode(() -> contentFilter.validateShortText("https://example.com 도움이 됐어요")).doesNotThrowAnyException();
        assertErrorCode(() -> contentFilter.validateShortText("씨발"), FilterErrorCode.BANNED_WORD);
        assertErrorCode(() -> contentFilter.validateShortText("010-1234-5678"), FilterErrorCode.PHONE_NUMBER);
    }

    @Test
    void 닉네임은_금지어만_검사한다() {
        assertThatCode(() -> contentFilter.validateNickname("포근한 양 312")).doesNotThrowAnyException();
        assertThatCode(() -> contentFilter.validateNickname("01012345678")).doesNotThrowAnyException();
        assertErrorCode(() -> contentFilter.validateNickname("씨 발"), FilterErrorCode.BANNED_WORD);
    }

    @Test
    void 금지어와_전화번호가_함께_있으면_금지어_오류가_먼저다() {
        assertErrorCode(() -> contentFilter.validateLongText("씨발 010-1234-5678"), FilterErrorCode.BANNED_WORD);
    }

    @Test
    void 비어_있는_글은_검사하지_않는다() {
        assertThatCode(() -> contentFilter.validateLongText(null)).doesNotThrowAnyException();
        assertThatCode(() -> contentFilter.validateLongText("   ")).doesNotThrowAnyException();
    }

    private void assertErrorCode(Runnable action, FilterErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
