package com.ganzithon.wool.worry.service;

import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.exception.WorryErrorCode;
import com.ganzithon.wool.worry.repository.WorryRepository;
import com.ganzithon.wool.worry.response.DeleteWorryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorryDeleteService {
    private final WorryRepository worryRepository;

    @Transactional
    public DeleteWorryResponse delete(String userId, Long worryId) {
        Worry worry = worryRepository.findByIdAndDeletedAtIsNull(worryId)
                .orElseThrow(() -> new BusinessException(WorryErrorCode.NOT_FOUND));
        if (!worry.getAuthor().getId().equals(userId)) {
            throw new BusinessException(WorryErrorCode.FORBIDDEN_DELETE);
        }
        worry.delete();
        worryRepository.flush();
        return new DeleteWorryResponse(worry.getId());
    }
}
