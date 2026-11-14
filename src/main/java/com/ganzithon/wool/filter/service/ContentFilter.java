package com.ganzithon.wool.filter.service;

import com.ganzithon.wool.filter.exception.FilterErrorCode;
import com.ganzithon.wool.global.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

// 전송을 막는 규칙 기반 검사(금지어, 전화번호, 링크·외부 연락). 내용은 절대 고치지 않고, 걸리면 BusinessException만 던진다.
//   validateLongText  : 고민 제목·본문, 글·음성 답장 (금지어 + 전화번호 + 링크·외부 연락)
//   validateShortText : 고마움 한마디, 후일담 (금지어 + 전화번호)
//   validateNickname  : 닉네임 (금지어)
@Component
public class ContentFilter {

    // 공백(유니코드 공백 포함), 하이픈, 점, 슬래시, 괄호, 콜론으로 끊어 써도 잡는다.
    private static final String PHONE_SEPARATOR = "[\\s\\p{Z}.\\-/():]*";
    // 휴대폰(01x), 인터넷전화(070), 050 계열(0505처럼 한 자리 더 붙는 형태 포함), 서울(02), 지역번호(031~064 등).
    private static final String PHONE_AREA = "(?:1[016789]|70|50[2-8]?|2|[3-6][1-5])";

    // 휴대폰(010 등), 인터넷전화(070), 050 계열, 지역번호 유선전화, 국제전화 형식(+82, 0082).
    private static final Pattern PHONE_NUMBER = Pattern.compile(
            "(?<!\\d)(?:"
                    + "0" + PHONE_AREA
                    + "|(?:\\+|00)82" + PHONE_SEPARATOR + "0?" + PHONE_AREA
                    + ")" + PHONE_SEPARATOR + "\\d{3,4}" + PHONE_SEPARATOR + "\\d{4}(?!\\d)");

    // 단어만 나왔을 때는 정상 문장일 수 있어서(예: "오픈채팅이 뭔가요?"), 플랫폼 이름 뒤에 아이디·주소·링크 등이 붙을 때만 잡는다.
    // 한글 플랫폼 이름은 글자 사이에 공백 한 칸까지 끼워 넣은 우회("카 톡 아이디")도 잡는다.
    // 일반 단어와 겹치기 쉬운 이름(라인, 디엠)은 아이디·계정처럼 연락처를 뜻하는 말이 붙을 때만 잡는다.
    // 영문 플랫폼 이름은 다른 영문 단어의 끝부분이면 제외한다. ("online id", "deadline id"의 line)
    private static final String NOT_AFTER_LETTER = "(?<![a-z])";
    private static final String STRONG_PLATFORM = String.join("|",
            spaced("카카오톡"), spaced("카톡"), spaced("카카오"), spaced("텔레그램"),
            spaced("인스타그램"), spaced("인스타"), spaced("오픈채팅") + "(?:\\s?방)?",
            NOT_AFTER_LETTER + "kakao(?:talk)?", NOT_AFTER_LETTER + "telegram", NOT_AFTER_LETTER + "insta(?:gram)?");
    private static final String WEAK_PLATFORM = String.join("|",
            spaced("라인"), spaced("디엠"), NOT_AFTER_LETTER + "line", NOT_AFTER_LETTER + "dm");
    // 영문 id는 독립된 단어일 때만 인정한다. ("identity", "idea"는 제외)
    // "(으)로 연락 주세요"처럼 연락을 요청하는 표현은 STRONG 플랫폼 뒤에서만 의미가 있다.
    private static final String STRONG_FOLLOW = "아이디|id(?![a-z])|주소|링크|계정|번호|코드|친추|친구\\s*추가"
            + "|(?:으)?로\\s*연락\\s*(?:주|해\\s*주|바람|바랍)";
    private static final String WEAK_FOLLOW = "아이디|id(?![a-z])|계정|친추|친구\\s*추가";

    private static final String TLD = "com|net|org|kr|co|io|me|gg|tv|app|link|ly|xyz|dev|shop|site|store|online|info|biz|cc|so|to|page|click|top|vip|club|live|blog|cafe|asia|us|jp|cn"
            + "|ai|tech|cloud|design|pro|space|world|studio|digital|network|agency|de|uk|fr|ca|au|ru|tw|hk|sg|eu|vn|th";

    private static final Pattern EXTERNAL_CONTACT = Pattern.compile(
            String.join("|",
                    "(?:https?://|www\\.)\\S+",
                    "\\b[a-z0-9][a-z0-9\\-]*(?:\\.[a-z0-9\\-]+)*\\.(?:" + TLD + ")\\b",
                    "[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}",
                    "(?<![\\w@])@[a-z0-9_.]{3,}",
                    "(?:" + STRONG_PLATFORM + ")\\s{0,2}(?:" + STRONG_FOLLOW + ")",
                    "(?:" + WEAK_PLATFORM + ")\\s{0,2}(?:" + WEAK_FOLLOW + ")"),
            Pattern.CASE_INSENSITIVE);

    // "hxxps://", "example[.]com", "example (dot) com"처럼 링크를 일부러 깨 쓴 표기를 검사용 사본에서만 되돌린다.
    // 깨진 점 표기 양옆의 공백은 최대 두 칸까지만 함께 지운다. (일반 문장의 다른 공백은 건드리지 않는다)
    private static final Pattern DEFANG_DOT = Pattern.compile(
            "\\s{0,2}(?:\\[\\s*(?:\\.|dot)\\s*]|\\(\\s*(?:\\.|dot)\\s*\\)|\\{\\s*(?:\\.|dot)\\s*})\\s{0,2}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DEFANG_SCHEME = Pattern.compile("hxxp", Pattern.CASE_INSENSITIVE);

    private final List<String> bannedWords = KeywordList.load("filter/banned-words.txt");

    public void validateLongText(String text) {
        check(text, true, true, true);
    }

    public void validateShortText(String text) {
        check(text, true, true, false);
    }

    public void validateNickname(String text) {
        check(text, true, false, false);
    }

    private void check(String text, boolean banned, boolean phone, boolean externalContact) {
        if (text == null || text.isBlank()) {
            return;
        }
        if (banned && containsBannedWord(text)) {
            throw new BusinessException(FilterErrorCode.BANNED_WORD);
        }
        if (phone && containsPhoneNumber(text)) {
            throw new BusinessException(FilterErrorCode.PHONE_NUMBER);
        }
        if (externalContact && containsExternalContact(text)) {
            throw new BusinessException(FilterErrorCode.EXTERNAL_CONTACT);
        }
    }

    // 공백·특수문자·숫자를 지운 사본에서 금지어를 찾는다. 일치한 구간은 다음 둘 중 하나일 때만 인정한다.
    //  1) 글자 사이에 숫자만 끼어 있거나 아무것도 없다. ("씨발", "씨1발", "씨123발")
    //  2) 단어의 시작에서 시작한다. ("너 씨 발"처럼 공백·특수문자로 숨긴 욕설)
    // 공백·특수문자가 끼어 있는데 단어 중간에서 시작하면 서로 다른 단어가 우연히 이어 붙은 것이라 제외한다.
    // ("아저씨 발이"의 "씨 발"이나 "7시 팔이"의 "시 팔")
    boolean containsBannedWord(String text) {
        TextNormalizer.Normalized normalized = TextNormalizer.normalizeLettersWithOrigin(text);
        String target = normalized.text();
        int[] origin = normalized.origin();
        for (String word : bannedWords) {
            int from = 0;
            int start;
            while ((start = target.indexOf(word, from)) >= 0) {
                int last = start + word.length() - 1;
                if (onlyDigitsBetween(text, origin, start, last) || atWordStart(text, origin[start])) {
                    return true;
                }
                from = start + 1;
            }
        }
        return false;
    }

    private static boolean onlyDigitsBetween(String text, int[] origin, int start, int last) {
        for (int k = start; k < last; k++) {
            int from = origin[k] + Character.charCount(text.codePointAt(origin[k]));
            int to = origin[k + 1];
            if (to > from && !text.substring(from, to).codePoints().allMatch(Character::isDigit)) {
                return false;
            }
        }
        return true;
    }

    private static boolean atWordStart(String text, int originIndex) {
        return originIndex == 0 || !Character.isLetterOrDigit(text.codePointBefore(originIndex));
    }

    boolean containsPhoneNumber(String text) {
        return PHONE_NUMBER.matcher(text).find();
    }

    boolean containsExternalContact(String text) {
        String restored = DEFANG_DOT.matcher(DEFANG_SCHEME.matcher(text).replaceAll("http")).replaceAll(".");
        return EXTERNAL_CONTACT.matcher(restored).find();
    }

    // 한글 이름의 글자 사이에 공백 한 칸까지 허용하는 정규식 조각을 만든다. ("카톡" -> "카\s?톡")
    private static String spaced(String word) {
        return String.join("\\s?", word.split(""));
    }
}
