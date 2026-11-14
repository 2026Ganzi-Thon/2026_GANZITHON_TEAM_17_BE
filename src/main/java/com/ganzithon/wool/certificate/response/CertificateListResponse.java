package com.ganzithon.wool.certificate.response;

import com.ganzithon.wool.certificate.entity.Certificate;

import java.time.LocalDateTime;
import java.util.List;

// WOOL-003. 발급일 최신순이다. 감사장이 없으면 items는 빈 배열이다.
public record CertificateListResponse(List<Item> items) {

    public record Item(Long certificateId, int replyCount, LocalDateTime issuedAt) {

        public static Item from(Certificate certificate) {
            return new Item(certificate.getId(), certificate.getReplyCount(), certificate.getIssuedAt());
        }
    }
}
