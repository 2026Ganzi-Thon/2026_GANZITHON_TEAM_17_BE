package com.ganzithon.wool.worry.service;

import com.ganzithon.wool.worry.repository.MyWorryListRepository;
import com.ganzithon.wool.worry.response.MyWorryListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MyWorryListService {
    private final MyWorryListRepository repository;

    @Transactional(readOnly = true)
    public MyWorryListResponse findFor(String authorId) {
        return new MyWorryListResponse(repository.findByAuthorId(authorId));
    }
}
