package com.javastudy.distributedid;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Random;

/**
 * ULID (Universally Unique Lexicographically Sortable Identifier) generator.
 * 
 * ULIDs are 128-bit identifiers that are:
 * - Lexicographically sortable by time
 * - Canonically encoded as a 26-character string
 * - Case insensitive
 * - No special characters (URL safe)
 * - Monotonic sort order within same millisecond
 * 
 * Structure:
 * - 48 bits: timestamp (milliseconds since Unix epoch)
 * - 80 bits: randomness
 */
public class UlidGenerator implements IdGenerator<String> {
    
    // Crockford's Base32 alphabet (case insensitive, excludes I, L, O, U)
    private static final char[] ENCODING = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K',
        'M', 'N', 'P', 'Q', 'R', 'S', 'T', 'V', 'W', 'X',
        'Y', 'Z'
    };
    
    private static final int TIMESTAMP_LENGTH = 10;
    private static final int RANDOMNESS_LENGTH = 16;
    private static final int TOTAL_LENGTH = TIMESTAMP_LENGTH + RANDOMNESS_LENGTH;
    
    private final Random random;
    private long lastTimestamp = -1L;
    private final byte[] lastRandomness = new byte[10]; // 80 bits
    
    /**
     * Create a ULID generator with secure random.
     */
    public UlidGenerator() {
        this(new SecureRandom());
    }
    
    /**
     * Create a ULID generator with the specified random source.
     * @param random the random source to use
     */
    public UlidGenerator(Random random) {
        this.random = random;
    }
    
    @Override
    public synchronized String generate() {
        return generateUlid();
    }
    
    @Override
    public String generateAsString() {
        return generate();
    }
    
    @Override
    public String getGeneratorName() {
        return "ULID";
    }
    
    /**
     * Generate a ULID with the current timestamp.
     * @return a ULID string
     */
    private String generateUlid() {
        return generateUlid(System.currentTimeMillis());
    }
    
    /**
     * Generate a ULID with the specified timestamp.
     * @param timestamp the timestamp in milliseconds
     * @return a ULID string
     */
    public synchronized String generateUlid(long timestamp) {
        if (timestamp < 0) {
            throw new IllegalArgumentException("Timestamp must be non-negative");
        }
        
        char[] result = new char[TOTAL_LENGTH];
        
        // Encode timestamp (48 bits -> 10 chars)
        encodeTimestamp(timestamp, result);
        
        // Generate and encode randomness (80 bits -> 16 chars)
        generateAndEncodeRandomness(timestamp, result);
        
        return new String(result);
    }
    
    /**
     * Encode the timestamp into the first 10 characters of the result.
     */
    private void encodeTimestamp(long timestamp, char[] result) {
        for (int i = TIMESTAMP_LENGTH - 1; i >= 0; i--) {
            result[i] = ENCODING[(int) (timestamp & 0x1F)];
            timestamp >>>= 5;
        }
    }
    
    /**
     * Generate and encode randomness into the last 16 characters of the result.
     * Implements monotonic randomness within the same millisecond.
     */
    private void generateAndEncodeRandomness(long timestamp, char[] result) {
        byte[] randomness = new byte[10]; // 80 bits
        
        if (timestamp == lastTimestamp) {
            // Same millisecond - increment randomness for monotonic ordering
            incrementRandomness(lastRandomness);
            System.arraycopy(lastRandomness, 0, randomness, 0, 10);
        } else {
            // Different millisecond - generate new randomness
            random.nextBytes(randomness);
            System.arraycopy(randomness, 0, lastRandomness, 0, 10);
            lastTimestamp = timestamp;
        }
        
        // Encode randomness (80 bits -> 16 chars)
        encodeRandomness(randomness, result);
    }
    
    /**
     * Encode the randomness bytes into the result array.
     */
    private void encodeRandomness(byte[] randomness, char[] result) {
        // Convert 80 bits (10 bytes) to 16 base-32 characters
        long value = 0;
        int bits = 0;
        int pos = TIMESTAMP_LENGTH;
        
        for (byte b : randomness) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            
            while (bits >= 5) {
                result[pos++] = ENCODING[(int) ((value >>> (bits - 5)) & 0x1F)];
                bits -= 5;
            }
        }
        
        // Handle remaining bits
        if (bits > 0) {
            result[pos] = ENCODING[(int) ((value << (5 - bits)) & 0x1F)];
        }
    }
    
    /**
     * Increment the randomness bytes for monotonic ordering.
     */
    private void incrementRandomness(byte[] randomness) {
        int carry = 1;
        for (int i = randomness.length - 1; i >= 0 && carry > 0; i--) {
            int sum = (randomness[i] & 0xFF) + carry;
            randomness[i] = (byte) (sum & 0xFF);
            carry = sum >>> 8;
        }
    }
    
    /**
     * Parse a ULID and extract its timestamp.
     * @param ulid the ULID string to parse
     * @return the timestamp in milliseconds
     */
    public static long parseTimestamp(String ulid) {
        if (ulid == null || ulid.length() != TOTAL_LENGTH) {
            throw new IllegalArgumentException("Invalid ULID format");
        }
        
        long timestamp = 0;
        for (int i = 0; i < TIMESTAMP_LENGTH; i++) {
            char c = ulid.charAt(i);
            int value = getCharValue(c);
            timestamp = (timestamp << 5) | value;
        }
        
        return timestamp;
    }
    
    /**
     * Parse a ULID and extract its timestamp as an Instant.
     * @param ulid the ULID string to parse
     * @return the timestamp as an Instant
     */
    public static Instant parseTimestampAsInstant(String ulid) {
        return Instant.ofEpochMilli(parseTimestamp(ulid));
    }
    
    /**
     * Get the numeric value of a Crockford Base32 character.
     */
    private static int getCharValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        } else if (c >= 'A' && c <= 'Z') {
            if (c == 'I') return 1;
            if (c == 'L') return 1;
            if (c == 'O') return 0;
            if (c == 'U') return 0;
            
            // Map A-Z to 10-31, skipping I, L, O, U
            int value = c - 'A' + 10;
            if (c > 'I') value--;
            if (c > 'L') value--;
            if (c > 'O') value--;
            if (c > 'U') value--;
            return value;
        } else if (c >= 'a' && c <= 'z') {
            // Case insensitive - convert to uppercase
            return getCharValue((char) (c - 'a' + 'A'));
        } else {
            throw new IllegalArgumentException("Invalid character in ULID: " + c);
        }
    }
    
    /**
     * Validate if a string is a valid ULID.
     * @param ulid the string to validate
     * @return true if the string is a valid ULID
     */
    public static boolean isValid(String ulid) {
        if (ulid == null || ulid.length() != TOTAL_LENGTH) {
            return false;
        }
        
        try {
            for (char c : ulid.toCharArray()) {
                getCharValue(c);
            }
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
} 