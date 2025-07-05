package com.javastudy.distributedid;

import java.util.UUID;

/**
 * UUID-based ID generator supporting different UUID versions.
 * UUIDs are widely used for distributed systems due to their uniqueness guarantees.
 */
public class UuidGenerator implements IdGenerator<UUID> {
    
    private final UuidVersion version;
    
    public enum UuidVersion {
        VERSION_1,  // Time-based UUID
        VERSION_4   // Random UUID (most common)
    }
    
    /**
     * Create a UUID generator with the specified version.
     * @param version the UUID version to use
     */
    public UuidGenerator(UuidVersion version) {
        this.version = version;
    }
    
    /**
     * Create a UUID generator with default version 4 (random).
     */
    public UuidGenerator() {
        this(UuidVersion.VERSION_4);
    }
    
    @Override
    public UUID generate() {
        return switch (version) {
            case VERSION_1 -> generateTimeBasedUuid();
            case VERSION_4 -> UUID.randomUUID();
        };
    }
    
    @Override
    public String generateAsString() {
        return generate().toString();
    }
    
    @Override
    public String getGeneratorName() {
        return "UUID-" + version.name().replace("_", "");
    }
    
    /**
     * Generate a time-based UUID (version 1).
     * Note: Java's standard library doesn't provide direct support for UUID v1,
     * so we'll use a simplified approach combining timestamp and random data.
     */
    private UUID generateTimeBasedUuid() {
        // Get current timestamp in milliseconds
        long timestamp = System.currentTimeMillis();
        
        // Create a pseudo-time-based UUID using timestamp and random data
        // This is a simplified implementation for demonstration
        long mostSigBits = (timestamp << 32) | (System.nanoTime() & 0xFFFFFFFFL);
        long leastSigBits = UUID.randomUUID().getLeastSignificantBits();
        
        // Set version bits to 1 (time-based)
        mostSigBits &= 0xFFFFFFFFFFFF0FFFL;
        mostSigBits |= 0x0000000000001000L;
        
        // Set variant bits
        leastSigBits &= 0x3FFFFFFFFFFFFFFFL;
        leastSigBits |= 0x8000000000000000L;
        
        return new UUID(mostSigBits, leastSigBits);
    }
    
    /**
     * Generate a compact UUID string (without hyphens).
     * @return UUID as a compact string
     */
    public String generateCompact() {
        return generateAsString().replace("-", "");
    }
    
    /**
     * Generate an uppercase UUID string.
     * @return UUID as an uppercase string
     */
    public String generateUppercase() {
        return generateAsString().toUpperCase();
    }
} 