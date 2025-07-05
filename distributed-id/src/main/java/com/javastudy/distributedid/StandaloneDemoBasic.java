package com.javastudy.distributedid;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Standalone demo that works without external dependencies.
 * This version excludes Redis-based generator to avoid dependency issues.
 */
public class StandaloneDemoBasic {
    
    public static void main(String[] args) {
        System.out.println("=== Distributed ID Generation Demo (Standalone) ===\n");
        
        demoUuidGenerator();
        demoSnowflakeGenerator();
        demoUlidGenerator();
        demoNanoIdGenerator();
        demoTimestampBasedGenerator();
        demoPerformanceComparison();
        
        System.out.println("\n=== Demo Complete ===");
        System.out.println("\nNote: For Redis-based generator demo, run with Maven:");
        System.out.println("mvn exec:java -Dexec.mainClass=\"com.javastudy.distributedid.DistributedIdDemo\"");
    }
    
    private static void demoUuidGenerator() {
        System.out.println("1. UUID Generator Demo");
        System.out.println("---------------------");
        
        UuidGenerator uuid = new UuidGenerator();
        System.out.println("Generated UUIDs:");
        for (int i = 0; i < 3; i++) {
            System.out.println("  " + uuid.generateAsString());
        }
        
        System.out.println("Compact format: " + uuid.generateCompact());
        System.out.println();
    }
    
    private static void demoSnowflakeGenerator() {
        System.out.println("2. Snowflake Generator Demo");
        System.out.println("---------------------------");
        
        SnowflakeGenerator snowflake = new SnowflakeGenerator();
        System.out.println("Generated Snowflake IDs:");
        for (int i = 0; i < 3; i++) {
            long id = snowflake.generate();
            System.out.println("  " + id);
            
            // Parse and show components
            SnowflakeGenerator.SnowflakeInfo info = SnowflakeGenerator.parseSnowflakeId(id);
            System.out.println("    -> Machine: " + info.getMachineId() + 
                             ", Sequence: " + info.getSequence());
        }
        System.out.println();
    }
    
    private static void demoUlidGenerator() {
        System.out.println("3. ULID Generator Demo");
        System.out.println("---------------------");
        
        UlidGenerator ulid = new UlidGenerator();
        System.out.println("Generated ULIDs (sortable):");
        for (int i = 0; i < 3; i++) {
            String id = ulid.generate();
            System.out.println("  " + id);
            System.out.println("    -> Timestamp: " + UlidGenerator.parseTimestamp(id));
        }
        
        // Validation demo
        String testUlid = ulid.generate();
        System.out.println("Validation test:");
        System.out.println("  " + testUlid + " -> valid: " + UlidGenerator.isValid(testUlid));
        System.out.println("  invalid-ulid -> valid: " + UlidGenerator.isValid("invalid-ulid"));
        System.out.println();
    }
    
    private static void demoNanoIdGenerator() {
        System.out.println("4. Nano ID Generator Demo");
        System.out.println("------------------------");
        
        // Default generator
        NanoIdGenerator nano = new NanoIdGenerator();
        System.out.println("Default Nano IDs (21 chars):");
        for (int i = 0; i < 3; i++) {
            System.out.println("  " + nano.generate());
        }
        
        // Custom size
        NanoIdGenerator nanoCustom = new NanoIdGenerator(10);
        System.out.println("Custom size (10 chars):");
        for (int i = 0; i < 3; i++) {
            System.out.println("  " + nanoCustom.generate());
        }
        
        // Numbers only
        NanoIdGenerator nanoNumbers = new NanoIdGenerator(NanoIdGenerator.NUMBERS_ALPHABET, 8);
        System.out.println("Numbers only (8 chars):");
        for (int i = 0; i < 3; i++) {
            System.out.println("  " + nanoNumbers.generate());
        }
        System.out.println();
    }
    
    private static void demoTimestampBasedGenerator() {
        System.out.println("5. Timestamp-based Generator Demo");
        System.out.println("--------------------------------");
        
        TimestampBasedGenerator.Format[] formats = {
            TimestampBasedGenerator.Format.MILLISECONDS_EPOCH,
            TimestampBasedGenerator.Format.COMPACT_DATETIME,
            TimestampBasedGenerator.Format.YYYYMMDD
        };
        
        for (TimestampBasedGenerator.Format format : formats) {
            TimestampBasedGenerator generator = new TimestampBasedGenerator(format);
            System.out.println(format + ": " + generator.generate());
        }
        
        // With random suffix
        TimestampBasedGenerator withRandom = new TimestampBasedGenerator(
            TimestampBasedGenerator.Format.COMPACT_DATETIME, true, 4);
        System.out.println("With random suffix: " + withRandom.generate());
        System.out.println();
    }
    
    private static void demoPerformanceComparison() {
        System.out.println("6. Performance Comparison");
        System.out.println("------------------------");
        
        int iterations = 50000;
        
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
        
        System.out.println("Performance for " + iterations + " iterations:");
        System.out.println("  UUID:      " + formatTime(uuidTime));
        System.out.println("  Snowflake: " + formatTime(snowflakeTime));
        System.out.println("  ULID:      " + formatTime(ulidTime));
        System.out.println("  Nano ID:   " + formatTime(nanoTime));
    }
    
    private static String formatTime(long nanos) {
        return String.format("%.2f ms", nanos / 1_000_000.0);
    }
} 