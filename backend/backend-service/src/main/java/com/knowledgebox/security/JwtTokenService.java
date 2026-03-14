package com.knowledgebox.security;

import com.knowledgebox.config.KnowledgeBoxProperties;
import com.knowledgebox.domain.user.UserAccount;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final KnowledgeBoxProperties properties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, KnowledgeBoxProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.properties = properties;
    }

    public IssuedToken issue(UserAccount userAccount) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(properties.getAuth().getTokenTtl());
        JwtClaimsSet claimsSet = JwtClaimsSet.builder()
                .issuer(properties.getAuth().getIssuer())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(String.valueOf(userAccount.getId()))
                .claim("email", userAccount.getEmail())
                .claim("roles", List.of("ROLE_USER"))
                .build();

        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claimsSet
        )).getTokenValue();

        return new IssuedToken(tokenValue, expiresAt);
    }

    public CurrentUser parse(String tokenValue) {
        Jwt jwt = jwtDecoder.decode(tokenValue);
        return new CurrentUser(Long.parseLong(jwt.getSubject()), jwt.getClaimAsString("email"));
    }

    public record IssuedToken(
            String token,
            Instant expiresAt
    ) {
    }
}
