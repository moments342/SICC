package com.moments.sicc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.sicc.api.ApiDtos.ErroResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {
    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, HttpStatus status, String mensagem) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getOutputStream(),
                new ErroResponse(status.value(), status.getReasonPhrase(), mensagem, LocalDateTime.now()));
    }
}
