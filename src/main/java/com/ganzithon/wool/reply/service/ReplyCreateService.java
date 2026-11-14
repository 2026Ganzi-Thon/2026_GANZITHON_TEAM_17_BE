package com.ganzithon.wool.reply.service;

import com.ganzithon.wool.certificate.entity.Certificate;
import com.ganzithon.wool.certificate.repository.CertificateRepository;
import com.ganzithon.wool.filter.service.ContentFilter;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.global.exception.CommonErrorCode;
import com.ganzithon.wool.global.storage.FileStorage;
import com.ganzithon.wool.notification.entity.NotificationType;
import com.ganzithon.wool.notification.entity.TargetKind;
import com.ganzithon.wool.notification.service.NotificationService;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.reply.entity.ReplyType;
import com.ganzithon.wool.reply.entity.WoolStage;
import com.ganzithon.wool.reply.exception.ReplyErrorCode;
import com.ganzithon.wool.reply.repository.ReplyRepository;
import com.ganzithon.wool.reply.request.CreateReplyRequest;
import com.ganzithon.wool.reply.response.CreateReplyResponse;
import com.ganzithon.wool.reply.response.RestrictedData;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.exception.WorryErrorCode;
import com.ganzithon.wool.worry.repository.WorryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 답장 작성(REPLY-005). 답장 저장, 청년 알림, 감사장 발급과 그 알림이 한 트랜잭션이라 함께 커밋되거나 함께 롤백된다.
@Service
@RequiredArgsConstructor
public class ReplyCreateService {

    private final WorryRepository worryRepository;
    private final SeniorLock seniorLock;
    private final ReplyRepository replyRepository;
    private final CertificateRepository certificateRepository;
    private final ContentFilter contentFilter;
    private final FileStorage fileStorage;
    private final NotificationService notificationService;

    // requester는 @LoginUser가 요청 시작 때 읽은 사용자다. 제재 상태가 그 사이 바뀌었을 수 있어서 id만 쓰고,
    // 잠금을 잡은 뒤 다시 읽은 최신 상태(senior)로 판단한다.
    // 어르신의 누적 답장 수와 감사장 기준을 잠금을 잡은 뒤에 읽으므로 READ COMMITTED로 실행한다.
    // MySQL 기본(REPEATABLE READ)에서는 트랜잭션의 첫 일반 조회가 그 시점의 스냅샷을 만들어서, 잠금을 기다린 뒤에도
    // 다른 트랜잭션이 그 사이 커밋한 답장·신고를 못 보는 오래된 수를 읽을 수 있다. READ COMMITTED는 조회마다 최신 커밋을 본다.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CreateReplyResponse create(User requester, Long worryId, CreateReplyRequest request) {
        // 손편지는 업로드 API가 실제로 저장한 사진만 받는다. 형식만 맞는 가짜 주소는 잠금을 잡기 전에 돌려보낸다.
        if (request.type() == ReplyType.LETTER && !fileStorage.exists(request.imageUrl())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }

        // 고민 수정(WorryUpdateTransaction)과 같은 고민 잠금을 써서, 수정과 답장 등록이 동시에 들어와도 어긋나지 않게 한다.
        Worry worry = worryRepository.findByIdAndDeletedAtIsNull(worryId)
                .orElseThrow(() -> new BusinessException(WorryErrorCode.NOT_FOUND));
        if (worry.isHidden()) {
            throw new BusinessException(WorryErrorCode.NOT_FOUND);
        }
        // 누적 답장 수 계산과 감사장 발급이 같은 어르신 기준으로 한 번에 하나씩 처리되게 한다.
        User senior = seniorLock.lock(requester.getId());
        // 잠금을 기다리는 동안 7일 제한이 풀렸을 수 있어서, 시각은 잠금을 잡은 뒤에 구한다.
        LocalDateTime now = LocalDateTime.now();
        if (senior.isReplyRestrictedAt(now)) {
            // 영구 제한이면 restrictedUntil이 null이다.
            throw new BusinessException(ReplyErrorCode.RESTRICTED, new RestrictedData(senior.getRestrictedUntil()));
        }

        // 삭제·숨김 답장도 1회로 친다.
        if (replyRepository.existsByWorryIdAndSeniorId(worryId, senior.getId())) {
            throw new BusinessException(ReplyErrorCode.ALREADY_REPLIED);
        }
        if (request.type() != ReplyType.LETTER) {
            contentFilter.validateLongText(request.content());
        }

        Reply reply = replyRepository.saveAndFlush(newReply(worry, senior, request));
        notificationService.send(worry.getAuthor().getId(), NotificationType.REPLY_ARRIVED,
                senior.getNickname() + " 선배님이 답장을 보냈어요", null, TargetKind.REPLY, reply.getId());

        long totalReplies = replyRepository.countVisibleBySeniorId(senior.getId());
        Long certificateId = issueCertificateIfDue(senior, totalReplies, now);
        return new CreateReplyResponse(reply.getId(), totalReplies, WoolStage.of(totalReplies),
                certificateId != null, certificateId);
    }

    private Reply newReply(Worry worry, User senior, CreateReplyRequest request) {
        return switch (request.type()) {
            case TEXT -> Reply.text(worry, senior, request.content());
            case VOICE -> Reply.voice(worry, senior, request.content());
            case LETTER -> Reply.letter(worry, senior, request.imageUrl());
        };
    }

    // 누적 답장이 20의 배수가 될 때마다 한 장씩 발급한다. 숨김·삭제로 수가 줄었다가 다시 같은 수가 되어도 이미 발급한 감사장은 다시 만들지 않는다.
    private Long issueCertificateIfDue(User senior, long totalReplies, LocalDateTime now) {
        if (totalReplies % Certificate.REPLY_COUNT_UNIT != 0
                || certificateRepository.existsBySeniorIdAndReplyCount(senior.getId(), (int) totalReplies)) {
            return null;
        }
        Certificate certificate = certificateRepository.save(Certificate.issue(senior, (int) totalReplies, now));
        notificationService.send(senior.getId(), NotificationType.CERTIFICATE_ISSUED, "감사장이 도착했어요",
                totalReplies + "통의 답장을 전해 주셔서 고맙습니다.", TargetKind.CERTIFICATE, certificate.getId());
        return certificate.getId();
    }
}
