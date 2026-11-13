package com.ganzithon.wool.worry.service;

import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.repository.ReplyRepository.VisibleReplySummary;
import com.ganzithon.wool.worry.repository.MyWorryListRepository;
import com.ganzithon.wool.worry.repository.MyWorryListRepository.WorryRow;
import com.ganzithon.wool.worry.response.MyWorryListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MyWorryListService {
    private final MyWorryListRepository repository;
    private final ReplyRepository replyRepository;

    @Transactional(readOnly = true)
    public MyWorryListResponse findFor(String authorId) {
        List<WorryRow> worries = repository.findByAuthorId(authorId);
        if (worries.isEmpty()) {
            return new MyWorryListResponse(List.of());
        }
        Map<Long, VisibleReplySummary> summaries = replyRepository.summarizeVisibleByAuthorId(authorId).stream()
                .collect(Collectors.toMap(VisibleReplySummary::getWorryId, Function.identity()));
        List<MyWorryListResponse.Item> items = worries.stream().map(worry -> {
            VisibleReplySummary summary = summaries.get(worry.worryId());
            return new MyWorryListResponse.Item(worry.worryId(), worry.title(), worry.tag(), worry.visibility(),
                    summary == null ? 0 : summary.getReplyCount(), summary != null && summary.getUnreadCount() > 0,
                    worry.hidden(), worry.createdAt());
        }).toList();
        return new MyWorryListResponse(items);
    }
}
