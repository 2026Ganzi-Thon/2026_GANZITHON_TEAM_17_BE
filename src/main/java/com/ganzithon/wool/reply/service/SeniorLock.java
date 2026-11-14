package com.ganzithon.wool.reply.service;

import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.global.exception.CommonErrorCode;
import com.ganzithon.wool.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 어르신의 누적 답장 수(털 단계, 감사장 발급)를 바꾸는 작업이 같은 어르신 기준으로 한 번에 하나씩 처리되게 하는 잠금이다.
// 답장 작성, 답장 삭제, (앞으로) 답장 신고로 인한 숨김이 모두 이 잠금을 쓴다.
// 현재: 답장 작성은 고민 행 → 어르신 행, 답장 삭제는 답장 행 → 어르신 행이라 두 작업 사이에 순환 대기(교착)가 없다.
// 앞으로 답장 수정·고마움·신고를 만들 때도 지킬 규칙: 답장(또는 고민) 행을 먼저 잠그고 어르신 행은 항상 마지막에 잠근다.
// 어르신 행을 먼저 잠근 채 답장 행을 잠그지 않는다. 여러 답장을 한 번에 잠가야 하면 답장 ID 순서로 잠근다.
@Component
@RequiredArgsConstructor
public class SeniorLock {

    private final EntityManager entityManager;

    // 사용자 행을 잠그면서 DB의 최신 값으로 다시 읽는다. 이미 영속성 컨텍스트에 있는 엔티티는 잠금 조회만으로는 갱신되지 않고,
    // MySQL 기본 격리 수준(REPEATABLE READ)에서는 일반 조회가 트랜잭션 시작 시점의 값을 돌려줄 수 있어서 잠금과 함께 refresh한다.
    public User lock(String seniorId) {
        User user = entityManager.find(User.class, seniorId);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.UNREGISTERED_USER);
        }
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);
        return user;
    }
}
