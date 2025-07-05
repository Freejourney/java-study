package com.javastudy.distributedid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive tests for all distributed ID generators.
 * 
 * These tests demonstrate:
 * - Basic functionality of each generator
 * - Thread safety and concurrent access
 * - Performance characteristics
 * - Uniqueness guarantees
 * - Format validation
 */
@DisplayName("Distributed ID Generator Tests")
public class DistributedIdGeneratorTest {
    
    @Nested
    @DisplayName("UUID Generator Tests")
    class UuidGeneratorTest {
        
        private UuidGenerator uuidV4Generator;
        private UuidGenerator uuidV1Generator;
        
        @BeforeEach
        void setUp() {
            uuidV4Generator = new UuidGenerator(UuidGenerator.UuidVersion.VERSION_4);
            uuidV1Generator = new UuidGenerator(UuidGenerator.UuidVersion.VERSION_1);
        }
        
        @Test
        @DisplayName("Should generate valid UUID v4")
        void testUuidV4Generation() {
            UUID uuid = uuidV4Generator.generate();
            
            assertThat(uuid).isNotNull();
            assertThat(uuid.version()).isEqualTo(4);
            assertThat(uuidV4Generator.generateAsString()).hasSize(36);
        }
        
        @Test
        @DisplayName("Should generate valid UUID v1")
        void testUuidV1Generation() {
            UUID uuid = uuidV1Generator.generate();
            
            assertThat(uuid).isNotNull();
            assertThat(uuid.version()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("Should generate unique UUIDs")
        void testUuidUniqueness() {
            Set<UUID> uuids = new HashSet<>();
            
            for (int i = 0; i < 10000; i++) {
                UUID uuid = uuidV4Generator.generate();
                assertThat(uuids).doesNotContain(uuid);
                uuids.add(uuid);
            }
        }
        
        @Test
        @DisplayName("Should generate compact format")
        void testCompactFormat() {
            String compact = uuidV4Generator.generateCompact();
            
            assertThat(compact).hasSize(32);
            assertThat(compact).doesNotContain("-");
        }
        
        @Test
        @DisplayName("Should be thread-safe")
        void testThreadSafety() throws InterruptedException {
            int threadCount = 10;
            int uuidsPerThread = 1000;
            Set<UUID> allUuids = new HashSet<>();
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        Set<UUID> threadUuids = new HashSet<>();
                        for (int j = 0; j < uuidsPerThread; j++) {
                            threadUuids.add(uuidV4Generator.generate());
                        }
                        
                        synchronized (allUuids) {
                            allUuids.addAll(threadUuids);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();
            
            assertThat(allUuids).hasSize(threadCount * uuidsPerThread);
        }
    }
    
    @Nested
    @DisplayName("Snowflake Generator Tests")
    class SnowflakeGeneratorTest {
        
        private SnowflakeGenerator snowflakeGenerator;
        
        @BeforeEach
        void setUp() {
            snowflakeGenerator = new SnowflakeGenerator(1);
        }
        
        @Test
        @DisplayName("Should generate valid Snowflake IDs")
        void testSnowflakeGeneration() {
            Long id = snowflakeGenerator.generate();
            
            assertThat(id).isNotNull();
            assertThat(id).isPositive();
            assertThat(snowflakeGenerator.getMachineId()).isEqualTo(1);
        }
        
        @Test
        @DisplayName("Should generate monotonically increasing IDs")
        void testMonotonicOrder() {
            long previousId = snowflakeGenerator.generate();
            
            for (int i = 0; i < 100; i++) {
                long currentId = snowflakeGenerator.generate();
                assertThat(currentId).isGreaterThan(previousId);
                previousId = currentId;
            }
        }
        
        @Test
        @DisplayName("Should parse Snowflake ID correctly")
        void testSnowflakeIdParsing() {
            long id = snowflakeGenerator.generate();
            SnowflakeGenerator.SnowflakeInfo info = SnowflakeGenerator.parseSnowflakeId(id);
            
            assertThat(info.getMachineId()).isEqualTo(1);
            assertThat(info.getTimestamp()).isCloseTo(System.currentTimeMillis(), within(1000L));
            assertThat(info.getSequence()).isGreaterThanOrEqualTo(0);
        }
        
        @Test
        @DisplayName("Should throw exception for invalid machine ID")
        void testInvalidMachineId() {
            assertThatThrownBy(() -> new SnowflakeGenerator(-1))
                    .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> new SnowflakeGenerator(1024))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("Should be thread-safe")
        void testThreadSafety() throws InterruptedException {
            int threadCount = 10;
            int idsPerThread = 1000;
            Set<Long> allIds = new HashSet<>();
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        Set<Long> threadIds = new HashSet<>();
                        for (int j = 0; j < idsPerThread; j++) {
                            threadIds.add(snowflakeGenerator.generate());
                        }
                        
                        synchronized (allIds) {
                            allIds.addAll(threadIds);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(10, TimeUnit.SECONDS);
            executor.shutdown();
            
            assertThat(allIds).hasSize(threadCount * idsPerThread);
        }
    }
    
    @Nested
    @DisplayName("ULID Generator Tests")
    class UlidGeneratorTest {
        
        private UlidGenerator ulidGenerator;
        
        @BeforeEach
        void setUp() {
            ulidGenerator = new UlidGenerator();
        }
        
        @Test
        @DisplayName("Should generate valid ULID")
        void testUlidGeneration() {
            String ulid = ulidGenerator.generate();
            
            assertThat(ulid).hasSize(26);
            assertThat(UlidGenerator.isValid(ulid)).isTrue();
        }
        
        @Test
        @DisplayName("Should generate lexicographically sortable ULIDs")
        void testLexicographicOrder() {
            String firstUlid = ulidGenerator.generate();
            
            // Small delay to ensure different timestamp
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            String secondUlid = ulidGenerator.generate();
            
            assertThat(firstUlid.compareTo(secondUlid)).isLessThan(0);
        }
        
        @Test
        @DisplayName("Should parse timestamp correctly")
        void testTimestampParsing() {
            long beforeGeneration = System.currentTimeMillis();
            String ulid = ulidGenerator.generate();
            long afterGeneration = System.currentTimeMillis();
            
            long parsedTimestamp = UlidGenerator.parseTimestamp(ulid);
            
            assertThat(parsedTimestamp).isBetween(beforeGeneration, afterGeneration);
        }
        
        @Test
        @DisplayName("Should validate ULID format")
        void testUlidValidation() {
            assertThat(UlidGenerator.isValid("01ARZ3NDEKTSV4RRFFQ69G5FAV")).isTrue();
            assertThat(UlidGenerator.isValid("invalid-ulid")).isFalse();
            assertThat(UlidGenerator.isValid("")).isFalse();
            assertThat(UlidGenerator.isValid(null)).isFalse();
        }
        
        @Test
        @DisplayName("Should generate unique ULIDs")
        void testUlidUniqueness() {
            Set<String> ulids = new HashSet<>();
            
            for (int i = 0; i < 10000; i++) {
                String ulid = ulidGenerator.generate();
                assertThat(ulids).doesNotContain(ulid);
                ulids.add(ulid);
            }
        }
    }
    
    @Nested
    @DisplayName("Nano ID Generator Tests")
    class NanoIdGeneratorTest {
        
        private NanoIdGenerator nanoIdGenerator;
        
        @BeforeEach
        void setUp() {
            nanoIdGenerator = new NanoIdGenerator();
        }
        
        @Test
        @DisplayName("Should generate Nano ID with default size")
        void testDefaultNanoId() {
            String nanoId = nanoIdGenerator.generate();
            
            assertThat(nanoId).hasSize(21);
        }
        
        @Test
        @DisplayName("Should generate Nano ID with custom size")
        void testCustomSizeNanoId() {
            NanoIdGenerator customGenerator = new NanoIdGenerator(10);
            String nanoId = customGenerator.generate();
            
            assertThat(nanoId).hasSize(10);
        }
        
        @Test
        @DisplayName("Should generate Nano ID with custom alphabet")
        void testCustomAlphabetNanoId() {
            NanoIdGenerator customGenerator = new NanoIdGenerator(NanoIdGenerator.NUMBERS_ALPHABET, 8);
            String nanoId = customGenerator.generate();
            
            assertThat(nanoId).hasSize(8);
            assertThat(nanoId).matches("[0-9]{8}");
        }
        
        @Test
        @DisplayName("Should generate unique Nano IDs")
        void testNanoIdUniqueness() {
            Set<String> nanoIds = new HashSet<>();
            
            for (int i = 0; i < 10000; i++) {
                String nanoId = nanoIdGenerator.generate();
                assertThat(nanoIds).doesNotContain(nanoId);
                nanoIds.add(nanoId);
            }
        }
        
        @Test
        @DisplayName("Should throw exception for invalid parameters")
        void testInvalidParameters() {
            assertThatThrownBy(() -> new NanoIdGenerator("", 10))
                    .isInstanceOf(IllegalArgumentException.class);
            
            assertThatThrownBy(() -> new NanoIdGenerator(NanoIdGenerator.DEFAULT_ALPHABET, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        
        @Test
        @DisplayName("Should use static methods correctly")
        void testStaticMethods() {
            String defaultNanoId = NanoIdGenerator.generateDefault();
            String customSizeNanoId = NanoIdGenerator.generateWith(15);
            
            assertThat(defaultNanoId).hasSize(21);
            assertThat(customSizeNanoId).hasSize(15);
        }
    }
    
    @Nested
    @DisplayName("Timestamp-based Generator Tests")
    class TimestampBasedGeneratorTest {
        
        @Test
        @DisplayName("Should generate timestamp-based IDs with different formats")
        void testDifferentFormats() {
            TimestampBasedGenerator.Format[] formats = {
                TimestampBasedGenerator.Format.MILLISECONDS_EPOCH,
                TimestampBasedGenerator.Format.SECONDS_EPOCH,
                TimestampBasedGenerator.Format.COMPACT_DATETIME,
                TimestampBasedGenerator.Format.YYYYMMDD
            };
            
            for (TimestampBasedGenerator.Format format : formats) {
                TimestampBasedGenerator generator = new TimestampBasedGenerator(format);
                String id = generator.generate();
                
                assertThat(id).isNotNull();
                assertThat(id).isNotEmpty();
            }
        }
        
        @Test
        @DisplayName("Should generate with random suffix")
        void testRandomSuffix() {
            TimestampBasedGenerator generator = new TimestampBasedGenerator(
                TimestampBasedGenerator.Format.COMPACT_DATETIME, true, 4);
            
            String id = generator.generate();
            
            assertThat(id).contains("-");
            String[] parts = id.split("-");
            assertThat(parts).hasSize(2);
            assertThat(parts[1]).hasSize(4);
        }
        
        @Test
        @DisplayName("Should parse timestamp correctly")
        void testTimestampParsing() {
            TimestampBasedGenerator generator = new TimestampBasedGenerator(
                TimestampBasedGenerator.Format.MILLISECONDS_EPOCH);
            
            long beforeGeneration = System.currentTimeMillis();
            String id = generator.generate();
            long afterGeneration = System.currentTimeMillis();
            
            long parsedTimestamp = generator.parseTimestamp(id);
            
            assertThat(parsedTimestamp).isBetween(beforeGeneration, afterGeneration);
        }
        
        @Test
        @DisplayName("Should handle reverse timestamp format")
        void testReverseTimestamp() {
            TimestampBasedGenerator generator = new TimestampBasedGenerator(
                TimestampBasedGenerator.Format.REVERSE_TIMESTAMP);
            
            String id1 = generator.generate();
            
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            String id2 = generator.generate();
            
            // For reverse timestamp, later IDs should be lexicographically smaller
            assertThat(id1.compareTo(id2)).isGreaterThan(0);
        }
    }
    
    @Test
    @DisplayName("Should demonstrate usage patterns")
    void testUsagePatterns() {
        // Basic usage example
        IdGenerator<UUID> uuidGenerator = new UuidGenerator();
        IdGenerator<Long> snowflakeGenerator = new SnowflakeGenerator();
        IdGenerator<String> ulidGenerator = new UlidGenerator();
        
        // Generate IDs
        UUID uuid = uuidGenerator.generate();
        Long snowflakeId = snowflakeGenerator.generate();
        String ulid = ulidGenerator.generate();
        
        // All generators should produce valid IDs
        assertThat(uuid).isNotNull();
        assertThat(snowflakeId).isNotNull();
        assertThat(ulid).isNotNull();
        
        // String representations
        assertThat(uuidGenerator.generateAsString()).hasSize(36);
        assertThat(snowflakeGenerator.generateAsString()).isNotEmpty();
        assertThat(ulidGenerator.generateAsString()).hasSize(26);
        
        // Generator names
        assertThat(uuidGenerator.getGeneratorName()).contains("UUID");
        assertThat(snowflakeGenerator.getGeneratorName()).contains("Snowflake");
        assertThat(ulidGenerator.getGeneratorName()).contains("ULID");
    }
    
    @Test
    @DisplayName("Should demonstrate performance characteristics")
    void testPerformanceCharacteristics() {
        int iterations = 1000;
        
        // UUID performance
        UuidGenerator uuid = new UuidGenerator();
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            uuid.generate();
        }
        long uuidTime = System.nanoTime() - startTime;
        
        // Snowflake performance
        SnowflakeGenerator snowflake = new SnowflakeGenerator();
        startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            snowflake.generate();
        }
        long snowflakeTime = System.nanoTime() - startTime;
        
        // ULID performance
        UlidGenerator ulid = new UlidGenerator();
        startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            ulid.generate();
        }
        long ulidTime = System.nanoTime() - startTime;
        
        // All generators should complete within reasonable time
        assertThat(uuidTime).isLessThan(TimeUnit.SECONDS.toNanos(1));
        assertThat(snowflakeTime).isLessThan(TimeUnit.SECONDS.toNanos(1));
        assertThat(ulidTime).isLessThan(TimeUnit.SECONDS.toNanos(1));
        
        System.out.println("Performance results for " + iterations + " iterations:");
        System.out.println("UUID: " + formatNanos(uuidTime));
        System.out.println("Snowflake: " + formatNanos(snowflakeTime));
        System.out.println("ULID: " + formatNanos(ulidTime));
    }
    
    private String formatNanos(long nanos) {
        return String.format("%.2f ms", nanos / 1_000_000.0);
    }
} 