package com.ganzithon.wool.user.repository;

import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.entity.SanctionLevel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserRepository extends JpaRepository<User, String> {

    boolean existsByNickname(String nickname);

    // 닉네임을 바꿀 때 자기 자신은 제외하고 중복을 확인한다. (내 닉네임 그대로, 또는 대소문자만 바꾸는 경우를 막지 않기 위해)
    boolean existsByNicknameAndIdNot(String nickname, String id);

    // 7일 제한 기간이 지난 어르신의 id를 afterId 다음부터 id 순서로 가져온다. 잠그지 않고 읽는 후보 목록이라서, 풀기 전에 잠금을 잡고 다시 확인한다.
    // id 커서로 이어 읽어서, 해제에 실패한 사람이 앞쪽을 계속 차지해도 뒤쪽 사람이 밀리지 않는다.
    @Query("select u.id from User u where u.sanctionLevel = :level and u.restrictedUntil <= :now and u.id > :afterId order by u.id")
    List<String> findIdsRestrictedUntilBefore(@Param("level") SanctionLevel level, @Param("now") LocalDateTime now,
                                              @Param("afterId") String afterId, Pageable pageable);
}
