# Distributed ID Generation Module

This module provides comprehensive implementations of various distributed ID generation algorithms commonly used in distributed systems. Each implementation includes practical examples and demonstrates different use cases.

## Overview

Distributed ID generation is crucial for distributed systems where you need unique identifiers across multiple machines, databases, or services. This module implements several popular algorithms:

1. **UUID Generator** - Universally Unique Identifiers
2. **Snowflake Generator** - Twitter's Snowflake algorithm
3. **ULID Generator** - Universally Unique Lexicographically Sortable Identifier
4. **Nano ID Generator** - Compact, URL-safe unique IDs
5. **Timestamp-based Generator** - Human-readable, time-based IDs
6. **Redis-based Generator** - Centralized sequential IDs using Redis

## Quick Start

### Basic Usage

```java
// UUID Generator
IdGenerator<UUID> uuidGenerator = new UuidGenerator();
UUID uuid = uuidGenerator.generate();
System.out.println(uuid); // e.g., 550e8400-e29b-41d4-a716-446655440000

// Snowflake Generator
IdGenerator<Long> snowflakeGenerator = new SnowflakeGenerator();
Long snowflakeId = snowflakeGenerator.generate();
System.out.println(snowflakeId); // e.g., 1234567890123456789

// ULID Generator
IdGenerator<String> ulidGenerator = new UlidGenerator();
String ulid = ulidGenerator.generate();
System.out.println(ulid); // e.g., 01ARZ3NDEKTSV4RRFFQ69G5FAV
```

### Running the Demo

```bash
# Compile and run the demo
mvn compile exec:java -Dexec.mainClass="com.javastudy.distributedid.DistributedIdDemo"

# Run tests
mvn test
```

## Detailed Usage

### 1. UUID Generator

UUIDs are widely used for distributed systems due to their uniqueness guarantees without coordination.

```java
// Version 4 (Random) UUID - Default
UuidGenerator uuidV4 = new UuidGenerator(UuidGenerator.UuidVersion.VERSION_4);
UUID uuid = uuidV4.generate();

// Version 1 (Time-based) UUID
UuidGenerator uuidV1 = new UuidGenerator(UuidGenerator.UuidVersion.VERSION_1);
UUID timeBasedUuid = uuidV1.generate();

// Different formats
String standard = uuidV4.generateAsString();    // 550e8400-e29b-41d4-a716-446655440000
String compact = uuidV4.generateCompact();      // 550e8400e29b41d4a716446655440000
String uppercase = uuidV4.generateUppercase();  // 550E8400-E29B-41D4-A716-446655440000
```

**Use Cases:**
- Database primary keys
- Distributed systems without coordination
- Session IDs
- File names

### 2. Snowflake Generator

Based on Twitter's Snowflake algorithm, generates 64-bit integers with embedded timestamp.

```java
// Default generator (auto-assigned machine ID)
SnowflakeGenerator snowflake = new SnowflakeGenerator();
Long id = snowflake.generate();

// Custom machine ID
SnowflakeGenerator customSnowflake = new SnowflakeGenerator(123);
Long customId = customSnowflake.generate();

// Parse Snowflake ID
SnowflakeGenerator.SnowflakeInfo info = SnowflakeGenerator.parseSnowflakeId(id);
System.out.println("Timestamp: " + info.getTimestamp());
System.out.println("Machine ID: " + info.getMachineId());
System.out.println("Sequence: " + info.getSequence());
```

**Use Cases:**
- Event logging and time-series data
- High-performance systems
- Sortable IDs by time
- Real-time analytics

### 3. ULID Generator

ULIDs are lexicographically sortable and URL-safe.

```java
// Basic ULID generation
UlidGenerator ulid = new UlidGenerator();
String id = ulid.generate(); // e.g., 01ARZ3NDEKTSV4RRFFQ69G5FAV

// Parse timestamp from ULID
long timestamp = UlidGenerator.parseTimestamp(id);
Instant instant = UlidGenerator.parseTimestampAsInstant(id);

// Validation
boolean isValid = UlidGenerator.isValid(id);
```

**Use Cases:**
- URLs and file names
- User-facing IDs
- Sortable identifiers
- NoSQL document IDs

### 4. Nano ID Generator

Compact, URL-safe IDs with customizable alphabet and length.

```java
// Default Nano ID (21 characters)
NanoIdGenerator nano = new NanoIdGenerator();
String id = nano.generate(); // e.g., V1StGXR8_Z5jdHi6B-myT

// Custom size
NanoIdGenerator customSize = new NanoIdGenerator(10);
String shortId = customSize.generate(); // e.g., V1StGXR8_Z

// Custom alphabet
NanoIdGenerator numbersOnly = new NanoIdGenerator(NanoIdGenerator.NUMBERS_ALPHABET, 8);
String numberId = numbersOnly.generate(); // e.g., 12345678

// Different alphabets
NanoIdGenerator noAmbiguous = new NanoIdGenerator(NanoIdGenerator.NO_AMBIGUOUS_ALPHABET, 12);
String cleanId = noAmbiguous.generate(); // No 0, O, I, l, 1

// Static methods
String defaultId = NanoIdGenerator.generateDefault();
String customId = NanoIdGenerator.generateWith(15);
```

**Use Cases:**
- Short URLs
- API keys and tokens
- Compact identifiers
- User-friendly IDs

### 5. Timestamp-based Generator

Human-readable IDs with embedded timestamps in various formats.

```java
// Different timestamp formats
TimestampBasedGenerator.Format[] formats = {
    TimestampBasedGenerator.Format.MILLISECONDS_EPOCH,  // 1640995200000
    TimestampBasedGenerator.Format.SECONDS_EPOCH,       // 1640995200
    TimestampBasedGenerator.Format.COMPACT_DATETIME,    // 20211231120000
    TimestampBasedGenerator.Format.YYYYMMDD,            // 20211231
    TimestampBasedGenerator.Format.REVERSE_TIMESTAMP    // For descending order
};

for (TimestampBasedGenerator.Format format : formats) {
    TimestampBasedGenerator generator = new TimestampBasedGenerator(format);
    String id = generator.generate();
    System.out.println(format + ": " + id);
}

// With random suffix for uniqueness
TimestampBasedGenerator withRandom = new TimestampBasedGenerator(
    TimestampBasedGenerator.Format.COMPACT_DATETIME, true, 4);
String uniqueId = withRandom.generate(); // e.g., 20211231120000-1234

// Parse timestamp back
long parsedTimestamp = withRandom.parseTimestamp(uniqueId);
```

**Use Cases:**
- Log file names
- Report IDs
- Batch processing
- Human-readable identifiers

### 6. Redis-based Generator

Centralized sequential ID generation using Redis atomic operations.

```java
// Prerequisites: Redis server running
JedisPool jedisPool = new JedisPool("localhost", 6379);

// Simple increment strategy
RedisBasedGenerator redis = new RedisBasedGenerator(jedisPool);
if (redis.testConnection()) {
    Long id = redis.generate(); // 1, 2, 3, ...
    System.out.println("Generated ID: " + id);
}

// Timestamp-based strategy
RedisBasedGenerator timestampRedis = new RedisBasedGenerator(
    jedisPool, "orders", 
    RedisBasedGenerator.GenerationStrategy.TIMESTAMP_INCREMENT, null);
String timestampId = timestampRedis.generateAsString(); // e.g., 20231231001

// Custom prefix strategy
RedisBasedGenerator customRedis = new RedisBasedGenerator(
    jedisPool, "invoices", 
    RedisBasedGenerator.GenerationStrategy.CUSTOM_PREFIX, "INV");
String customId = customRedis.generateAsString(); // e.g., INV0001

// Monitor current counts
Long currentCount = redis.getCurrentCount();
Long todayCount = timestampRedis.getCurrentCountForDate("20231231");
Long prefixCount = customRedis.getCurrentCountForPrefix("INV");
```

**Use Cases:**
- Order numbers
- Invoice IDs
- Sequential identifiers
- Centralized ID management

## Performance Characteristics

Based on 100,000 iterations benchmark:

| Generator | Performance | Characteristics |
|-----------|-------------|----------------|
| UUID | ~50ms | Fast, no coordination needed |
| Snowflake | ~30ms | Fastest, sortable by time |
| ULID | ~80ms | Sortable, URL-safe |
| Nano ID | ~60ms | Compact, customizable |
| Timestamp | ~40ms | Human-readable |
| Redis | ~2000ms | Sequential, requires network |

## Thread Safety

All generators are thread-safe and can be used in multi-threaded environments:

```java
// Thread-safe usage example
SnowflakeGenerator snowflake = new SnowflakeGenerator();
ExecutorService executor = Executors.newFixedThreadPool(10);

for (int i = 0; i < 10; i++) {
    executor.submit(() -> {
        for (int j = 0; j < 1000; j++) {
            Long id = snowflake.generate(); // Thread-safe
            System.out.println("Generated: " + id);
        }
    });
}
```

## When to Use Each Generator

### UUID Generator
- ✅ **Use when:** Need globally unique IDs without coordination
- ✅ **Pros:** No single point of failure, works offline
- ❌ **Cons:** Not sortable, larger size (36 chars)
- 🎯 **Best for:** Distributed systems, database primary keys

### Snowflake Generator
- ✅ **Use when:** Need sortable IDs with high performance
- ✅ **Pros:** Time-ordered, compact (long), high performance
- ❌ **Cons:** Requires unique machine IDs, clock synchronization
- 🎯 **Best for:** Event logging, time-series data

### ULID Generator
- ✅ **Use when:** Need lexicographically sortable string IDs
- ✅ **Pros:** Sortable, case-insensitive, URL-safe
- ❌ **Cons:** Slightly larger than Snowflake
- 🎯 **Best for:** URLs, file names, user-facing IDs

### Nano ID Generator
- ✅ **Use when:** Need short, URL-safe IDs
- ✅ **Pros:** Very compact, customizable, URL-safe
- ❌ **Cons:** Not sortable, not time-based
- 🎯 **Best for:** Short URLs, API keys, tokens

### Timestamp-based Generator
- ✅ **Use when:** Need human-readable, time-based IDs
- ✅ **Pros:** Human-readable, sortable, flexible formats
- ❌ **Cons:** Potential collisions, predictable
- 🎯 **Best for:** Log files, report names, batch processing

### Redis-based Generator
- ✅ **Use when:** Need guaranteed sequential IDs
- ✅ **Pros:** Sequential, atomic, centralized control
- ❌ **Cons:** Single point of failure, requires Redis
- 🎯 **Best for:** Order numbers, invoice IDs, sequence numbers

## Testing

Run the comprehensive test suite:

```bash
mvn test
```

The tests cover:
- Basic functionality
- Thread safety
- Performance characteristics
- Uniqueness guarantees
- Format validation
- Edge cases

## Dependencies

The module uses the following dependencies:
- **Jedis** for Redis connectivity
- **JUnit 5** for testing
- **AssertJ** for fluent assertions
- **SLF4J** for logging

## Examples

See `DistributedIdDemo.java` for comprehensive usage examples and `DistributedIdGeneratorTest.java` for detailed test cases demonstrating all features.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## License

This project is part of the Java Study modules and is provided for educational purposes. 