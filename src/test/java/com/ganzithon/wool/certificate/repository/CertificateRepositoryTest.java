package com.ganzithon.wool.certificate.repository;

import com.ganzithon.wool.IntegrationTestSupport;
import com.ganzithon.wool.certificate.entity.Certificate;
import com.ganzithon.wool.user.entity.AgeGroup;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class CertificateRepositoryTest extends IntegrationTestSupport {

    private static final LocalDateTime ISSUED_AT = LocalDateTime.of(2026, 11, 13, 12, 0);

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CertificateRepository certificateRepository;

    private User senior;

    @BeforeEach
    void setUp() {
        senior = User.join(Role.SENIOR, AgeGroup.AGE_60S, "다정한 양 2");
        entityManager.persist(senior);
    }

    @Test
    void 감사장을_저장하고_다시_조회하면_어르신_기준_답장_수_발급_시각이_유지된다() {
        Certificate certificate = certificateRepository.saveAndFlush(Certificate.issue(senior, 20, ISSUED_AT));
        entityManager.clear();

        Certificate saved = certificateRepository.findById(certificate.getId()).orElseThrow();
        assertThat(saved.getSenior().getId()).isEqualTo(senior.getId());
        assertThat(saved.getReplyCount()).isEqualTo(20);
        assertThat(saved.getIssuedAt()).isEqualTo(ISSUED_AT);
        assertThat(saved.isOwnedBy(senior.getId())).isTrue();
        assertThat(saved.isOwnedBy("other")).isFalse();
    }

    @Test
    void 같은_어르신에게_같은_기준의_감사장을_두_번_발급하면_거부된다() {
        certificateRepository.saveAndFlush(Certificate.issue(senior, 20, ISSUED_AT));

        assertThatThrownBy(() -> certificateRepository.saveAndFlush(Certificate.issue(senior, 20, ISSUED_AT.plusDays(1))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 기준이_다르면_같은_어르신에게_더_발급할_수_있다() {
        certificateRepository.saveAndFlush(Certificate.issue(senior, 20, ISSUED_AT));
        certificateRepository.saveAndFlush(Certificate.issue(senior, 40, ISSUED_AT.plusDays(10)));

        assertThat(certificateRepository.existsBySeniorIdAndReplyCount(senior.getId(), 20)).isTrue();
        assertThat(certificateRepository.existsBySeniorIdAndReplyCount(senior.getId(), 40)).isTrue();
        assertThat(certificateRepository.existsBySeniorIdAndReplyCount(senior.getId(), 60)).isFalse();
    }

    @Test
    void 기준_답장_수가_20의_배수가_아니거나_값이_없으면_거부한다() {
        assertThatThrownBy(() -> Certificate.issue(senior, 0, ISSUED_AT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Certificate.issue(senior, 25, ISSUED_AT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Certificate.issue(senior, -20, ISSUED_AT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Certificate.issue(null, 20, ISSUED_AT)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Certificate.issue(senior, 20, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
