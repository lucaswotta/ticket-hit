package dev.lucaswotta.tickethit.queue.pass;

import com.nimbusds.jose.JOSEException;
import dev.lucaswotta.tickethit.queue.QueueProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
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
import org.springframework.stereotype.Component;

/**
 * Emite o passe de acesso: um JWT assinado com ES256. Só a queue tem a chave
 * privada; o ticketing confere com a pública e não consegue emitir passe.
 */
@Component
public class PassIssuer {

    public static final String ISSUER = "ticket-hit-queue";
    public static final String AUDIENCE = "ticketing";

    private final JwtEncoder encoder;

    PassIssuer(QueueProperties properties) throws GeneralSecurityException {
        var keys = KeyFactory.getInstance("EC");
        var publicKey = (ECPublicKey) keys.generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(properties.pass().publicKey())));
        var privateKey = (ECPrivateKey) keys.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(properties.pass().privateKey())));
        // kid = thumbprint da chave pública (RFC 7638): quem verifica calcula o
        // mesmo id a partir da pública, sem combinar um identificador à parte.
        this.encoder = NimbusJwtEncoder.withKeyPair(publicKey, privateKey)
                .jwkPostProcessor(jwk -> {
                    try {
                        jwk.keyIDFromThumbprint();
                    } catch (JOSEException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .build();
    }

    public String issue(UUID saleId, String customerId, Instant admittedAt, Instant expiresAt) {
        var claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(customerId)
                .claim("sid", saleId.toString())
                .id(passId(saleId, customerId).toString())
                .issuedAt(admittedAt)
                .expiresAt(expiresAt)
                .build();
        var header = JwsHeader.with(SignatureAlgorithm.ES256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // Derivado da venda e do comprador: consultar a fila de novo devolve o mesmo
    // id de passe, e o ticketing aceita um hold por passe.
    static UUID passId(UUID saleId, String customerId) {
        return UUID.nameUUIDFromBytes((saleId + ":" + customerId).getBytes(StandardCharsets.UTF_8));
    }
}
