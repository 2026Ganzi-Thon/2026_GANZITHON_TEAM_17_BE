package com.ganzithon.wool.reply.service;

import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.repository.SeniorWorryRepository;
import com.ganzithon.wool.reply.repository.SeniorWorryRepository.WorryRow;
import com.ganzithon.wool.reply.response.SeniorWorryDetailResponse;
import com.ganzithon.wool.reply.response.SeniorWorryDetailResponse.OtherReply;
import com.ganzithon.wool.reply.response.SeniorWorryListResponse;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import com.ganzithon.wool.worry.exception.WorryErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

// 어르신이 고민을 고르고(REPLY-002) 읽는(REPLY-009) 조회. 읽기만 하므로 고민 행을 잠그지 않는다.
@Service
@RequiredArgsConstructor
public class SeniorWorryService {

    // 답장 0통인 고민이 먼저, 그 안에서 각각 최신순이다. (POL-RPL-07) 작성 시각이 같으면 나중에 쓴 글이 먼저다.
    private static final Comparator<SeniorWorryListResponse.Item> ORDER = Comparator
            .comparing((SeniorWorryListResponse.Item item) -> item.replyCount() > 0)
            .thenComparing(SeniorWorryListResponse.Item::createdAt, Comparator.reverseOrder())
            .thenComparing(SeniorWorryListResponse.Item::worryId, Comparator.reverseOrder());

    private final SeniorWorryRepository seniorWorryRepository;
    private final ReplyRepository replyRepository;

    @Transactional(readOnly = true)
    public SeniorWorryListResponse list(User senior) {
        List<SeniorWorryListResponse.Item> items = seniorWorryRepository.findReadableRows(senior.getId()).stream()
                .map(SeniorWorryService::toItem)
                .sorted(ORDER)
                .toList();
        long waitingCount = items.stream().filter(item -> item.replyCount() == 0).count();
        return new SeniorWorryListResponse(waitingCount, items);
    }

    private static SeniorWorryListResponse.Item toItem(WorryRow row) {
        return new SeniorWorryListResponse.Item(row.getWorryId(), row.getTitle(), row.getTag(),
                row.getAuthorAgeGroup(), row.getVisibility(), row.getReplyCount(), row.getMyReplyCount() > 0,
                row.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public SeniorWorryDetailResponse detail(User senior, Long worryId) {
        Worry worry = seniorWorryRepository.findByIdAndDeletedAtIsNullAndHiddenFalse(worryId)
                .orElseThrow(() -> new BusinessException(WorryErrorCode.NOT_FOUND));

        boolean repliedByMe = replyRepository.existsByWorryIdAndSeniorId(worryId, senior.getId());
        // 제한은 읽기 화면의 안내용이다. 실제 답장을 막는 판단은 답장 작성(REPLY-005)이 잠금을 잡고 다시 한다.
        boolean restricted = senior.isReplyRestrictedAt(LocalDateTime.now());
        // 영구 제한이면 해제 시각이 없어서 null이다.
        LocalDateTime restrictedUntil = restricted ? senior.getRestrictedUntil() : null;

        List<OtherReply> otherReplies = worry.getVisibility() == WorryVisibility.PUBLIC
                ? replyRepository.findVisibleByWorryIdExcludingSenior(worryId, senior.getId()).stream()
                        .map(OtherReply::from).toList()
                : List.of();
        return SeniorWorryDetailResponse.of(worry, repliedByMe, !repliedByMe && !restricted, restrictedUntil, otherReplies);
    }
}
