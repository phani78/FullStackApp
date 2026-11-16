package com.bank.origination.batch;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class BatchSecurityConfiguration {
    @Bean
    SecurityFilterChain batchSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        .anyRequest().hasRole("OPERATIONS"))
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter())))
                .build();
    }

    @Bean
    JwtDecoder batchJwtDecoder(
            @Value("${app.security.jwt-secret-base64}") String secret,
            @Value("${app.security.jwt-issuer}") String issuer,
            @Value("${app.security.jwt-audience}") String audience) {
        SecretKey key = secretKey(secret);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required audience is missing.", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
        return decoder;
    }

    @Bean
    JwtEncoder batchJwtEncoder(@Value("${app.security.jwt-secret-base64}") String secret) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey(secret).getEncoded()));
    }

    private SecretKey secretKey(String secret) {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET_BASE64 must decode to at least 32 bytes.");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    private Converter<Jwt, ? extends AbstractAuthenticationToken> roleConverter() {
        return jwt -> {
            Object claim = jwt.getClaims().getOrDefault("roles", List.of());
            Collection<?> roles = claim instanceof Collection<?> values ? values : List.of();
            Collection<GrantedAuthority> authorities = roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .map(GrantedAuthority.class::cast)
                    .toList();
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }
}
