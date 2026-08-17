package com.moments.sicc.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moments.sicc.domain.UsuarioInterno;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final long expirationSeconds;

    public JwtService(ObjectMapper objectMapper,
            @Value("${sicc.jwt.secret}") String secret,
            @Value("${sicc.jwt.expiration-seconds:28800}") long expirationSeconds) {
        this.objectMapper = objectMapper;
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException(
                    "SICC_JWT_SECRET deve conter ao menos 32 bytes para assinar tokens com segurança.");
        }
        this.secret = secretBytes;
        this.expirationSeconds = expirationSeconds;
    }

    public String gerar(UsuarioInterno usuario) {
        try {
            String header = encode(objectMapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", usuario.getLogin());
            payload.put("perfil", usuario.getPerfil().name());
            payload.put("temp", usuario.isSenhaTemporaria());
            payload.put("ver", usuario.getVersaoAcesso());
            payload.put("exp", Instant.now().plusSeconds(expirationSeconds).getEpochSecond());
            String body = encode(objectMapper.writeValueAsBytes(payload));
            String unsigned = header + "." + body;
            return unsigned + "." + encode(assinar(unsigned));
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível gerar o token.", e);
        }
    }

    public Claims validar(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException("Token inválido.");
            byte[] expected = assinar(parts[0] + "." + parts[1]);
            byte[] informed = Base64.getUrlDecoder().decode(parts[2]);
            if (!java.security.MessageDigest.isEqual(expected, informed)) {
                throw new IllegalArgumentException("Token inválido.");
            }
            Map<String, Object> payload = objectMapper.readValue(
                    Base64.getUrlDecoder().decode(parts[1]), new TypeReference<>() {});
            if (!(payload.get("exp") instanceof Number expValue)
                    || !(payload.get("ver") instanceof Number versaoValue)
                    || !(payload.get("sub") instanceof String login)
                    || !(payload.get("perfil") instanceof String perfil)
                    || !(payload.get("temp") instanceof Boolean senhaTemporaria)) {
                throw new IllegalArgumentException("Token inválido.");
            }
            long exp = expValue.longValue();
            if (Instant.now().getEpochSecond() >= exp) throw new IllegalArgumentException("Token expirado.");
            return new Claims(login, perfil, senhaTemporaria, versaoValue.longValue());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Token inválido.", e);
        }
    }

    private byte[] assinar(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public record Claims(String login, String perfil, boolean senhaTemporaria, long versaoAcesso) {}
}
