package com.uteexpress.identity.repository;

import com.uteexpress.identity.entity.OtpPurpose;
import com.uteexpress.identity.entity.OtpTokenEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpTokenEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpTokenEntity> findFirstByUser_IdAndPurposeOrderBySentAtDescIdDesc(
            Long userId, OtpPurpose purpose);
}
