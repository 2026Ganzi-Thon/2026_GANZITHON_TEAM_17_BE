package com.ganzithon.wool.feedback.entity;

import com.ganzithon.wool.global.entity.BaseCreatedEntity;
import com.ganzithon.wool.reply.entity.Reply;
import com.ganzithon.wool.worry.entity.WorryVisibility;
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
@Table(name = "follow_ups", uniqueConstraints = @UniqueConstraint(name = "uk_follow_ups_reply", columnNames = "reply_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FollowUp extends BaseCreatedEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "reply_id", nullable = false) private Reply reply;
    @Column(nullable = false, length = 1000) private String content;
    @Enumerated(EnumType.STRING) @Column(nullable = false, columnDefinition = "ENUM('SENIORS_ONLY', 'PUBLIC')") private WorryVisibility visibility;
    @Column(name = "read_by_senior", nullable = false) private boolean readBySenior;
    public void makeSeniorsOnly() {
        this.visibility = WorryVisibility.SENIORS_ONLY;
    }

    public static FollowUp of(Reply reply, String content, WorryVisibility visibility) {
        FollowUp result = new FollowUp();
        result.reply = reply; result.content = content; result.visibility = visibility;
        return result;
    }
}
