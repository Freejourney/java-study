package com.javastudy.distributedid;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.time.Duration;

/**
 * Redis-based distributed ID generator.
 * 
 * Uses Redis atomic operations (INCR) to generate sequential IDs across multiple instances.
 * Supports different strategies for ID generation:
 * - Simple increment
 * - Timestamp + increment
 * - Custom prefix + increment
 * 
 * This generator is particularly useful when you need:
 * - Guaranteed uniqueness across multiple application instances
 * - Sequential IDs
 * - Centralized ID management
 */
public class RedisBasedGenerator implements IdGenerator<Long> {
    
    private final JedisPool jedisPool;
    private final String keyPrefix;
    private final GenerationStrategy strategy;
    private final String customPrefix;
    
    public enum GenerationStrategy {
        SIMPLE_INCREMENT,       // Simple counter: 1, 2, 3, ...
        TIMESTAMP_INCREMENT,    // Timestamp-based: 20231231001, 20231231002, ...
        CUSTOM_PREFIX          // Custom prefix: PREFIX001, PREFIX002, ...
    }
    
    /**
     * Create a Redis-based generator with default settings.
     * @param jedisPool the Redis connection pool
     */
    public RedisBasedGenerator(JedisPool jedisPool) {
        this(jedisPool, "distributed_id", GenerationStrategy.SIMPLE_INCREMENT, null);
    }
    
    /**
     * Create a Redis-based generator with custom key prefix.
     * @param jedisPool the Redis connection pool
     * @param keyPrefix the Redis key prefix for storing counters
     */
    public RedisBasedGenerator(JedisPool jedisPool, String keyPrefix) {
        this(jedisPool, keyPrefix, GenerationStrategy.SIMPLE_INCREMENT, null);
    }
    
    /**
     * Create a Redis-based generator with custom strategy.
     * @param jedisPool the Redis connection pool
     * @param keyPrefix the Redis key prefix for storing counters
     * @param strategy the generation strategy
     * @param customPrefix custom prefix for CUSTOM_PREFIX strategy
     */
    public RedisBasedGenerator(JedisPool jedisPool, String keyPrefix, 
                             GenerationStrategy strategy, String customPrefix) {
        if (jedisPool == null) {
            throw new IllegalArgumentException("JedisPool cannot be null");
        }
        if (keyPrefix == null || keyPrefix.trim().isEmpty()) {
            throw new IllegalArgumentException("Key prefix cannot be null or empty");
        }
        if (strategy == GenerationStrategy.CUSTOM_PREFIX && 
            (customPrefix == null || customPrefix.trim().isEmpty())) {
            throw new IllegalArgumentException("Custom prefix is required for CUSTOM_PREFIX strategy");
        }
        
        this.jedisPool = jedisPool;
        this.keyPrefix = keyPrefix;
        this.strategy = strategy;
        this.customPrefix = customPrefix;
    }
    
    /**
     * Create a Redis-based generator with simple connection parameters.
     * @param host Redis host
     * @param port Redis port
     * @param password Redis password (null if no password)
     */
    public static RedisBasedGenerator create(String host, int port, String password) {
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(10);
        config.setMaxIdle(5);
        config.setMinIdle(1);
        config.setTestOnBorrow(true);
        config.setTestOnReturn(true);
        config.setTestWhileIdle(true);
        config.setMaxWait(Duration.ofSeconds(3));
        
        JedisPool jedisPool = new JedisPool(config, host, port, 2000, password);
        return new RedisBasedGenerator(jedisPool);
    }
    
    /**
     * Create a Redis-based generator with localhost default settings.
     */
    public static RedisBasedGenerator createLocal() {
        return create("localhost", 6379, null);
    }
    
    @Override
    public Long generate() {
        return generateId();
    }
    
    @Override
    public String generateAsString() {
        return switch (strategy) {
            case SIMPLE_INCREMENT -> generate().toString();
            case TIMESTAMP_INCREMENT -> generateTimestampBasedId();
            case CUSTOM_PREFIX -> generateCustomPrefixId();
        };
    }
    
    @Override
    public String getGeneratorName() {
        return "RedisBased-" + strategy.name();
    }
    
    /**
     * Generate a new ID based on the configured strategy.
     * @return a new unique ID
     */
    private Long generateId() {
        try (Jedis jedis = jedisPool.getResource()) {
            return switch (strategy) {
                case SIMPLE_INCREMENT -> jedis.incr(keyPrefix + ":counter");
                case TIMESTAMP_INCREMENT -> generateTimestampBasedIdLong(jedis);
                case CUSTOM_PREFIX -> generateCustomPrefixIdLong(jedis);
            };
        } catch (JedisException e) {
            throw new RuntimeException("Failed to generate ID from Redis", e);
        }
    }
    
    /**
     * Generate a timestamp-based ID as a string.
     * Format: YYYYMMDDXXX where XXX is the counter for that day.
     */
    private String generateTimestampBasedId() {
        String dateKey = getCurrentDateKey();
        String fullKey = keyPrefix + ":timestamp:" + dateKey;
        
        try (Jedis jedis = jedisPool.getResource()) {
            Long counter = jedis.incr(fullKey);
            // Set expiry for the key (e.g., 7 days)
            jedis.expire(fullKey, 7 * 24 * 60 * 60);
            
            return dateKey + String.format("%03d", counter);
        } catch (JedisException e) {
            throw new RuntimeException("Failed to generate timestamp-based ID from Redis", e);
        }
    }
    
    /**
     * Generate a timestamp-based ID as a long.
     */
    private Long generateTimestampBasedIdLong(Jedis jedis) {
        return Long.parseLong(generateTimestampBasedId());
    }
    
    /**
     * Generate a custom prefix ID as a string.
     * Format: CUSTOMPREFIXXXX where XXXX is the counter.
     */
    private String generateCustomPrefixId() {
        String fullKey = keyPrefix + ":custom:" + customPrefix;
        
        try (Jedis jedis = jedisPool.getResource()) {
            Long counter = jedis.incr(fullKey);
            return customPrefix + String.format("%04d", counter);
        } catch (JedisException e) {
            throw new RuntimeException("Failed to generate custom prefix ID from Redis", e);
        }
    }
    
    /**
     * Generate a custom prefix ID as a long (hash of the string).
     */
    private Long generateCustomPrefixIdLong(Jedis jedis) {
        return (long) generateCustomPrefixId().hashCode();
    }
    
    /**
     * Get the current date key in YYYYMMDD format.
     */
    private String getCurrentDateKey() {
        return String.format("%1$tY%1$tm%1$td", System.currentTimeMillis());
    }
    
    /**
     * Get the current count for the default counter.
     * @return the current counter value
     */
    public Long getCurrentCount() {
        try (Jedis jedis = jedisPool.getResource()) {
            String value = jedis.get(keyPrefix + ":counter");
            return value != null ? Long.parseLong(value) : 0L;
        } catch (JedisException e) {
            throw new RuntimeException("Failed to get current count from Redis", e);
        }
    }
    
    /**
     * Reset the counter to a specific value.
     * @param value the value to set the counter to
     */
    public void resetCounter(Long value) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(keyPrefix + ":counter", value.toString());
        } catch (JedisException e) {
            throw new RuntimeException("Failed to reset counter in Redis", e);
        }
    }
    
    /**
     * Get the current count for a specific date (for timestamp-based strategy).
     * @param date the date in YYYYMMDD format
     * @return the current counter value for that date
     */
    public Long getCurrentCountForDate(String date) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = keyPrefix + ":timestamp:" + date;
            String value = jedis.get(key);
            return value != null ? Long.parseLong(value) : 0L;
        } catch (JedisException e) {
            throw new RuntimeException("Failed to get current count for date from Redis", e);
        }
    }
    
    /**
     * Get the current count for a custom prefix.
     * @param prefix the custom prefix
     * @return the current counter value for that prefix
     */
    public Long getCurrentCountForPrefix(String prefix) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = keyPrefix + ":custom:" + prefix;
            String value = jedis.get(key);
            return value != null ? Long.parseLong(value) : 0L;
        } catch (JedisException e) {
            throw new RuntimeException("Failed to get current count for prefix from Redis", e);
        }
    }
    
    /**
     * Test Redis connection.
     * @return true if Redis is reachable
     */
    public boolean testConnection() {
        try (Jedis jedis = jedisPool.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Get the generation strategy used by this generator.
     * @return the generation strategy
     */
    public GenerationStrategy getStrategy() {
        return strategy;
    }
    
    /**
     * Get the key prefix used by this generator.
     * @return the key prefix
     */
    public String getKeyPrefix() {
        return keyPrefix;
    }
    
    /**
     * Get the custom prefix (if using CUSTOM_PREFIX strategy).
     * @return the custom prefix
     */
    public String getCustomPrefix() {
        return customPrefix;
    }
    
    /**
     * Close the Redis connection pool.
     */
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
} 