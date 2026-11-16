package com.bank.origination.api.security;

import java.util.Collection;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import java.util.Base64;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/api/v1/products", "/api/v1/local-tokens").permitAll()
                        .requestMatchers("/api/v1/workbench/**").hasAnyRole("UNDERWRITER", "UNDERWRITER_SUPERVISOR")
                        .requestMatchers("/api/v1/ops/**").hasRole("OPERATIONS")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter())))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${app.security.jwt-secret-base64}") String secret,
            @Value("${app.security.jwt-issuer}") String issuer,
            @Value("${app.security.jwt-audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(secret)).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Required audience is missing.", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer), audienceValidator));
        return decoder;
    }

    @Bean
    JwtEncoder jwtEncoder(@Value("${app.security.jwt-secret-base64}") String secret) {
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
            Object rolesClaim = jwt.getClaims().getOrDefault("roles", List.of());
            Collection<?> roles = rolesClaim instanceof Collection<?> values ? values : List.of();
            Collection<GrantedAuthority> authorities = roles.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .map(GrantedAuthority.class::cast)
                    .toList();
            String name = jwt.getSubject() == null ? "unknown" : jwt.getSubject();
            return new JwtAuthenticationToken(jwt, authorities, name);
        };
    }
}
