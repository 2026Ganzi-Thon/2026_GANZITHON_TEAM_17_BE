package com.ganzithon.wool.worry.repository;

import com.ganzithon.wool.worry.entity.Worry;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WorryDetailRepository extends Repository<Worry, Long> {
    @Query("select w from Worry w join fetch w.author where w.id = :id and w.deletedAt is null")
    Optional<Worry> findVisibleCandidate(@Param("id") Long id);
}
