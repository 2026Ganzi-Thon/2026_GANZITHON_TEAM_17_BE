package com.ganzithon.wool.user.service;

import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.exception.UserErrorCode;
import com.ganzithon.wool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final int MAX_SAVE_ATTEMPTS = 5;

    private final UserRepository userRepository;
    private final NicknameGenerator nicknameGenerator;
    private final NicknameValidator nicknameValidator;

    // 가입 화면에서 미리 보여줄 닉네임을 하나 돌려준다. 저장하거나 예약하지 않으므로, 가입하기 전에 다른 사람이
    // 같은 닉네임을 쓰면 그 닉네임으로 가입할 수 없을 수 있다. (그때는 새 닉네임을 다시 받는다)
    public String suggestNickname() {
        return nicknameGenerator.generate();
    }

    // 로그인 없이 사용자 ID(UUID)를 발급한다. 역할별 나이 제한은 없다.
    // 닉네임을 받으면(requestedNickname != null) 검사해서 쓰고, 없으면 서버가 자동으로 만든다.
    //
    // 호출 조건: 이 메서드는 바깥 트랜잭션 없이 호출해야 한다. @Transactional을 붙이지 않았으므로 저장 시도마다 저장소의
    // 트랜잭션이 따로 열리고 끝난다. 바깥 트랜잭션 안에서 부르면 저장소가 그 트랜잭션에 참여하는데, UNIQUE 위반으로 실패한
    // 트랜잭션은 더 쓸 수 없어서 아래의 재시도와 409 변환이 통하지 않는다. (지금은 가입 API만 호출한다)
    // 웹 요청에서는 같은 EntityManager를 요청 내내 쓰지만(Open-In-View), 저장소 트랜잭션이 롤백될 때 Spring이 그
    // EntityManager의 상태를 비우므로 실패 뒤의 조회가 정상으로 동작한다. (통합 테스트로 확인)
    public User join(Role role, AgeGroup ageGroup, String requestedNickname) {
        if (requestedNickname != null) {
            return joinWithNickname(role, ageGroup, requestedNickname);
        }
        return joinWithGeneratedNickname(role, ageGroup);
    }

    // 가입한 사용자가 닉네임을 바꾼다. 형식 -> 금지어 -> 중복 순서로 검사하고, 겹치면 409이다. (자기 자신의 닉네임은 중복이 아니다)
    // 지금 닉네임과 똑같이 보내면 바꿀 것이 없으므로 그대로 성공시킨다.
    // join과 같은 이유로 @Transactional을 쓰지 않고, 바깥 트랜잭션 없이 호출해야 한다. (저장 실패를 잡아 409로 바꾸기 위해)
    // 저장에 실패하면 메모리의 닉네임을 원래대로 되돌린다. 요청이 끝난 뒤에도 같은 객체가 남아 있을 수 있기 때문이다.
    public String updateNickname(User user, String requestedNickname) {
        String nickname = nicknameValidator.validate(requestedNickname, user.getId());
        if (nickname.equals(user.getNickname())) {
            return nickname;
        }
        String previous = user.getNickname();
        user.changeNickname(nickname);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            user.changeNickname(previous);
            if (isNicknameConflict(e, nickname, user.getId())) {
                throw new BusinessException(UserErrorCode.NICKNAME_DUPLICATED);
            }
            throw e;
        }
        return nickname;
    }

    // 사용자가 직접 정한 닉네임이다. 겹치면 서버가 마음대로 다른 닉네임으로 바꾸지 않고 409로 알려, 프론트가 사용자에게 다시 고르게 한다.
    // 확인(검증)과 저장 사이에 같은 닉네임이 먼저 저장된 경우도 같은 409로 돌려준다.
    private User joinWithNickname(Role role, AgeGroup ageGroup, String requestedNickname) {
        String nickname = nicknameValidator.validate(requestedNickname);
        try {
            return userRepository.saveAndFlush(User.join(role, ageGroup, nickname));
        } catch (DataIntegrityViolationException e) {
            if (isNicknameConflict(e, nickname)) {
                throw new BusinessException(UserErrorCode.NICKNAME_DUPLICATED);
            }
            throw e;
        }
    }

    // 서버가 만든 닉네임이다. 확인과 저장 사이에 같은 닉네임이 먼저 저장되면 새로 만들어 다시 저장한다. (최대 5번)
    // 닉네임 충돌이 아닌 무결성 오류(NOT NULL, 길이 초과 등)는 반복해도 소용없으므로 바로 예외를 올린다.
    private User joinWithGeneratedNickname(Role role, AgeGroup ageGroup) {
        for (int attempt = 1; ; attempt++) {
            String nickname = nicknameGenerator.generate();
            try {
                return userRepository.saveAndFlush(User.join(role, ageGroup, nickname));
            } catch (DataIntegrityViolationException e) {
                if (!isNicknameConflict(e, nickname) || attempt >= MAX_SAVE_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    // 저장 실패가 "방금 시도한 닉네임이 이미 있어서"인지 판별한다. 두 단계로 가린다.
    //  1) 예외에 담긴 제약 종류가 UNIQUE가 아니면(NOT NULL, 외래 키 등) 닉네임 충돌이 아니다. (Hibernate가 DB별로 분류해 준다)
    //  2) UNIQUE이거나 종류를 알 수 없으면, 그 닉네임이 실제로 저장되어 있는지 조회해서 확인한다.
    // users 테이블의 UNIQUE는 닉네임과 기본 키(UUID)뿐이고 UUID 충돌은 사실상 일어나지 않으므로, 2)까지 통과하면 닉네임 충돌로 본다.
    // 닉네임과 무관한 UNIQUE 오류인 순간 다른 요청이 같은 닉네임을 저장해 둔 경우만 잘못 판단할 수 있는데, 다음 시도에서 같은 오류가
    // 다시 나면 그대로 올라가므로 오류가 숨겨지지는 않는다.
    private boolean isNicknameConflict(DataIntegrityViolationException e, String nickname) {
        return isNicknameConflict(e, nickname, null);
    }

    // 닉네임을 바꾸다 실패한 경우에는 자기 자신을 뺀 조회를 쓴다. (대소문자만 바꾸면 내 행이 같은 값으로 잡히기 때문)
    private boolean isNicknameConflict(DataIntegrityViolationException e, String nickname, String excludedUserId) {
        if (isKnownNonUniqueViolation(e)) {
            return false;
        }
        return excludedUserId == null
                ? userRepository.existsByNickname(nickname)
                : userRepository.existsByNicknameAndIdNot(nickname, excludedUserId);
    }

    private static boolean isKnownNonUniqueViolation(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                return violation.getKind() != ConstraintViolationException.ConstraintKind.UNIQUE;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
