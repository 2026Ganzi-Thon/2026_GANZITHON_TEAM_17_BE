package com.ganzithon.wool.user.service;

import com.ganzithon.wool.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

// 닉네임을 "형용사 + 양 + 숫자"로 자동 만든다. (예: 포근한 양 312)
// 길이는 2~10자여야 하므로 형용사는 3자 이하, 숫자는 세 자리(100~999)로 한다. ("포근한 양 312" = 9자)
// 형용사 목록은 직접 고른 순한 말이라 금지어 검사를 거치지 않는다. (테스트가 목록 전체를 금지어 검사로 확인한다)
@Component
public class NicknameGenerator {

    static final List<String> ADJECTIVES = List.of(
            "포근한", "따뜻한", "다정한", "느긋한", "수줍은", "씩씩한", "차분한", "푸근한",
            "든든한", "상냥한", "활발한", "온화한", "명랑한", "듬직한", "고운", "착한",
            "맑은", "순한", "귀한", "넉넉한");

    static final int MIN_NUMBER = 100;
    static final int MAX_NUMBER = 999;
    static final int MAX_ATTEMPTS = 30;

    private final UserRepository userRepository;
    private final RandomGenerator random;

    @Autowired
    public NicknameGenerator(UserRepository userRepository) {
        // RandomGenerator.getDefault()는 jdk.random 모듈이 없는 실행 환경에서 실패하므로, 항상 있는 Random을 쓴다.
        this(userRepository, new Random());
    }

    // 테스트에서 난수를 고정할 수 있게 열어 둔다.
    NicknameGenerator(UserRepository userRepository, RandomGenerator random) {
        this.userRepository = userRepository;
        this.random = random;
    }

    // 기존 닉네임과 겹치지 않는 닉네임을 만든다. 조합(20 x 900 = 18000개)이 거의 소진돼 계속 겹치면 예외를 던진다.
    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String nickname = randomNickname();
            if (!userRepository.existsByNickname(nickname)) {
                return nickname;
            }
        }
        throw new IllegalStateException("사용할 수 있는 닉네임을 만들지 못했습니다.");
    }

    String randomNickname() {
        String adjective = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
        int number = random.nextInt(MIN_NUMBER, MAX_NUMBER + 1);
        return adjective + " 양 " + number;
    }
}
