package com.runway.api.credentials;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class CredentialEncryptionServiceTest {

    private static final String KEY_A = randomKey();
    private static final String KEY_B = randomKey();

    @Test
    void roundTripsPlaintext() {
        CredentialEncryptionService service = new CredentialEncryptionService(KEY_A);

        String ciphertext = service.encrypt("super-secret-private-key");

        assertThat(service.decrypt(ciphertext)).isEqualTo("super-secret-private-key");
    }

    @Test
    void producesDifferentCiphertextForTheSamePlaintextAcrossCalls() {
        CredentialEncryptionService service = new CredentialEncryptionService(KEY_A);

        String first = service.encrypt("same-plaintext");
        String second = service.encrypt("same-plaintext");

        assertThat(first).isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo("same-plaintext");
        assertThat(service.decrypt(second)).isEqualTo("same-plaintext");
    }

    @Test
    void failsToDecryptWhenCiphertextIsTampered() {
        CredentialEncryptionService service = new CredentialEncryptionService(KEY_A);
        String ciphertext = service.encrypt("super-secret-private-key");

        byte[] bytes = Base64.getDecoder().decode(ciphertext);
        bytes[bytes.length - 1] ^= 0x1;
        String tampered = Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> service.decrypt(tampered)).isInstanceOf(CredentialDecryptionException.class);
    }

    @Test
    void failsToDecryptWithTheWrongKey() {
        CredentialEncryptionService encryptor = new CredentialEncryptionService(KEY_A);
        CredentialEncryptionService decryptor = new CredentialEncryptionService(KEY_B);
        String ciphertext = encryptor.encrypt("super-secret-private-key");

        assertThatThrownBy(() -> decryptor.decrypt(ciphertext)).isInstanceOf(CredentialDecryptionException.class);
    }

    @Test
    void rejectsAKeyThatIsNotThirtyTwoBytes() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new CredentialEncryptionService(shortKey))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new java.security.SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
