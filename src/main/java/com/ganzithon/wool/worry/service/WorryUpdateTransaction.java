package com.ganzithon.wool.worry.service;

import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.exception.WorryErrorCode;
import com.ganzithon.wool.worry.repository.WorryRepository;
import com.ganzithon.wool.worry.request.UpdateWorryRequest;
import com.ganzithon.wool.worry.response.UpdateWorryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorryUpdateTransaction {
    private final WorryRepository worryRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public void validate(String userId, Long worryId) {
        findEditable(userId, worryId);
    }

    @Transactional
    public UpdateWorryResponse update(String userId, Long worryId, UpdateWorryRequest request, boolean crisisDetected) {
        Worry worry = findEditable(userId, worryId);
        worry.update(request.title(), request.content(), request.tag());
        worryRepository.flush();
        return new UpdateWorryResponse(worry.getId(), crisisDetected, worry.getUpdatedAt());
    }

    private Worry findEditable(String userId, Long worryId) {
        Worry worry = worryRepository.findByIdAndDeletedAtIsNull(worryId)
                .orElseThrow(() -> new BusinessException(WorryErrorCode.NOT_FOUND));
        if (!worry.getAuthor().getId().equals(userId)) {
            throw new BusinessException(WorryErrorCode.FORBIDDEN_UPDATE);
        }
        if (worry.isHidden()) {
            throw new BusinessException(WorryErrorCode.NOT_FOUND);
        }
        Boolean hasReplies = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM replies WHERE worry_id = ? AND hidden = FALSE AND deleted_at IS NULL)",
                Boolean.class, worryId);
        if (Boolean.TRUE.equals(hasReplies)) {
            throw new BusinessException(WorryErrorCode.UPDATE_CONFLICT);
        }
        return worry;
    }
}
