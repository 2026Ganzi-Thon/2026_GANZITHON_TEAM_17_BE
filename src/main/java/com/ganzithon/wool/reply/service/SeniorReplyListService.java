package com.ganzithon.wool.reply.service;

import com.ganzithon.wool.reply.entity.ReplyType;
import com.ganzithon.wool.reply.repository.SeniorReplyListRepository;
import com.ganzithon.wool.reply.repository.SeniorReplyListRepository.Row;
import com.ganzithon.wool.reply.response.SeniorReplyListResponse;
import com.ganzithon.wool.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 내가 보낸 답장 목록(REPLY-006). 읽기만 하므로 잠그지 않는다.
// 고민이 삭제됐거나 신고로 숨겨졌어도 답장과 털 기록은 남으므로 목록에는 "삭제된 고민"으로 보여주고,
// 그 고민에 달린 공감·고마움·후일담은 보여주지 않는다. (POL-WRY-09)
@Service
@RequiredArgsConstructor
public class SeniorReplyListService {

    static final String UNAVAILABLE_WORRY_TITLE = "삭제된 고민";

    private final SeniorReplyListRepository seniorReplyListRepository;

    @Transactional(readOnly = true)
    public SeniorReplyListResponse list(User senior) {
        return new SeniorReplyListResponse(seniorReplyListRepository.findBySeniorId(senior.getId()).stream()
                .map(SeniorReplyListService::toItem)
                .toList());
    }

    private static SeniorReplyListResponse.Item toItem(Row row) {
        ReplyType type = ReplyType.valueOf(row.type());
        if (!row.worryVisible()) {
            return new SeniorReplyListResponse.Item(row.replyId(), row.worryId(), UNAVAILABLE_WORRY_TITLE, type,
                    0, false, false, row.createdAt());
        }
        return new SeniorReplyListResponse.Item(row.replyId(), row.worryId(), row.worryTitle(), type,
                row.likeCount(), row.thanked(), row.hasFollowUp(), row.createdAt());
    }
}
