package com.ganzithon.wool.global.auth;

import com.ganzithon.wool.user.entity.Role;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 컨트롤러의 User 파라미터에 붙이면 X-User-Id 헤더로 사용자를 조회해 주입한다.
//   @LoginUser User user                  : 역할 상관없음
//   @LoginUser(Role.SENIOR) User user     : 어르신만 (아니면 USER_403_001)
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LoginUser {

    Role[] value() default {};
}
