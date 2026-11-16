package com.ganzithon.wool.feedback.entity;

import com.ganzithon.wool.global.entity.BaseCreatedEntity;
import com.ganzithon.wool.reply.entity.Reply;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "thanks", uniqueConstraints = @UniqueConstraint(name = "uk_thanks_reply", columnNames = "reply_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Thanks extends BaseCreatedEntity {
    public static final int MESSAGE_MAX_LENGTH = 255;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reply_id", nullable = false, updatable = false)
    private Reply reply;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private ThanksStamp stamp;

    @Column(length = MESSAGE_MAX_LENGTH, updatable = false)
    private String message;

    @Column(name = "read_by_senior", nullable = false)
    private boolean readBySenior;

    public static Thanks of(Reply reply, ThanksStamp stamp, String message) {
        Thanks thanks = new Thanks();
        thanks.reply = reply;
        thanks.stamp = stamp;
        thanks.message = message;
        return thanks;
    }
}
