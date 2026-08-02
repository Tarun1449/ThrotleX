package com.throttlex.urlshortener.util;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;

@Component
public class ShortCodeGenerator {

    // Base62 characters (A-Z, a-z, 0-9). Completely URL safe.
    private static final String BASE62_ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE62_LENGTH = BASE62_ALPHABET.length();
    private static final int CODE_LENGTH = 7;
    
    // SecureRandom is cryptographically strong, ensuring no predictable patterns.
    private final SecureRandom random = new SecureRandom();

    /**
     * Generates a random 7-character Base62 string.
     * Yields 62^7 (3.5 trillion) possible combinations.
     * 
     * @return A URL-safe short code.
     */
    public String generateShortCode() {
        StringBuilder shortCode = new StringBuilder(CODE_LENGTH);
        
        for (int i = 0; i < CODE_LENGTH; i++) {
            int randomIndex = random.nextInt(BASE62_LENGTH);
            shortCode.append(BASE62_ALPHABET.charAt(randomIndex));
        }
        
        return shortCode.toString();
    }
}
