package dev.lucaswotta.tickethit.ticketing.pass;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.lucaswotta.tickethit.ticketing.support.MutableClock;
import dev.lucaswotta.tickethit.ticketing.support.TestPasses;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

class PassVerifierTest {

    private final MutableClock clock = new MutableClock();
    private final PassVerifier verifier =
            new PassVerifier(new PassProperties(TestPasses.PUBLIC_KEY, Duration.ofSeconds(5)), clock);
    private final UUID saleId = UUID.randomUUID();
    private final Instant now = clock.instant();

    PassVerifierTest() throws Exception {}

    @Test
    void validPassReturnsItsId() {
        String pass = TestPasses.issue(saleId, "ana", now, now.plusSeconds(120));

        assertThat(verifier.verify(pass, saleId, "ana")).isNotNull();
    }

    @Test
    void passForAnotherSaleIsRejected() {
        String pass = TestPasses.issue(UUID.randomUUID(), "ana", now, now.plusSeconds(120));

        assertThatThrownBy(() -> verifier.verify(pass, saleId, "ana")).hasMessageContaining("outra venda");
    }

    @Test
    void passForAnotherBuyerIsRejected() {
        String pass = TestPasses.issue(saleId, "ana", now, now.plusSeconds(120));

        assertThatThrownBy(() -> verifier.verify(pass, saleId, "bot")).hasMessageContaining("outro comprador");
    }

    @Test
    void expiredPassIsRejectedAfterTheClockSkew() {
        String pass = TestPasses.issue(saleId, "ana", now, now.plusSeconds(120));
        clock.advance(Duration.ofSeconds(124));
        assertThat(verifier.verify(pass, saleId, "ana")).isNotNull();

        clock.advance(Duration.ofSeconds(2));

        assertThatThrownBy(() -> verifier.verify(pass, saleId, "ana")).isInstanceOf(InvalidPassException.class);
    }

    @Test
    void passSignedWithAnotherKeyIsRejected() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        var otherKeys = generator.generateKeyPair();
        String forged = TestPasses.encoder(
                        Base64.getEncoder().encodeToString(otherKeys.getPublic().getEncoded()),
                        Base64.getEncoder().encodeToString(otherKeys.getPrivate().getEncoded()))
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(SignatureAlgorithm.ES256).build(),
                        TestPasses.claims(saleId, "bot", now, now.plusSeconds(120)).build()))
                .getTokenValue();

        assertThatThrownBy(() -> verifier.verify(forged, saleId, "bot")).isInstanceOf(InvalidPassException.class);
    }

    // Ataques clássicos de JWT: alg "none" sem assinatura, e HS256 usando a chave
    // pública (que é conhecida) como segredo do HMAC.
    @Test
    void unsignedPassIsRejected() {
        String forged = unsigned("{\"alg\":\"none\"}") + ".";

        assertThatThrownBy(() -> verifier.verify(forged, saleId, "bot")).isInstanceOf(InvalidPassException.class);
    }

    @Test
    void passSignedWithHmacOverThePublicKeyIsRejected() throws Exception {
        String signingInput = unsigned("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        var hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(Base64.getDecoder().decode(TestPasses.PUBLIC_KEY), "HmacSHA256"));
        String forged = signingInput + "."
                + base64Url(hmac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));

        assertThatThrownBy(() -> verifier.verify(forged, saleId, "bot")).isInstanceOf(InvalidPassException.class);
    }

    @Test
    void passFromAnotherIssuerOrAudienceIsRejected() {
        String otherIssuer = TestPasses.issue(TestPasses.claims(saleId, "ana", now, now.plusSeconds(120))
                .issuer("outro-emissor")
                .build());
        String otherAudience = TestPasses.issue(TestPasses.claims(saleId, "ana", now, now.plusSeconds(120))
                .audience(List.of("payment"))
                .build());

        assertThatThrownBy(() -> verifier.verify(otherIssuer, saleId, "ana")).isInstanceOf(InvalidPassException.class);
        assertThatThrownBy(() -> verifier.verify(otherAudience, saleId, "ana")).isInstanceOf(InvalidPassException.class);
    }

    private String unsigned(String header) {
        String claims = """
                {"iss":"ticket-hit-queue","aud":["ticketing"],"sub":"bot","sid":"%s","jti":"%s","exp":%d}
                """.formatted(saleId, UUID.randomUUID(), now.plusSeconds(120).getEpochSecond()).strip();
        return base64Url(header.getBytes(StandardCharsets.UTF_8)) + "." + base64Url(claims.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
