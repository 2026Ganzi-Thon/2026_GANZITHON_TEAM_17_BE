package com.ganzithon.wool.worry.repository;

import com.ganzithon.wool.worry.entity.Worry;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface WorryRepository extends JpaRepository<Worry, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Worry> findByIdAndDeletedAtIsNull(Long id);
}
