package com.ganzithon.wool.user.service;

import com.ganzithon.wool.filter.service.ContentFilter;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.global.exception.CommonErrorCode;
import com.ganzithon.wool.user.exception.UserErrorCode;
import com.ganzithon.wool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

// 사용자가 직접 정한 닉네임을 검사한다. 가입(닉네임 선택 입력)과 닉네임 수정이 함께 쓴다.
// 검사 순서: 형식(400) -> 금지어(422) -> 중복(409). 비용이 작은 검사를 먼저 하고, DB 조회는 마지막에 한다.
@Component
@RequiredArgsConstructor
public class NicknameValidator {

    public static final int MIN_LENGTH = 2;
    public static final int MAX_LENGTH = 10;

    // 앞뒤의 공백을 모두 잘라낸다. String.strip()은 줄바꿈 없는 공백(NBSP, U+00A0 등)을 남기므로 유니코드 공백 구분자(\p{Z})까지 함께 지운다.
    private static final Pattern EDGE_SPACES = Pattern.compile("^[\\s\\p{Z}]+|[\\s\\p{Z}]+$");

    private static final int ZERO_WIDTH_JOINER = 0x200D;

    // 글자처럼 보이지만 화면에는 아무것도 그려지지 않아 "빈 닉네임"이나 똑같아 보이는 닉네임을 만드는 데 쓰이는 문자.
    // 한글 채움 문자(U+115F, U+1160, U+3164, U+FFA0)와 점자 빈칸(U+2800)이 대표적이다.
    private static final Set<Integer> INVISIBLE_LOOKALIKES = Set.of(0x115F, 0x1160, 0x3164, 0xFFA0, 0x2800);

    private final ContentFilter contentFilter;
    private final UserRepository userRepository;

    // 통과하면 앞뒤 공백을 잘라낸 닉네임을 돌려준다. (닉네임은 사람이 쓴 글이 아니라 식별용 이름이라 공백을 정리해 저장한다)
    public String validate(String raw) {
        String nickname = checkFormat(raw);
        contentFilter.validateNickname(nickname);
        if (userRepository.existsByNickname(nickname)) {
            throw new BusinessException(UserErrorCode.NICKNAME_DUPLICATED);
        }
        return nickname;
    }

    // 길이는 코드포인트 단위로 센다. DB의 VARCHAR(10)이 코드포인트 단위라 String.length()나 사용자가 보는 글자 수와 다를 수 있다.
    // (이모지 한 개는 1자, 가족 이모지처럼 여러 개를 이어 붙인 것은 7자로 센다)
    private String checkFormat(String raw) {
        if (raw == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        String nickname = EDGE_SPACES.matcher(raw).replaceAll("");
        int length = nickname.codePointCount(0, nickname.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH || nickname.codePoints().anyMatch(NicknameValidator::isRejected)) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        return nickname;
    }

    // 닉네임 안에 둘 수 없는 문자:
    //  - 제어 문자(줄바꿈, 탭 등)
    //  - 보이지 않는 서식 문자(제로폭 공백 U+200B, 방향 제어 U+202E, BOM U+FEFF 등). 이모지를 이어 붙이는 U+200D만 허용한다.
    //  - 일반 공백(U+0020) 외의 공백(NBSP, 전각 공백 등)
    //  - 화면에 그려지지 않는 글자 모양 문자(한글 채움 문자 등)
    private static boolean isRejected(int codePoint) {
        if (Character.isISOControl(codePoint) || INVISIBLE_LOOKALIKES.contains(codePoint)) {
            return true;
        }
        return switch (Character.getType(codePoint)) {
            case Character.FORMAT -> codePoint != ZERO_WIDTH_JOINER;
            case Character.SPACE_SEPARATOR, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> codePoint != ' ';
            default -> false;
        };
    }
}
