package com.throttlex.urlshortener.util;

import org.springframework.stereotype.Component;

@Component
public class Base62Encoder {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    /**
     * Encodes a Long (Snowflake ID) into a Base62 String.
     */
    public String encode(long value) {
        if (value == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }

        StringBuilder sb = new StringBuilder();
        while (value > 0) {
            sb.append(ALPHABET.charAt((int) (value % BASE)));
            value /= BASE;
        }

        // We reverse it because the division remainder gives us the least significant character first.
        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 String back into a Long (Snowflake ID).
     */
    public long decode(String base62) {
        long result = 0;
        int length = base62.length();

        for (int i = 0; i < length; i++) {
            char c = base62.charAt(i);
            int value = ALPHABET.indexOf(c);
            
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base62 character: " + c);
            }
            
            result = result * BASE + value;
        }

        return result;
    }
}
