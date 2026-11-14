package com.ganzithon.wool.worry.service;

import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.repository.PublicWorryFeedRepository;
import com.ganzithon.wool.worry.response.PublicWorryFeedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicWorryFeedService {
    private final PublicWorryFeedRepository repository;

    @Transactional(readOnly = true)
    public PublicWorryFeedResponse find(WorryTag tag) {
        return new PublicWorryFeedResponse(repository.find(tag));
    }
}
