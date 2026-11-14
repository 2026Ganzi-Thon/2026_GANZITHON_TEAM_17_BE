package com.ganzithon.wool.worry.service;

import com.ganzithon.wool.filter.service.ContentFilter;
import com.ganzithon.wool.filter.service.CrisisDetector;
import com.ganzithon.wool.worry.request.UpdateWorryRequest;
import com.ganzithon.wool.worry.response.UpdateWorryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorryUpdateService {
    private final ContentFilter contentFilter;
    private final CrisisDetector crisisDetector;
    private final WorryUpdateTransaction updateTransaction;

    public UpdateWorryResponse update(String userId, Long worryId, UpdateWorryRequest request) {
        updateTransaction.validate(userId, worryId);
        contentFilter.validateLongText(request.title());
        contentFilter.validateLongText(request.content());
        boolean crisisDetected = request.content() != null && crisisDetector.detect(request.content());
        return updateTransaction.update(userId, worryId, request, crisisDetected);
    }
}
