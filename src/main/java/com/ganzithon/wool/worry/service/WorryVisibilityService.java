package com.ganzithon.wool.worry.service;

import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.global.exception.CommonErrorCode;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.exception.WorryErrorCode;
import com.ganzithon.wool.worry.repository.WorryRepository;
import com.ganzithon.wool.worry.response.ChangeVisibilityResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorryVisibilityService {
    private final WorryRepository worryRepository;

    @Transactional
    public ChangeVisibilityResponse change(String userId, Long worryId, WorryVisibility visibility) {
        Worry worry = worryRepository.findByIdAndDeletedAtIsNull(worryId)
                .orElseThrow(() -> new BusinessException(WorryErrorCode.NOT_FOUND));
        if (!worry.getAuthor().getId().equals(userId)) {
            throw new BusinessException(WorryErrorCode.FORBIDDEN_CHANGE);
        }
        if (worry.isHidden()) {
            throw new BusinessException(WorryErrorCode.NOT_FOUND);
        }
        if (worry.getVisibility() == WorryVisibility.SENIORS_ONLY) {
            throw new BusinessException(WorryErrorCode.VISIBILITY_CONFLICT);
        }
        if (visibility != WorryVisibility.SENIORS_ONLY) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        worry.makeSeniorsOnly();
        return new ChangeVisibilityResponse(worry.getId(), worry.getVisibility());
    }
}
