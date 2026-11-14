package com.ganzithon.wool.reply.entity;

import com.ganzithon.wool.global.entity.BaseSoftDeleteEntity;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.worry.entity.Worry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 어르신 한 명은 같은 고민에 한 번만 답장할 수 있다. 삭제·숨김 처리된 답장도 그 한 번에 포함된다. (uk_replies_worry_senior)
@Entity
@Table(name = "replies",
        uniqueConstraints = @UniqueConstraint(name = "uk_replies_worry_senior", columnNames = {"worry_id", "senior_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reply extends BaseSoftDeleteEntity {

    public static final int CONTENT_MAX_LENGTH = 1000;
    public static final int IMAGE_URL_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worry_id", nullable = false, updatable = false)
    private Worry worry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "senior_id", nullable = false, updatable = false)
    private User senior;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, columnDefinition = "ENUM('TEXT', 'VOICE', 'LETTER')")
    private ReplyType type;

    // TEXT/VOICE 본문. 사람이 쓴 글이라 다듬지 않고 그대로 저장한다. LETTER는 null.
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    // LETTER 이미지. TEXT/VOICE는 null.
    @Column(name = "image_url", length = IMAGE_URL_MAX_LENGTH)
    private String imageUrl;

    // 청년이 처음 열어본 시각. 안 읽은 답장 표시에 쓴다.
    @Column(name = "first_viewed_at")
    private LocalDateTime firstViewedAt;

    // 후일담 리마인더를 보낸 시각. 한 번만 보내기 위한 표시다.
    @Column(name = "follow_up_reminded_at")
    private LocalDateTime followUpRemindedAt;

    // 신고로 숨김. 영구이며 해제하지 않는다.
    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    private Reply(Worry worry, User senior, ReplyType type, String content, String imageUrl) {
        this.worry = worry;
        this.senior = senior;
        this.type = type;
        this.content = content;
        this.imageUrl = imageUrl;
    }

    public static Reply text(Worry worry, User senior, String content) {
        return ofWritten(worry, senior, ReplyType.TEXT, content);
    }

    public static Reply voice(Worry worry, User senior, String content) {
        return ofWritten(worry, senior, ReplyType.VOICE, content);
    }

    public static Reply letter(Worry worry, User senior, String imageUrl) {
        requireParticipants(worry, senior);
        if (imageUrl == null || imageUrl.isBlank() || imageUrl.codePointCount(0, imageUrl.length()) > IMAGE_URL_MAX_LENGTH) {
            throw new IllegalArgumentException("손편지 이미지 주소는 1자 이상 " + IMAGE_URL_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return new Reply(worry, senior, ReplyType.LETTER, null, imageUrl);
    }

    private static Reply ofWritten(Worry worry, User senior, ReplyType type, String content) {
        requireParticipants(worry, senior);
        // 글자 수는 문자(코드포인트) 단위로 센다. 이모지가 둘로 세어지지 않게 String.length()를 쓰지 않는다.
        if (content == null || content.isBlank() || content.codePointCount(0, content.length()) > CONTENT_MAX_LENGTH) {
            throw new IllegalArgumentException("답장은 1자 이상 " + CONTENT_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return new Reply(worry, senior, type, content, null);
    }

    private static void requireParticipants(Worry worry, User senior) {
        if (worry == null || senior == null) {
            throw new IllegalArgumentException("고민과 답장한 어르신은 필수입니다.");
        }
    }

    // 사용자에게 보이는 답장인가. ReplyRepository의 "유효 답장" 조건과 같은 정의다.
    public boolean isVisible() {
        return !hidden && !isDeleted();
    }

    public void hide() {
        this.hidden = true;
    }

    // 이미 열어본 답장에 다시 호출해도 최초 시각을 유지한다.
    public void markFirstViewed(LocalDateTime viewedAt) {
        if (this.firstViewedAt == null) {
            this.firstViewedAt = viewedAt;
        }
    }

    // 이미 보낸 리마인더는 다시 보내지 않으므로 최초 시각을 유지한다.
    public void markFollowUpReminded(LocalDateTime remindedAt) {
        if (this.followUpRemindedAt == null) {
            this.followUpRemindedAt = remindedAt;
        }
    }

    public boolean isWrittenBy(String userId) {
        return senior.getId().equals(userId);
    }
}
