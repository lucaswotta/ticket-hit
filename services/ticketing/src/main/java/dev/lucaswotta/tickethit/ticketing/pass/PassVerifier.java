package dev.lucaswotta.tickethit.ticketing.pass;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Confere o passe emitido pela queue só com a chave pública, sem chamar a
 * queue. O algoritmo é fixo em ES256: um token com outro {@code alg} no header,
 * inclusive {@code none} ou HS256 assinado com a chave pública, é recusado.
 */
@Component
public class PassVerifier {

    private static final String ISSUER = "ticket-hit-queue";
    private static final String AUDIENCE = "ticketing";

    private final NimbusJwtDecoder decoder;

    PassVerifier(PassProperties properties, Clock clock) throws GeneralSecurityException {
        var publicKey = (ECPublicKey) KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(properties.publicKey())));
        // kid = thumbprint da chave pública, o mesmo que a queue põe no header.
        ECKey jwk;
        try {
            jwk = new ECKey.Builder(Curve.P_256, publicKey).keyIDFromThumbprint().build();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
        this.decoder = NimbusJwtDecoder.withJwkSource(new ImmutableJWKSet<>(new JWKSet(jwk)))
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();
        var timestamps = new JwtTimestampValidator(properties.clockSkew());
        timestamps.setClock(clock);
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamps,
                new JwtIssuerValidator(ISSUER),
                new JwtClaimValidator<List<String>>(
                        JwtClaimNames.AUD, audience -> audience != null && audience.contains(AUDIENCE))));
    }

    /** Devolve o id do passe se ele vale para esta venda e este comprador. */
    public UUID verify(String token, UUID saleId, String customerId) {
        Jwt pass;
        try {
            pass = decoder.decode(token);
        } catch (JwtException rejected) {
            throw new InvalidPassException(rejected.getMessage(), rejected);
        }
        if (!saleId.toString().equals(pass.getClaimAsString("sid"))) {
            throw new InvalidPassException("emitido para outra venda");
        }
        if (!customerId.equals(pass.getSubject())) {
            throw new InvalidPassException("emitido para outro comprador");
        }
        try {
            return UUID.fromString(pass.getId());
        } catch (IllegalArgumentException | NullPointerException malformed) {
            throw new InvalidPassException("sem id válido", malformed);
        }
    }
}
