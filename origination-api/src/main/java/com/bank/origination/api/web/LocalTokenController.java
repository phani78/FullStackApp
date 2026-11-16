package com.bank.origination.api.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import com.bank.origination.api.service.OriginationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/local-tokens")
@Profile("local")
public class LocalTokenController {
    private static final Set<String> ALLOWED_ROLES =
            Set.of("CUSTOMER", "UNDERWRITER", "UNDERWRITER_SUPERVISOR", "OPERATIONS", "AUDITOR");
    private final JwtEncoder jwtEncoder;

    public LocalTokenController(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    @PostMapping
    public TokenResponse issue(@Valid @RequestBody TokenRequest request) {
        if (!ALLOWED_ROLES.contains(request.role())) {
            throw new OriginationException(HttpStatus.BAD_REQUEST, "Unsupported local demonstration role.");
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("local-card-origination")
                .audience(List.of("card-origination"))
                .subject(request.subject())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("roles", List.of(request.role()))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, 3600);
    }

    public record TokenRequest(
            @NotBlank @Email String subject,
            @NotBlank @Pattern(regexp = "CUSTOMER|UNDERWRITER|UNDERWRITER_SUPERVISOR|OPERATIONS|AUDITOR") String role) {}
    public record TokenResponse(String accessToken, long expiresInSeconds) {
        @Override
        public String toString() {
            return "TokenResponse[accessToken=REDACTED, expiresInSeconds=" + expiresInSeconds + "]";
        }
    }
}
