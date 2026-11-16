package com.ganzithon.wool.wool.service;

import com.ganzithon.wool.feedback.entity.ThanksStamp;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.wool.repository.ReceivedHeartRepository;
import com.ganzithon.wool.wool.repository.ReceivedHeartRepository.Row;
import com.ganzithon.wool.wool.response.ReceivedHeartListResponse;
import com.ganzithon.wool.wool.response.ReceivedHeartListResponse.Kind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

// 받은 마음 목록(WOOL-002). 조회하면 목록에 담아 보낸 마음이 읽음 처리된다. (어르신 홈의 안 읽은 수가 줄어든다)
// 읽기 요청이 값을 바꾸므로 읽기 전용 트랜잭션이 아니다. 목록과 읽음 표시가 한 트랜잭션이고,
// 읽음 표시는 보낸 id만 바꾼다. (조회하는 사이 새로 도착한 마음은 안 읽은 채로 남는다)
@Service
@RequiredArgsConstructor
public class ReceivedHeartService {

    private final ReceivedHeartRepository receivedHeartRepository;

    @Transactional
    public ReceivedHeartListResponse list(User senior) {
        List<Row> rows = receivedHeartRepository.findBySeniorId(senior.getId());

        List<Long> thanksIds = new ArrayList<>();
        List<Long> followUpIds = new ArrayList<>();
        for (Row row : rows) {
            (Kind.valueOf(row.kind()) == Kind.THANKS ? thanksIds : followUpIds).add(row.itemId());
        }
        receivedHeartRepository.markRead(thanksIds, followUpIds);

        return new ReceivedHeartListResponse(rows.stream().map(ReceivedHeartService::toItem).toList());
    }

    private static ReceivedHeartListResponse.Item toItem(Row row) {
        ThanksStamp stamp = row.stamp() == null ? null : ThanksStamp.valueOf(row.stamp());
        return new ReceivedHeartListResponse.Item(Kind.valueOf(row.kind()), row.replyId(), row.worryTitle(), stamp,
                row.content(), row.createdAt());
    }
}
