package com.ganzithon.wool.reply.entity;

import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.worry.entity.Worry;
import com.ganzithon.wool.worry.entity.WorryTag;
import com.ganzithon.wool.worry.entity.WorryVisibility;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplyTest {

    private final User youth = User.join(Role.YOUTH, AgeGroup.AGE_20S, "포근한 양 1");
    private final User senior = User.join(Role.SENIOR, AgeGroup.AGE_60S, "다정한 양 2");
    private final Worry worry = Worry.of(youth, "제목", "본문", WorryTag.CAREER, WorryVisibility.PUBLIC);

    @Test
    void 글_답장은_본문을_원문_그대로_가진다() {
        Reply reply = Reply.text(worry, senior, "  앞뒤 공백도 그대로  ");

        assertThat(reply.getType()).isEqualTo(ReplyType.TEXT);
        assertThat(reply.getContent()).isEqualTo("  앞뒤 공백도 그대로  ");
        assertThat(reply.getImageUrl()).isNull();
        assertThat(reply.isHidden()).isFalse();
        assertThat(reply.isVisible()).isTrue();
    }

    @Test
    void 말_답장은_본문을_가지고_타입이_VOICE이다() {
        Reply reply = Reply.voice(worry, senior, "말로 한 답장");

        assertThat(reply.getType()).isEqualTo(ReplyType.VOICE);
        assertThat(reply.getContent()).isEqualTo("말로 한 답장");
    }

    @Test
    void 손편지_답장은_이미지만_가지고_본문은_없다() {
        Reply reply = Reply.letter(worry, senior, "https://example.com/letter.png");

        assertThat(reply.getType()).isEqualTo(ReplyType.LETTER);
        assertThat(reply.getImageUrl()).isEqualTo("https://example.com/letter.png");
        assertThat(reply.getContent()).isNull();
    }

    @Test
    void 글과_말은_빈_본문이나_공백만_있는_본문을_거부한다() {
        assertThatThrownBy(() -> Reply.text(worry, senior, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reply.text(worry, senior, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reply.text(worry, senior, "  \n ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reply.voice(worry, senior, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 본문은_1000자까지_허용하고_이모지는_한_글자로_센다() {
        assertThat(Reply.text(worry, senior, "가".repeat(1000)).getContent()).hasSize(1000);
        assertThat(Reply.text(worry, senior, "😀".repeat(1000)).getContent()).isNotNull();
        assertThatThrownBy(() -> Reply.text(worry, senior, "가".repeat(1001)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reply.text(worry, senior, "😀".repeat(1001)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 손편지는_이미지_주소가_없거나_500자를_넘으면_거부한다() {
        assertThatThrownBy(() -> Reply.letter(worry, senior, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reply.letter(worry, senior, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reply.letter(worry, senior, "a".repeat(501)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Reply.letter(worry, senior, "a".repeat(500)).getImageUrl()).hasSize(500);
    }

    @Test
    void 고민이나_어르신이_없으면_거부한다() {
        assertThatThrownBy(() -> Reply.text(null, senior, "내용")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reply.text(worry, null, "내용")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Reply.letter(null, senior, "u")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 숨기거나_삭제하면_보이지_않는다() {
        Reply hidden = Reply.text(worry, senior, "내용");
        hidden.hide();
        Reply deleted = Reply.text(worry, senior, "내용");
        deleted.delete();

        assertThat(hidden.isHidden()).isTrue();
        assertThat(hidden.isVisible()).isFalse();
        assertThat(deleted.isVisible()).isFalse();
    }

    @Test
    void 최초_열람_시각과_리마인더_시각은_처음_값을_유지한다() {
        Reply reply = Reply.text(worry, senior, "내용");
        LocalDateTime first = LocalDateTime.of(2026, 11, 13, 9, 0);

        reply.markFirstViewed(first);
        reply.markFirstViewed(first.plusDays(1));
        reply.markFollowUpReminded(first.plusDays(3));
        reply.markFollowUpReminded(first.plusDays(4));

        assertThat(reply.getFirstViewedAt()).isEqualTo(first);
        assertThat(reply.getFollowUpRemindedAt()).isEqualTo(first.plusDays(3));
    }

    @Test
    void 작성자_여부는_어르신_ID로_판단한다() {
        Reply reply = Reply.text(worry, senior, "내용");

        assertThat(reply.isWrittenBy(senior.getId())).isTrue();
        assertThat(reply.isWrittenBy(youth.getId())).isFalse();
    }
}
