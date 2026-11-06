package com.ganzithon.wool.global.auth;

import com.ganzithon.wool.global.config.WebConfig;
import com.ganzithon.wool.global.exception.BusinessException;
import com.ganzithon.wool.global.exception.CommonErrorCode;
import com.ganzithon.wool.user.entity.Role;
import com.ganzithon.wool.user.entity.User;
import com.ganzithon.wool.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class LoginUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginUser.class)
                && User.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        String userId = webRequest.getHeader(WebConfig.USER_ID_HEADER);
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(CommonErrorCode.UNREGISTERED_USER);
        }

        User user = userRepository.findById(userId.trim())
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNREGISTERED_USER));

        Role[] allowedRoles = parameter.getParameterAnnotation(LoginUser.class).value();
        if (allowedRoles.length > 0 && !Arrays.asList(allowedRoles).contains(user.getRole())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN_ROLE);
        }
        return user;
    }
}
