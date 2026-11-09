package com.ganzithon.wool.worry.repository;

import com.ganzithon.wool.worry.entity.Worry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorryRepository extends JpaRepository<Worry, Long> {
}
