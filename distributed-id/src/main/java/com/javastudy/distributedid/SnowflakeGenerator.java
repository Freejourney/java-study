package com.javastudy.distributedid;

import java.time.Instant;

/**
 * Snowflake ID generator based on Twitter's Snowflake algorithm.
 * 
 * Snowflake IDs are 64-bit integers composed of:
 * - 1 bit: sign bit (always 0)
 * - 41 bits: timestamp (milliseconds since epoch)
 * - 10 bits: machine ID (data center + worker)
 * - 12 bits: sequence number
 * 
 * This provides roughly 4000 unique IDs per millisecond per machine.
 */
public class SnowflakeGenerator implements IdGenerator<Long> {
    
    // Twitter's epoch (January 1, 2010)
    private static final long TWITTER_EPOCH = 1288834974657L;
    
    // Bit lengths
    private static final int SEQUENCE_BITS = 12;
    private static final int MACHINE_ID_BITS = 10;
    private static final int TIMESTAMP_BITS = 41;
    
    // Maximum values
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long MAX_MACHINE_ID = (1L << MACHINE_ID_BITS) - 1;
    
    // Bit shifts
    private static final int MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;
    
    private final long machineId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;
    
    /**
     * Create a Snowflake generator with the specified machine ID.
     * @param machineId the machine ID (0 to 1023)
     */
    public SnowflakeGenerator(long machineId) {
        if (machineId < 0 || machineId > MAX_MACHINE_ID) {
            throw new IllegalArgumentException(
                "Machine ID must be between 0 and " + MAX_MACHINE_ID);
        }
        this.machineId = machineId;
    }
    
    /**
     * Create a Snowflake generator with a default machine ID based on system properties.
     */
    public SnowflakeGenerator() {
        this(generateDefaultMachineId());
    }
    
    @Override
    public synchronized Long generate() {
        long currentTimestamp = getCurrentTimestamp();
        
        // Check for clock regression
        if (currentTimestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate ID.");
        }
        
        // Same millisecond as last ID
        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            
            // Sequence overflow - wait for next millisecond
            if (sequence == 0) {
                currentTimestamp = waitForNextMillisecond(currentTimestamp);
            }
        } else {
            // New millisecond - reset sequence
            sequence = 0L;
        }
        
        lastTimestamp = currentTimestamp;
        
        // Combine all parts
        return ((currentTimestamp - TWITTER_EPOCH) << TIMESTAMP_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | sequence;
    }
    
    @Override
    public String generateAsString() {
        return generate().toString();
    }
    
    @Override
    public String getGeneratorName() {
        return "Snowflake";
    }
    
    /**
     * Get the machine ID used by this generator.
     * @return the machine ID
     */
    public long getMachineId() {
        return machineId;
    }
    
    /**
     * Parse a Snowflake ID and extract its components.
     * @param snowflakeId the Snowflake ID to parse
     * @return SnowflakeInfo containing the parsed components
     */
    public static SnowflakeInfo parseSnowflakeId(long snowflakeId) {
        long timestamp = (snowflakeId >> TIMESTAMP_SHIFT) + TWITTER_EPOCH;
        long machineId = (snowflakeId >> MACHINE_ID_SHIFT) & MAX_MACHINE_ID;
        long sequence = snowflakeId & MAX_SEQUENCE;
        
        return new SnowflakeInfo(timestamp, machineId, sequence);
    }
    
    /**
     * Generate a default machine ID based on system properties.
     * This is a simple implementation for demonstration purposes.
     */
    private static long generateDefaultMachineId() {
        // Simple hash of hostname and process ID
        String hostname = System.getProperty("user.name", "unknown");
        long processId = ProcessHandle.current().pid();
        
        return Math.abs((hostname.hashCode() + processId) % (MAX_MACHINE_ID + 1));
    }
    
    /**
     * Get current timestamp in milliseconds.
     */
    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }
    
    /**
     * Wait for the next millisecond.
     */
    private long waitForNextMillisecond(long currentTimestamp) {
        long nextTimestamp = getCurrentTimestamp();
        while (nextTimestamp <= currentTimestamp) {
            nextTimestamp = getCurrentTimestamp();
        }
        return nextTimestamp;
    }
    
    /**
     * Information extracted from a Snowflake ID.
     */
    public static class SnowflakeInfo {
        private final long timestamp;
        private final long machineId;
        private final long sequence;
        
        public SnowflakeInfo(long timestamp, long machineId, long sequence) {
            this.timestamp = timestamp;
            this.machineId = machineId;
            this.sequence = sequence;
        }
        
        public long getTimestamp() {
            return timestamp;
        }
        
        public Instant getInstant() {
            return Instant.ofEpochMilli(timestamp);
        }
        
        public long getMachineId() {
            return machineId;
        }
        
        public long getSequence() {
            return sequence;
        }
        
        @Override
        public String toString() {
            return String.format("SnowflakeInfo{timestamp=%d (%s), machineId=%d, sequence=%d}",
                    timestamp, getInstant(), machineId, sequence);
        }
    }
} 