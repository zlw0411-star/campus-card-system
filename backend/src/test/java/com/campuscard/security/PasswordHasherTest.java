package com.campuscard.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    @Test void hashUsesAgreedPbkdf2FormatAndVerifiesOnlyMatchingPassword() {
        char[] password = "correct horse battery staple".toCharArray();
        String encoded = PasswordHasher.hash(password);
        String[] parts = encoded.split("\\$", -1);

        assertEquals(4, parts.length);
        assertEquals("pbkdf2", parts[0]);
        assertEquals("210000", parts[1]);
        assertEquals(16, Base64.getDecoder().decode(parts[2]).length);
        assertEquals(32, Base64.getDecoder().decode(parts[3]).length);
        assertTrue(PasswordHasher.verify(password, encoded));
        assertFalse(PasswordHasher.verify("different password".toCharArray(), encoded));
        assertNotEquals(encoded, PasswordHasher.hash(password), "each hash should use a fresh salt");
    }

    @Test void malformedOrAdversarialEncodingsFailClosed() {
        char[] password = "password123".toCharArray();
        String valid = PasswordHasher.hash(password);
        String[] malformed = {
                "", "pbkdf2$1$AA==$AA==", "pbkdf2$210000$%%%$" + "A".repeat(44),
                "pbkdf2$210000$" + "A".repeat(24) + "$" + "A".repeat(44),
                "pbkdf2$210000$" + valid.split("\\$")[2] + "$" + valid.split("\\$")[3] + "$extra",
                "pbkdf2$999999999999999999999999$" + "A".repeat(24) + "$" + "A".repeat(44),
                "x".repeat(129)
        };
        for (String value : malformed) assertFalse(PasswordHasher.verify(password, value), value);
        assertFalse(PasswordHasher.verify(null, valid));
        assertFalse(PasswordHasher.verify(password, null));
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null));
    }
}
