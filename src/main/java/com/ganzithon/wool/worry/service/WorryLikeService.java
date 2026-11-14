package com.ganzithon.wool.worry.service;

import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.exception.WorryErrorCode;
import com.ganzithon.wool.worry.repository.WorryLikeRepository;
import com.ganzithon.wool.worry.repository.WorryRepository;
import com.ganzithon.wool.worry.response.WorryLikeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorryLikeService {
    private final WorryRepository worries;
    private final WorryLikeRepository likes;

    // 같은 고민의 토글·공개 범위 변경·삭제가 한 번에 하나씩 처리된다.
    // READ_COMMITTED는 잠금 대기 전에 조회가 있더라도 확정된 최신 좋아요 수를 읽는다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WorryLikeResponse toggle(String userId, Long worryId) {
        Worry worry = worries.findByIdAndDeletedAtIsNull(worryId)
                .orElseThrow(() -> new BusinessException(WorryErrorCode.NOT_FOUND));
        if (worry.isHidden()) {
            throw new BusinessException(WorryErrorCode.NOT_FOUND);
        }
        if (worry.getVisibility() != WorryVisibility.PUBLIC) {
            throw new BusinessException(WorryErrorCode.FORBIDDEN_VIEW);
        }
        boolean likedByMe = likes.delete(worryId, userId) == 0;
        if (likedByMe) {
            likes.insert(worryId, userId);
        }
        return new WorryLikeResponse(worryId, likes.count(worryId), likedByMe);
    }
}
