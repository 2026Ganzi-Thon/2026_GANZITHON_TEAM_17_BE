package com.ganzithon.wool.worry.service;

import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.exception.WorryErrorCode;
import com.ganzithon.wool.worry.repository.WorryDetailExtrasRepository;
import com.ganzithon.wool.worry.repository.WorryDetailExtrasRepository.ReplyExtras;
import com.ganzithon.wool.worry.repository.WorryDetailRepository;
import com.ganzithon.wool.worry.response.WorryDetailResponse;
import com.ganzithon.wool.worry.response.WorryDetailResponse.FollowUp;
import com.ganzithon.wool.worry.response.WorryDetailResponse.Person;
import com.ganzithon.wool.worry.response.WorryDetailResponse.ReplyItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorryDetailService {
    private final WorryDetailRepository worries;
    private final WorryDetailExtrasRepository extras;
    private final ReplyRepository replies;

    @Transactional(readOnly = true)
    public WorryDetailResponse find(String userId, Long worryId) {
        Worry worry = worries.findVisibleCandidate(worryId)
                .orElseThrow(() -> new BusinessException(WorryErrorCode.NOT_FOUND));
        boolean mine = worry.getAuthor().getId().equals(userId);
        if (!mine && worry.isHidden()) throw new BusinessException(WorryErrorCode.NOT_FOUND);
        if (!mine && worry.getVisibility() != WorryVisibility.PUBLIC) {
            throw new BusinessException(WorryErrorCode.FORBIDDEN_VIEW);
        }
        var likes = extras.worryLikes(worryId, userId);
        var visibleReplies = replies.findVisibleByWorryId(worryId);
        Map<Long, ReplyExtras> byId = extras.replyExtras(worryId, userId).stream()
                .collect(Collectors.toMap(ReplyExtras::replyId, Function.identity()));
        var items = visibleReplies.stream().map(reply -> {
            ReplyExtras data = byId.get(reply.getId());
            FollowUp followUp = data == null || data.followUpContent() == null
                    || (!mine && !"PUBLIC".equals(data.followUpVisibility())) ? null
                    : new FollowUp(data.followUpContent(), data.followUpVisibility(), data.followUpCreatedAt());
            return new ReplyItem(reply.getId(), reply.getType(), reply.getContent(), reply.getImageUrl(),
                    person(reply.getSenior()), data == null ? 0 : data.likeCount(), data != null && data.likedByMe(),
                    mine && reply.getFirstViewedAt() == null, followUp, reply.getCreatedAt());
        }).toList();
        return new WorryDetailResponse(worry.getId(), worry.getTitle(), worry.getContent(), worry.getTag(),
                worry.getVisibility(), person(worry.getAuthor()), mine, mine && !worry.isHidden() && items.isEmpty(),
                worry.isHidden(), likes.count(), likes.mine(), items, worry.getCreatedAt());
    }

    private Person person(User user) {
        return new Person(user.getNickname(), user.getAgeGroup());
    }
}
