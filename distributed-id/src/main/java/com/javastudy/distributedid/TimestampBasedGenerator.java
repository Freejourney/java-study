package com.javastudy.distributedid;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Timestamp-based ID generator that creates IDs with embedded timestamps.
 * 
 * This generator creates IDs that are sortable by time and can be configured
 * to include different levels of precision and additional randomness.
 */
public class TimestampBasedGenerator implements IdGenerator<String> {
    
    private final Format format;
    private final boolean includeRandom;
    private final int randomLength;
    
    public enum Format {
        MILLISECONDS_EPOCH,     // 1640995200000
        SECONDS_EPOCH,          // 1640995200
        ISO_DATETIME,           // 20211231T120000
        COMPACT_DATETIME,       // 20211231120000
        YYYYMMDDHHMMSS,         // 20211231120000
        YYYYMMDDHHMM,           // 202112311200
        YYYYMMDD,               // 20211231
        REVERSE_TIMESTAMP       // Reverse for descending order
    }
    
    /**
     * Create a timestamp-based generator with default format (milliseconds epoch).
     */
    public TimestampBasedGenerator() {
        this(Format.MILLISECONDS_EPOCH, false, 0);
    }
    
    /**
     * Create a timestamp-based generator with specified format.
     * @param format the timestamp format to use
     */
    public TimestampBasedGenerator(Format format) {
        this(format, false, 0);
    }
    
    /**
     * Create a timestamp-based generator with format and randomness.
     * @param format the timestamp format to use
     * @param includeRandom whether to include random suffix
     * @param randomLength length of random suffix (if includeRandom is true)
     */
    public TimestampBasedGenerator(Format format, boolean includeRandom, int randomLength) {
        this.format = format;
        this.includeRandom = includeRandom;
        this.randomLength = Math.max(0, randomLength);
    }
    
    @Override
    public String generate() {
        return generateTimestampId();
    }
    
    @Override
    public String generateAsString() {
        return generate();
    }
    
    @Override
    public String getGeneratorName() {
        return "TimestampBased-" + format.name();
    }
    
    /**
     * Generate a timestamp-based ID with the current time.
     * @return a timestamp-based ID string
     */
    private String generateTimestampId() {
        return generateTimestampId(System.currentTimeMillis());
    }
    
    /**
     * Generate a timestamp-based ID with the specified timestamp.
     * @param timestamp the timestamp in milliseconds
     * @return a timestamp-based ID string
     */
    public String generateTimestampId(long timestamp) {
        String timestampPart = formatTimestamp(timestamp);
        
        if (includeRandom && randomLength > 0) {
            String randomPart = generateRandomSuffix(randomLength);
            return timestampPart + "-" + randomPart;
        }
        
        return timestampPart;
    }
    
    /**
     * Format the timestamp according to the specified format.
     */
    private String formatTimestamp(long timestamp) {
        return switch (format) {
            case MILLISECONDS_EPOCH -> String.valueOf(timestamp);
            case SECONDS_EPOCH -> String.valueOf(timestamp / 1000);
            case ISO_DATETIME -> formatAsIsoDateTime(timestamp);
            case COMPACT_DATETIME -> formatAsCompactDateTime(timestamp);
            case YYYYMMDDHHMMSS -> formatAsYYYYMMDDHHMMSS(timestamp);
            case YYYYMMDDHHMM -> formatAsYYYYMMDDHHMM(timestamp);
            case YYYYMMDD -> formatAsYYYYMMDD(timestamp);
            case REVERSE_TIMESTAMP -> String.valueOf(Long.MAX_VALUE - timestamp);
        };
    }
    
    /**
     * Format as ISO datetime: 20211231T120000
     */
    private String formatAsIsoDateTime(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp), 
            ZoneId.systemDefault()
        );
        return dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"));
    }
    
    /**
     * Format as compact datetime: 20211231120000
     */
    private String formatAsCompactDateTime(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp), 
            ZoneId.systemDefault()
        );
        return dateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }
    
    /**
     * Format as YYYYMMDDHHMMSS: 20211231120000
     */
    private String formatAsYYYYMMDDHHMMSS(long timestamp) {
        return formatAsCompactDateTime(timestamp);
    }
    
    /**
     * Format as YYYYMMDDHHMM: 202112311200
     */
    private String formatAsYYYYMMDDHHMM(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp), 
            ZoneId.systemDefault()
        );
        return dateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
    }
    
    /**
     * Format as YYYYMMDD: 20211231
     */
    private String formatAsYYYYMMDD(long timestamp) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp), 
            ZoneId.systemDefault()
        );
        return dateTime.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }
    
    /**
     * Generate a random suffix of specified length.
     */
    private String generateRandomSuffix(int length) {
        StringBuilder sb = new StringBuilder();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        
        return sb.toString();
    }
    
    /**
     * Parse a timestamp from a generated ID (if possible).
     * @param id the ID to parse
     * @return the timestamp in milliseconds, or -1 if not parseable
     */
    public long parseTimestamp(String id) {
        if (id == null || id.isEmpty()) {
            return -1;
        }
        
        try {
            // Remove random suffix if present
            String timestampPart = id.contains("-") ? id.split("-")[0] : id;
            
            return switch (format) {
                case MILLISECONDS_EPOCH -> Long.parseLong(timestampPart);
                case SECONDS_EPOCH -> Long.parseLong(timestampPart) * 1000;
                case REVERSE_TIMESTAMP -> Long.MAX_VALUE - Long.parseLong(timestampPart);
                case ISO_DATETIME, COMPACT_DATETIME, YYYYMMDDHHMMSS -> 
                    parseCompactDateTime(timestampPart);
                case YYYYMMDDHHMM -> parseYYYYMMDDHHMM(timestampPart);
                case YYYYMMDD -> parseYYYYMMDD(timestampPart);
            };
        } catch (Exception e) {
            return -1;
        }
    }
    
    /**
     * Parse compact datetime format.
     */
    private long parseCompactDateTime(String timestampPart) {
        if (timestampPart.length() < 14) {
            return -1;
        }
        
        LocalDateTime dateTime = LocalDateTime.parse(timestampPart, 
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    
    /**
     * Parse YYYYMMDDHHMM format.
     */
    private long parseYYYYMMDDHHMM(String timestampPart) {
        if (timestampPart.length() < 12) {
            return -1;
        }
        
        LocalDateTime dateTime = LocalDateTime.parse(timestampPart, 
            DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    
    /**
     * Parse YYYYMMDD format.
     */
    private long parseYYYYMMDD(String timestampPart) {
        if (timestampPart.length() < 8) {
            return -1;
        }
        
        LocalDateTime dateTime = LocalDateTime.parse(timestampPart + "000000", 
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    
    /**
     * Get the format used by this generator.
     * @return the format
     */
    public Format getFormat() {
        return format;
    }
    
    /**
     * Check if this generator includes random suffix.
     * @return true if random suffix is included
     */
    public boolean isIncludeRandom() {
        return includeRandom;
    }
    
    /**
     * Get the length of the random suffix.
     * @return the random suffix length
     */
    public int getRandomLength() {
        return randomLength;
    }
} 