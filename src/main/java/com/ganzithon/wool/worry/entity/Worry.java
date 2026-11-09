package com.ganzithon.wool.worry.entity;

import com.ganzithon.wool.global.entity.BaseSoftDeleteEntity;
import com.ganzithon.wool.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "worries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Worry extends BaseSoftDeleteEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false, updatable = false)
    private User author;

    @Column(nullable = false, length = 40)
    private String title;

    @Column(nullable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('CAREER', 'LOVE', 'FAMILY', 'RELATIONSHIP', 'PARENTING', 'LIVING', 'MIND')")
    private WorryTag tag;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('SENIORS_ONLY', 'PUBLIC')")
    private WorryVisibility visibility;

    @Column(nullable = false)
    private boolean hidden;

    private Worry(User author, String title, String content, WorryTag tag, WorryVisibility visibility) {
        this.author = author;
        this.title = title;
        this.content = content;
        this.tag = tag;
        this.visibility = visibility;
    }

    public static Worry of(User author, String title, String content, WorryTag tag, WorryVisibility visibility) {
        return new Worry(author, title, content, tag, visibility);
    }
}
