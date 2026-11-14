package com.ganzithon.wool.global.config;

import com.ganzithon.wool.global.storage.LocalFileStorage;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 업로드한 파일(/files/**) 응답에 nosniff를 붙인다. 브라우저가 파일 내용을 보고 종류를 멋대로 바꿔 해석(이미지로 올린 파일을
// 스크립트나 문서로 실행)하지 못하게, 서버가 정한 Content-Type(확장자 기준 image/*)만 따르게 한다.
@Component
public class FileResponseHeaderFilter extends OncePerRequestFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(LocalFileStorage.PUBLIC_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        chain.doFilter(request, response);
    }
}
