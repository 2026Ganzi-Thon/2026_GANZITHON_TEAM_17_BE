package com.ganzithon.wool.certificate.response;

import com.ganzithon.wool.certificate.entity.Certificate;

import java.time.LocalDateTime;

// WOOL-004. 감사장 이미지·PDF는 서버가 만들지 않고 프론트가 이 값으로 화면의 감사장 카드를 그려 저장한다.
// nickname은 발급 때가 아니라 지금의 닉네임이다. (닉네임을 바꾸면 이미 쓴 글에도 바뀐 닉네임이 표시된다. POL-MBM-05)
public record CertificateDetailResponse(Long certificateId, String nickname, int replyCount, LocalDateTime issuedAt) {

    public static CertificateDetailResponse from(Certificate certificate) {
        return new CertificateDetailResponse(certificate.getId(), certificate.getSenior().getNickname(),
                certificate.getReplyCount(), certificate.getIssuedAt());
    }
}
