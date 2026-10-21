package com.jobtrackr.user_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmEncryptionServiceTest {

    private AesGcmEncryptionService encryptionService;

    @BeforeEach
    void setUp() {
        encryptionService = new AesGcmEncryptionService("my-super-secret-encryption-key-for-tests-12345");
    }

    @Test
    @DisplayName("Should successfully encrypt and decrypt string to original value")
    void shouldEncryptAndDecryptSuccessfully() {
        String original = "1//04_random_google_refresh_token_xyz987654321";

        String encrypted = encryptionService.encrypt(original);
        assertThat(encrypted).isNotNull();
        assertThat(encrypted).isNotEqualTo(original);

        String decrypted = encryptionService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    @DisplayName("Should produce different ciphertexts for identical plaintext due to random IV")
    void shouldProduceDifferentCiphertextForSamePlaintext() {
        String plaintext = "same-token-content";

        String encrypted1 = encryptionService.encrypt(plaintext);
        String encrypted2 = encryptionService.encrypt(plaintext);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
        assertThat(encryptionService.decrypt(encrypted1)).isEqualTo(plaintext);
        assertThat(encryptionService.decrypt(encrypted2)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("Should return null when encrypting or decrypting null input")
    void shouldReturnNullForNullInput() {
        assertThat(encryptionService.encrypt(null)).isNull();
        assertThat(encryptionService.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("Should throw exception when decrypting corrupted ciphertext")
    void shouldFailOnCorruptedCiphertext() {
        String validEncrypted = encryptionService.encrypt("secret-value");
        // Tamper with ciphertext by corrupting last character
        char lastChar = validEncrypted.charAt(validEncrypted.length() - 1);
        char tamperedChar = (lastChar == 'A') ? 'B' : 'A';
        String tampered = validEncrypted.substring(0, validEncrypted.length() - 1) + tamperedChar;

        assertThatThrownBy(() -> encryptionService.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }
}
