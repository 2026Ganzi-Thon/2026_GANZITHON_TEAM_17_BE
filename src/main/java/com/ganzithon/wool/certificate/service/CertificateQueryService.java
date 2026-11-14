package com.ganzithon.wool.certificate.service;

import com.ganzithon.wool.certificate.entity.Certificate;
import com.ganzithon.wool.certificate.exception.CertificateErrorCode;
import com.ganzithon.wool.certificate.repository.CertificateRepository;
import com.ganzithon.wool.certificate.response.CertificateDetailResponse;
import com.ganzithon.wool.certificate.response.CertificateListResponse;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 감사장 목록(WOOL-003)과 상세(WOOL-004) 조회. 읽기만 하고, 본인의 감사장만 볼 수 있다.
@Service
@RequiredArgsConstructor
public class CertificateQueryService {

    private final CertificateRepository certificateRepository;

    @Transactional(readOnly = true)
    public CertificateListResponse list(User senior) {
        return new CertificateListResponse(certificateRepository.findBySeniorIdOrderByIssuedAtDescIdDesc(senior.getId())
                .stream().map(CertificateListResponse.Item::from).toList());
    }

    // 없는 감사장은 404, 남의 감사장은 403이다. 존재 여부를 먼저 확인한다.
    @Transactional(readOnly = true)
    public CertificateDetailResponse detail(User senior, Long certificateId) {
        Certificate certificate = certificateRepository.findById(certificateId)
                .orElseThrow(() -> new BusinessException(CertificateErrorCode.NOT_FOUND));
        if (!certificate.isOwnedBy(senior.getId())) {
            throw new BusinessException(CertificateErrorCode.FORBIDDEN);
        }
        return CertificateDetailResponse.from(certificate);
    }
}
