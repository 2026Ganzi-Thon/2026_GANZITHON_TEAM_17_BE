package com.ganzithon.wool.worry.service;

import com.ganzithon.wool.filter.service.ContentFilter;
import com.ganzithon.wool.filter.service.CrisisDetector;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.repository.WorryRepository;
import com.ganzithon.wool.worry.request.CreateWorryRequest;
import com.ganzithon.wool.worry.response.CreateWorryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorryService {
    private final WorryRepository worryRepository;
    private final ContentFilter contentFilter;
    private final CrisisDetector crisisDetector;

    public CreateWorryResponse create(User author, CreateWorryRequest request) {
        contentFilter.validateLongText(request.title());
        contentFilter.validateLongText(request.content());
        boolean crisisDetected = crisisDetector.detect(request.content());
        Worry worry = worryRepository.save(Worry.of(author, request.title(), request.content(),
                request.tag(), request.visibility()));
        return new CreateWorryResponse(worry.getId(), crisisDetected, worry.getCreatedAt());
    }
}
