package com.javastudy.distributedid;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive demo showing how to use various distributed ID generators.
 * 
 * This class demonstrates:
 * - Different types of ID generators
 * - Usage patterns and best practices
 * - Performance considerations
 * - Thread safety demonstrations
 */
public class DistributedIdDemo {
    
    public static void main(String[] args) {
        System.out.println("=== Distributed ID Generation Demo ===\n");
        
        // Demo each generator type
        demoUuidGenerator();
        demoSnowflakeGenerator();
        demoUlidGenerator();
        demoNanoIdGenerator();
        demoTimestampBasedGenerator();
        demoRedisBasedGenerator();
        
        // Demo concurrent usage
        demoConcurrentUsage();
        
        // Demo performance comparison
        demoPerformanceComparison();
        
        System.out.println("\n=== Demo Complete ===");
    }
    
    /**
     * Demonstrate UUID generator usage.
     */
    private static void demoUuidGenerator() {
        System.out.println("1. UUID Generator Demo");
        System.out.println("---------------------");
        
        // Version 4 (Random) UUID
        UuidGenerator uuidV4 = new UuidGenerator(UuidGenerator.UuidVersion.VERSION_4);
        System.out.println("UUID v4 (Random):");
        for (int i = 0; i < 3; i++) {
            System.out.println("  " + uuidV4.generateAsString());
        }
        
        // Version 1 (Time-based) UUID
        UuidGenerator uuidV1 = new UuidGenerator(UuidGenerator.UuidVersion.VERSION_1);
        System.out.println("\nUUID v1 (Time-based):");
        for (int i = 0; i < 3; i++) {
            System.out.println("  " + uuidV1.generateAsString());
        }
        
        // Compact format
        System.out.println("\nCompact format:");
        System.out.println("  " + uuidV4.generateCompact());
        System.out.println("  " + uuidV4.generateUppercase());
        
        System.out.println();
    }
    
    /**
     * Demonstrate Snowflake generator usage.
     */
    private static void demoSnowflakeGenerator() {
        System.out.println("2. Snowflake Generator Demo");
        System.out.println("---------------------------");
        
        // Default Snowflake generator
        SnowflakeGenerator snowflake = new SnowflakeGenerator();
        System.out.println("Default Snowflake IDs:");
        for (int i = 0; i < 5; i++) {
            long id = snowflake.generate();
            System.out.println("  " + id);
            
            // Parse the ID
            SnowflakeGenerator.SnowflakeInfo info = SnowflakeGenerator.parseSnowflakeId(id);
            System.out.println("    -> " + info);
        }
        
        // Custom machine ID
        SnowflakeGenerator customSnowflake = new SnowflakeGenerator(123);
        System.out.println("\nCustom Machine ID (123):");
        for (int i = 0; i < 3; i++) {
            System.out.println("  " + customSnowflake.generate());
        }
        
        System.out.println();
    }
    
    /**
     * Demonstrate ULID generator usage.
     */
    private static void demoUlidGenerator() {
        System.out.println("3. ULID Generator Demo");
        System.out.println("---------------------");
        
        UlidGenerator ulid = new UlidGenerator();
        System.out.println("ULID IDs (lexicographically sortable):");
        for (int i = 0; i < 5; i++) {
            String id = ulid.generate();
            System.out.println("  " + id);
            
            // Parse timestamp
            long timestamp = UlidGenerator.parseTimestamp(id);
            System.out.println("    -> Timestamp: " + timestamp + " (" + 
                             UlidGenerator.parseTimestampAsInstant(id) + ")");
        }
        
        // Validation
        String validUlid = ulid.generate();
        System.out.println("\nValidation:");
        System.out.println("  " + validUlid + " -> " + UlidGenerator.isValid(validUlid));
        System.out.println("  invalid-ulid -> " + UlidGenerator.isValid("invalid-ulid"));
        
        System.out.println();
    }
    
    /**
     * Demonstrate Nano ID generator usage.
     */
    private static void demoNanoIdGenerator() {
        System.out.println("4. Nano ID Generator Demo");
        System.out.println("------------------------");
        
        // Default Nano ID
        NanoIdGenerator nano = new NanoIdGenerator();
        System.out.println("Default Nano IDs (21 chars):");
        for (int i = 0; i < 3; i++) {
            System.out.println("  " + nano.generate());
        }
        
        // Custom size
        NanoIdGenerator nanoCustom = new NanoIdGenerator(10);
        System.out.println("\nCustom size (10 chars):");
        for (int i = 0; i < 3; i++) {
            System.out.println("  " + nanoCustom.generate());
        }
        
        // Different alphabets
        NanoIdGenerator nanoNumbers = new NanoIdGenerator(NanoIdGenerator.NUMBERS_ALPHABET, 8);
        System.out.println("\nNumbers only (8 chars):");
        for (int i = 0; i < 3; i++) {
            System.out.println("  " + nanoNumbers.generate());
        }
        
        NanoIdGenerator nanoNoAmbiguous = new NanoIdGenerator(NanoIdGenerator.NO_AMBIGUOUS_ALPHABET, 12);
        System.out.println("\nNo ambiguous characters (12 chars):");
        for (int i = 0; i < 3; i++) {
            System.out.println("  " + nanoNoAmbiguous.generate());
        }
        
        // Static methods
        System.out.println("\nStatic methods:");
        System.out.println("  Default: " + NanoIdGenerator.generateDefault());
        System.out.println("  Custom size: " + NanoIdGenerator.generateWith(15));
        
        System.out.println();
    }
    
    /**
     * Demonstrate timestamp-based generator usage.
     */
    private static void demoTimestampBasedGenerator() {
        System.out.println("5. Timestamp-based Generator Demo");
        System.out.println("--------------------------------");
        
        // Different formats
        TimestampBasedGenerator.Format[] formats = {
            TimestampBasedGenerator.Format.MILLISECONDS_EPOCH,
            TimestampBasedGenerator.Format.SECONDS_EPOCH,
            TimestampBasedGenerator.Format.COMPACT_DATETIME,
            TimestampBasedGenerator.Format.YYYYMMDD,
            TimestampBasedGenerator.Format.REVERSE_TIMESTAMP
        };
        
        for (TimestampBasedGenerator.Format format : formats) {
            TimestampBasedGenerator generator = new TimestampBasedGenerator(format);
            System.out.println(format + ": " + generator.generate());
        }
        
        // With random suffix
        TimestampBasedGenerator withRandom = new TimestampBasedGenerator(
            TimestampBasedGenerator.Format.COMPACT_DATETIME, true, 4);
        System.out.println("\nWith random suffix:");
        for (int i = 0; i < 3; i++) {
            String id = withRandom.generate();
            System.out.println("  " + id);
            
            // Parse timestamp
            long timestamp = withRandom.parseTimestamp(id);
            System.out.println("    -> Parsed timestamp: " + timestamp);
        }
        
        System.out.println();
    }
    
    /**
     * Demonstrate Redis-based generator usage.
     */
    private static void demoRedisBasedGenerator() {
        System.out.println("6. Redis-based Generator Demo");
        System.out.println("-----------------------------");
        
        try {
            // Create Redis connection (you need Redis running locally)
            JedisPoolConfig config = new JedisPoolConfig();
            config.setMaxTotal(10);
            config.setMaxIdle(5);
            config.setTestOnBorrow(true);
            config.setMaxWait(Duration.ofSeconds(1));
            
            JedisPool jedisPool = new JedisPool(config, "localhost", 6379, 1000);
            
            // Test connection
            RedisBasedGenerator redis = new RedisBasedGenerator(jedisPool);
            
            if (redis.testConnection()) {
                System.out.println("Redis connection successful!");
                
                // Simple increment
                System.out.println("Simple increment:");
                for (int i = 0; i < 5; i++) {
                    System.out.println("  " + redis.generate());
                }
                
                // Timestamp-based
                RedisBasedGenerator timestampRedis = new RedisBasedGenerator(
                    jedisPool, "demo_timestamp", 
                    RedisBasedGenerator.GenerationStrategy.TIMESTAMP_INCREMENT, null);
                System.out.println("\nTimestamp-based:");
                for (int i = 0; i < 3; i++) {
                    System.out.println("  " + timestampRedis.generateAsString());
                }
                
                // Custom prefix
                RedisBasedGenerator customRedis = new RedisBasedGenerator(
                    jedisPool, "demo_custom", 
                    RedisBasedGenerator.GenerationStrategy.CUSTOM_PREFIX, "ORDER");
                System.out.println("\nCustom prefix:");
                for (int i = 0; i < 3; i++) {
                    System.out.println("  " + customRedis.generateAsString());
                }
                
                // Current counts
                System.out.println("\nCurrent counts:");
                System.out.println("  Simple: " + redis.getCurrentCount());
                System.out.println("  Today: " + timestampRedis.getCurrentCountForDate(getCurrentDate()));
                System.out.println("  ORDER: " + customRedis.getCurrentCountForPrefix("ORDER"));
                
                redis.close();
            } else {
                System.out.println("Redis not available - skipping Redis demo");
                System.out.println("(Start Redis with: redis-server)");
            }
            
        } catch (Exception e) {
            System.out.println("Redis not available - skipping Redis demo");
            System.out.println("Error: " + e.getMessage());
        }
        
        System.out.println();
    }
    
    /**
     * Demonstrate concurrent usage and thread safety.
     */
    private static void demoConcurrentUsage() {
        System.out.println("7. Concurrent Usage Demo");
        System.out.println("------------------------");
        
        // Test thread safety with Snowflake
        SnowflakeGenerator snowflake = new SnowflakeGenerator();
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        System.out.println("Concurrent Snowflake ID generation (5 threads):");
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < 3; j++) {
                    long id = snowflake.generate();
                    System.out.println("  Thread " + threadId + ": " + id);
                }
            });
        }
        
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        System.out.println();
    }
    
    /**
     * Demonstrate performance comparison.
     */
    private static void demoPerformanceComparison() {
        System.out.println("8. Performance Comparison");
        System.out.println("------------------------");
        
        int iterations = 100000;
        
        // UUID
        long start = System.nanoTime();
        UuidGenerator uuid = new UuidGenerator();
        for (int i = 0; i < iterations; i++) {
            uuid.generate();
        }
        long uuidTime = System.nanoTime() - start;
        
        // Snowflake
        start = System.nanoTime();
        SnowflakeGenerator snowflake = new SnowflakeGenerator();
        for (int i = 0; i < iterations; i++) {
            snowflake.generate();
        }
        long snowflakeTime = System.nanoTime() - start;
        
        // ULID
        start = System.nanoTime();
        UlidGenerator ulid = new UlidGenerator();
        for (int i = 0; i < iterations; i++) {
            ulid.generate();
        }
        long ulidTime = System.nanoTime() - start;
        
        // Nano ID
        start = System.nanoTime();
        NanoIdGenerator nano = new NanoIdGenerator();
        for (int i = 0; i < iterations; i++) {
            nano.generate();
        }
        long nanoTime = System.nanoTime() - start;
        
        // Timestamp-based
        start = System.nanoTime();
        TimestampBasedGenerator timestamp = new TimestampBasedGenerator();
        for (int i = 0; i < iterations; i++) {
            timestamp.generate();
        }
        long timestampTime = System.nanoTime() - start;
        
        System.out.println("Performance for " + iterations + " iterations:");
        System.out.println("  UUID:           " + formatTime(uuidTime));
        System.out.println("  Snowflake:      " + formatTime(snowflakeTime));
        System.out.println("  ULID:           " + formatTime(ulidTime));
        System.out.println("  Nano ID:        " + formatTime(nanoTime));
        System.out.println("  Timestamp:      " + formatTime(timestampTime));
        
        System.out.println();
    }
    
    /**
     * Format time in milliseconds.
     */
    private static String formatTime(long nanos) {
        return String.format("%.2f ms", nanos / 1_000_000.0);
    }
    
    /**
     * Get current date in YYYYMMDD format.
     */
    private static String getCurrentDate() {
        return String.format("%1$tY%1$tm%1$td", System.currentTimeMillis());
    }
    
    /**
     * Demonstrate different use cases and when to use each generator.
     */
    public static void printUsageRecommendations() {
        System.out.println("Usage Recommendations:");
        System.out.println("======================");
        
        System.out.println("1. UUID Generator");
        System.out.println("   - Use when: You need globally unique IDs without coordination");
        System.out.println("   - Pros: No single point of failure, works offline");
        System.out.println("   - Cons: Not sortable, larger size (36 chars)");
        System.out.println("   - Best for: Distributed systems, database primary keys");
        
        System.out.println("\n2. Snowflake Generator");
        System.out.println("   - Use when: You need sortable IDs with high performance");
        System.out.println("   - Pros: Time-ordered, compact (long), high performance");
        System.out.println("   - Cons: Requires unique machine IDs, clock synchronization");
        System.out.println("   - Best for: Event logging, time-series data");
        
        System.out.println("\n3. ULID Generator");
        System.out.println("   - Use when: You need lexicographically sortable string IDs");
        System.out.println("   - Pros: Sortable, case-insensitive, URL-safe");
        System.out.println("   - Cons: Slightly larger than Snowflake");
        System.out.println("   - Best for: URLs, file names, user-facing IDs");
        
        System.out.println("\n4. Nano ID Generator");
        System.out.println("   - Use when: You need short, URL-safe IDs");
        System.out.println("   - Pros: Very compact, customizable, URL-safe");
        System.out.println("   - Cons: Not sortable, not time-based");
        System.out.println("   - Best for: Short URLs, API keys, tokens");
        
        System.out.println("\n5. Timestamp-based Generator");
        System.out.println("   - Use when: You need human-readable, time-based IDs");
        System.out.println("   - Pros: Human-readable, sortable, flexible formats");
        System.out.println("   - Cons: Potential collisions, predictable");
        System.out.println("   - Best for: Log files, report names, batch processing");
        
        System.out.println("\n6. Redis-based Generator");
        System.out.println("   - Use when: You need guaranteed sequential IDs");
        System.out.println("   - Pros: Sequential, atomic, centralized control");
        System.out.println("   - Cons: Single point of failure, requires Redis");
        System.out.println("   - Best for: Order numbers, invoice IDs, sequence numbers");
    }
} 