package com.ganzithon.wool.user.service;

import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_SAVE_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final NicknameGenerator nicknameGenerator;

    // 로그인 없이 사용자 ID(UUID)를 발급한다. 역할별 나이 제한은 없다.
    //
    // 닉네임 중복 확인과 저장 사이에 같은 닉네임이 먼저 저장될 수 있어(UNIQUE 위반) 닉네임을 새로 만들어 다시 저장한다.
    // 재시도는 "방금 시도한 닉네임이 실제로 저장되어 있을 때"만 한다. 그렇지 않으면 닉네임과 무관한 무결성 오류
    // (NOT NULL, 길이 초과 등)이므로 반복해도 소용없어 바로 예외를 올린다. (DB 종류나 예외 메시지에 기대지 않는 확인 방법)
    //
    // 호출 조건: 이 메서드는 바깥 트랜잭션 없이 호출해야 한다. 한 트랜잭션 안에서 UNIQUE 위반이 나면 그 트랜잭션은 더 쓸 수 없어서,
    // @Transactional을 쓰지 않고 저장 시도마다 저장소의 트랜잭션이 따로 열리게 했다. 바깥 트랜잭션 안에서 부르면
    // 저장소가 그 트랜잭션에 참여해 재시도가 통하지 않는다. (지금은 가입 API만 호출한다)
    public User join(Role role, AgeGroup ageGroup) {
        for (int attempt = 1; ; attempt++) {
            String nickname = nicknameGenerator.generate();
            try {
                return userRepository.saveAndFlush(User.join(role, ageGroup, nickname));
            } catch (DataIntegrityViolationException e) {
                boolean nicknameTaken = userRepository.existsByNickname(nickname);
                if (!nicknameTaken || attempt >= MAX_SAVE_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }
}
