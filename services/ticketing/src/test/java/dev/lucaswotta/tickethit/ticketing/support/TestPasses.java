package dev.lucaswotta.tickethit.ticketing.support;

import com.nimbusds.jose.JOSEException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Emite passes como a queue emite, com um par EC P-256 só para testes (o mesmo
 * dos testes da queue). Fora dos testes, o par vem de infra/generate-env.sh.
 */
public final class TestPasses {

    public static final String PRIVATE_KEY = "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgFWMmgv0lT2v6NDCp5SRTb7/IHH5lx9cPSDdOVYocztmhRANCAAQIUQvMOwYM131/SpYKhlF9Nz4ta7OFTf6zn9EO+dSNF0CzB2T2m1Qb/7FWSPE7swOz9TmxQ40pXpdqurILkNTL";

    public static final String PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAECFELzDsGDNd9f0qWCoZRfTc+LWuzhU3+s5/RDvnUjRdAswdk9ptUG/+xVkjxO7MDs/U5sUONKV6XarqyC5DUyw==";

    private TestPasses() {}

    public static String issue(UUID saleId, String customerId, Instant issuedAt, Instant expiresAt) {
        return issue(claims(saleId, customerId, issuedAt, expiresAt).build());
    }

    public static JwtClaimsSet.Builder claims(UUID saleId, String customerId, Instant issuedAt, Instant expiresAt) {
        return JwtClaimsSet.builder()
                .issuer("ticket-hit-queue")
                .audience(List.of("ticketing"))
                .subject(customerId)
                .claim("sid", saleId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt);
    }

    public static String issue(JwtClaimsSet claims) {
        return encoder(PUBLIC_KEY, PRIVATE_KEY)
                .encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.ES256).build(), claims))
                .getTokenValue();
    }

    public static JwtEncoder encoder(String publicKey, String privateKey) {
        try {
            var keys = KeyFactory.getInstance("EC");
            var ecPublic = (ECPublicKey) keys.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey)));
            var ecPrivate =
                    (ECPrivateKey) keys.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey)));
            return NimbusJwtEncoder.withKeyPair(ecPublic, ecPrivate)
                    .jwkPostProcessor(jwk -> {
                        try {
                            jwk.keyIDFromThumbprint();
                        } catch (JOSEException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
