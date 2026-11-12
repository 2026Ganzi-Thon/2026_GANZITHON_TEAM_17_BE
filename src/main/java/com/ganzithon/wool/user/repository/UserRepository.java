package com.ganzithon.wool.user.repository;

import com.ganzithon.wool.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

    boolean existsByNickname(String nickname);

    // 닉네임을 바꿀 때 자기 자신은 제외하고 중복을 확인한다. (내 닉네임 그대로, 또는 대소문자만 바꾸는 경우를 막지 않기 위해)
    boolean existsByNicknameAndIdNot(String nickname, String id);
}
