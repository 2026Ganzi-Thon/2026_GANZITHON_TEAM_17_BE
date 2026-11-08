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
    // 닉네임 중복 확인과 저장 사이에 같은 닉네임이 먼저 저장될 수 있어(UNIQUE 위반) 닉네임을 새로 만들어 다시 저장한다.
    // 한 트랜잭션 안에서 UNIQUE 위반이 나면 그 트랜잭션은 더 쓸 수 없으므로, 이 메서드는 @Transactional을 쓰지 않고
    // 저장 시도마다 저장소의 트랜잭션이 따로 열리게 한다.
    public User join(Role role, AgeGroup ageGroup) {
        for (int attempt = 1; ; attempt++) {
            try {
                return userRepository.saveAndFlush(User.join(role, ageGroup, nicknameGenerator.generate()));
            } catch (DataIntegrityViolationException e) {
                if (attempt >= MAX_SAVE_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }
}
