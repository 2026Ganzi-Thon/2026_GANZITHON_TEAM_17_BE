package com.ganzithon.wool.user.repository;

import com.ganzithon.wool.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

    boolean existsByNickname(String nickname);
}
