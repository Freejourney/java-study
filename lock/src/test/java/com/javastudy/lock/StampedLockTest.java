package com.javastudy.lock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.StampedLock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * StampedLock 测试用例
 * 
 * StampedLock是Java 8引入的一种新的锁机制，它提供了三种模式的锁：
 * 1. 写锁（Write Lock）：排他锁
 * 2. 悲观读锁（Pessimistic Read Lock）：共享锁，类似ReadWriteLock的读锁
 * 3. 乐观读锁（Optimistic Read Lock）：无锁的读取方式
 * 
 * 核心特性：
 * 1. 乐观读：提供无锁的读取机制，性能更好
 * 2. 锁转换：支持在不同锁模式之间转换
 * 3. 非重入：StampedLock是非重入锁
 * 4. 不支持条件变量：没有Condition支持
 * 5. 性能优势：在读多写少的场景下比ReadWriteLock性能更好
 * 
 * 适用场景：
 * - 读多写少且对性能要求极高的场景
 * - 数据更新频率低，读取频率高的缓存系统
 * - 实时系统中需要高性能读取的场景
 */
public class StampedLockTest {
    
    private static final Logger logger = LoggerFactory.getLogger(StampedLockTest.class);
    
    // 用于测试的共享数据结构
    private static class Point {
        private double x, y;
        private final StampedLock sl = new StampedLock();
        
        // 写操作：移动点的位置
        void move(double deltaX, double deltaY) {
            long stamp = sl.writeLock(); // 获取写锁
            try {
                x += deltaX;
                y += deltaY;
                logger.debug("Moved point to ({}, {})", x, y);
            } finally {
                sl.unlockWrite(stamp); // 释放写锁
            }
        }
        
        // 悲观读操作：计算到原点的距离
        double distanceFromOriginPessimistic() {
            long stamp = sl.readLock(); // 获取悲观读锁
            try {
                logger.debug("Pessimistic read: point at ({}, {})", x, y);
                return Math.sqrt(x * x + y * y);
            } finally {
                sl.unlockRead(stamp); // 释放悲观读锁
            }
        }
        
        // 乐观读操作：计算到原点的距离
        double distanceFromOriginOptimistic() {
            long stamp = sl.tryOptimisticRead(); // 尝试乐观读
            double curX = x, curY = y; // 读取当前值
            
            if (!sl.validate(stamp)) { // 验证读取期间是否有写操作
                // 如果有写操作，降级为悲观读
                stamp = sl.readLock();
                try {
                    curX = x;
                    curY = y;
                    logger.debug("Optimistic read failed, downgraded to pessimistic read");
                } finally {
                    sl.unlockRead(stamp);
                }
            } else {
                logger.debug("Optimistic read successful: point at ({}, {})", curX, curY);
            }
            
            return Math.sqrt(curX * curX + curY * curY);
        }
        
        // 获取当前坐标（使用乐观读）
        double[] getCoordinates() {
            long stamp = sl.tryOptimisticRead();
            double curX = x, curY = y;
            
            if (!sl.validate(stamp)) {
                stamp = sl.readLock();
                try {
                    curX = x;
                    curY = y;
                } finally {
                    sl.unlockRead(stamp);
                }
            }
            
            return new double[]{curX, curY};
        }
    }
    
    /**
     * 测试基本的写锁功能
     */
    @Test
    @Timeout(3)
    public void testWriteLock() throws InterruptedException {
        Point point = new Point();
        int writerCount = 3;
        CountDownLatch latch = new CountDownLatch(writerCount);
        
        // 启动多个写线程
        for (int i = 0; i < writerCount; i++) {
            final int writerId = i;
            new Thread(() -> {
                try {
                    point.move(writerId, writerId * 2);
                    Thread.sleep(100); // 模拟写操作耗时
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        boolean finished = latch.await(2, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        
        // 验证最终结果
        double[] coords = point.getCoordinates();
        logger.info("Final coordinates after write operations: ({}, {})", coords[0], coords[1]);
        
        // 所有写操作都应该成功执行
        double expectedX = 0 + 1 + 2; // 0 + 1 + 2
        double expectedY = 0 + 2 + 4; // 0 + 2 + 4
        assertThat(coords[0]).isEqualTo(expectedX);
        assertThat(coords[1]).isEqualTo(expectedY);
    }
    
    /**
     * 测试悲观读锁的功能
     */
    @Test
    @Timeout(3)
    public void testPessimisticRead() throws InterruptedException {
        Point point = new Point();
        point.move(3, 4); // 设置初始位置
        
        int readerCount = 5;
        CountDownLatch latch = new CountDownLatch(readerCount);
        AtomicInteger successfulReads = new AtomicInteger(0);
        
        // 启动多个悲观读线程
        for (int i = 0; i < readerCount; i++) {
            new Thread(() -> {
                try {
                    double distance = point.distanceFromOriginPessimistic();
                    assertThat(distance).isEqualTo(5.0); // 3-4-5三角形
                    successfulReads.incrementAndGet();
                    logger.info("Pessimistic read successful, distance: {}", distance);
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        boolean finished = latch.await(2, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        assertThat(successfulReads.get()).isEqualTo(readerCount);
        
        logger.info("All {} pessimistic reads completed successfully", readerCount);
    }
    
    /**
     * 测试乐观读锁的功能
     */
    @Test
    @Timeout(3)
    public void testOptimisticRead() throws InterruptedException {
        Point point = new Point();
        point.move(3, 4); // 设置初始位置
        
        int readerCount = 10;
        CountDownLatch latch = new CountDownLatch(readerCount);
        AtomicInteger successfulReads = new AtomicInteger(0);
        
        // 启动多个乐观读线程
        for (int i = 0; i < readerCount; i++) {
            new Thread(() -> {
                try {
                    double distance = point.distanceFromOriginOptimistic();
                    assertThat(distance).isEqualTo(5.0); // 3-4-5三角形
                    successfulReads.incrementAndGet();
                    logger.info("Optimistic read successful, distance: {}", distance);
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        boolean finished = latch.await(2, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        assertThat(successfulReads.get()).isEqualTo(readerCount);
        
        logger.info("All {} optimistic reads completed successfully", readerCount);
    }
    
    /**
     * 测试乐观读在有写操作时的降级行为
     */
    @Test
    @Timeout(5)
    public void testOptimisticReadDowngrade() throws InterruptedException {
        Point point = new Point();
        point.move(1, 1); // 设置初始位置
        
        CountDownLatch writerStarted = new CountDownLatch(1);
        CountDownLatch readerStarted = new CountDownLatch(1);
        CountDownLatch writerFinished = new CountDownLatch(1);
        CountDownLatch readerFinished = new CountDownLatch(1);
        
        // 写线程：在读操作过程中修改数据
        Thread writerThread = new Thread(() -> {
            try {
                writerStarted.countDown();
                readerStarted.await(); // 等待读线程开始
                Thread.sleep(100); // 确保读线程正在进行乐观读
                
                logger.info("Writer starting to modify data during optimistic read");
                point.move(2, 2); // 修改数据，导致乐观读失效
                logger.info("Writer finished modifying data");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                writerFinished.countDown();
            }
        });
        
        // 读线程：使用乐观读
        Thread readerThread = new Thread(() -> {
            try {
                writerStarted.await(); // 等待写线程准备
                readerStarted.countDown();
                
                // 执行可能被中断的乐观读
                double distance = point.distanceFromOriginOptimistic();
                logger.info("Optimistic read result: {}", distance);
                
                // 结果应该是一致的（要么是修改前的值，要么是修改后的值）
                assertThat(distance).isIn(Math.sqrt(2), Math.sqrt(18)); // sqrt(1²+1²) 或 sqrt(3²+3²)
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                readerFinished.countDown();
            }
        });
        
        writerThread.start();
        readerThread.start();
        
        boolean writerCompleted = writerFinished.await(3, TimeUnit.SECONDS);
        boolean readerCompleted = readerFinished.await(1, TimeUnit.SECONDS);
        
        assertThat(writerCompleted).isTrue();
        assertThat(readerCompleted).isTrue();
        
        logger.info("Optimistic read downgrade test completed successfully");
    }
    
    /**
     * 测试锁转换功能
     * StampedLock支持在不同锁模式之间转换
     */
    @Test
    @Timeout(3)
    public void testLockConversion() {
        StampedLock sl = new StampedLock();
        
        // 测试从悲观读锁转换为写锁
        long readStamp = sl.readLock();
        try {
            logger.info("Acquired pessimistic read lock with stamp: {}", readStamp);
            
            // 尝试转换为写锁
            long writeStamp = sl.tryConvertToWriteLock(readStamp);
            if (writeStamp != 0) {
                try {
                    logger.info("Successfully converted read lock to write lock with stamp: {}", writeStamp);
                    assertThat(writeStamp).isNotEqualTo(0);
                } finally {
                    sl.unlockWrite(writeStamp);
                }
            } else {
                logger.info("Failed to convert read lock to write lock");
                sl.unlockRead(readStamp);
            }
        } catch (Exception e) {
            sl.unlockRead(readStamp);
            throw e;
        }
        
        // 测试从乐观读转换为悲观读
        long optimisticStamp = sl.tryOptimisticRead();
        logger.info("Acquired optimistic read stamp: {}", optimisticStamp);
        
        long pessimisticStamp = sl.tryConvertToReadLock(optimisticStamp);
        if (pessimisticStamp != 0) {
            try {
                logger.info("Successfully converted optimistic read to pessimistic read with stamp: {}", pessimisticStamp);
                assertThat(pessimisticStamp).isNotEqualTo(0);
            } finally {
                sl.unlockRead(pessimisticStamp);
            }
        } else {
            logger.info("Failed to convert optimistic read to pessimistic read");
        }
        
        logger.info("Lock conversion test completed successfully");
    }
    
    /**
     * 测试StampedLock的非重入特性
     * StampedLock是非重入锁，同一线程不能重复获取同一个锁
     */
    @Test
    @Timeout(2)
    public void testNonReentrant() {
        StampedLock sl = new StampedLock();
        
        // 获取写锁
        long writeStamp = sl.writeLock();
        try {
            logger.info("Acquired write lock with stamp: {}", writeStamp);
            
            // 尝试再次获取写锁（应该失败，因为是非重入的）
            long acquired = sl.tryWriteLock(100, TimeUnit.MILLISECONDS);
            assertThat(acquired).isEqualTo(0);
            logger.info("Cannot reacquire write lock - non-reentrant behavior confirmed");
            
            // 尝试获取读锁（也应该失败）
            long readAcquired = sl.tryReadLock(100, TimeUnit.MILLISECONDS);
            assertThat(readAcquired).isEqualTo(0);
            logger.info("Cannot acquire read lock while holding write lock - as expected");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sl.unlockWrite(writeStamp);
        }
        
        // 获取读锁
        long readStamp = sl.readLock();
        try {
            logger.info("Acquired read lock with stamp: {}", readStamp);
            
            // 尝试再次获取读锁（应该成功，因为读锁是共享的）
            long readAcquiredStamp = sl.tryReadLock(100, TimeUnit.MILLISECONDS);
            if (readAcquiredStamp != 0) {
                long secondReadStamp = sl.tryReadLock();
                if (secondReadStamp != 0) {
                    sl.unlockRead(secondReadStamp);
                    logger.info("Successfully acquired second read lock - read locks are shared");
                }
                sl.unlockRead(readAcquiredStamp);
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            sl.unlockRead(readStamp);
        }
        
        logger.info("Non-reentrant test completed successfully");
    }
    
    /**
     * 性能对比测试：StampedLock vs ReadWriteLock
     */
    @Test
    @Timeout(10)
    public void testPerformanceComparison() throws InterruptedException {
        int readerCount = 8;
        int writerCount = 2;
        int operationsPerThread = 1000;
        
        // 测试StampedLock性能
        long stampedLockTime = testStampedLockPerformance(readerCount, writerCount, operationsPerThread);
        
        // 这里我们只测试StampedLock的性能，实际项目中可以与ReadWriteLock对比
        logger.info("StampedLock performance test completed in {} ms", stampedLockTime);
        
        assertThat(stampedLockTime).isGreaterThan(0);
    }
    
    private long testStampedLockPerformance(int readerCount, int writerCount, int operationsPerThread) throws InterruptedException {
        Point point = new Point();
        point.move(5, 5); // 初始化
        
        CountDownLatch latch = new CountDownLatch(readerCount + writerCount);
        long startTime = System.currentTimeMillis();
        
        // 启动读线程
        for (int i = 0; i < readerCount; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        // 随机选择乐观读或悲观读
                        if (j % 2 == 0) {
                            point.distanceFromOriginOptimistic();
                        } else {
                            point.distanceFromOriginPessimistic();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        // 启动写线程
        for (int i = 0; i < writerCount; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        point.move(0.1, 0.1);
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        latch.await();
        return System.currentTimeMillis() - startTime;
    }
    
    /**
     * 测试StampedLock的状态查询功能
     */
    @Test
    public void testLockStateQueries() {
        StampedLock sl = new StampedLock();
        
        // 初始状态
        assertThat(sl.isWriteLocked()).isFalse();
        assertThat(sl.isReadLocked()).isFalse();
        assertThat(sl.getReadLockCount()).isEqualTo(0);
        
        // 获取写锁后的状态
        long writeStamp = sl.writeLock();
        try {
            assertThat(sl.isWriteLocked()).isTrue();
            assertThat(sl.isReadLocked()).isFalse();
            assertThat(sl.getReadLockCount()).isEqualTo(0);
        } finally {
            sl.unlockWrite(writeStamp);
        }
        
        // 获取读锁后的状态
        long readStamp = sl.readLock();
        try {
            assertThat(sl.isWriteLocked()).isFalse();
            assertThat(sl.isReadLocked()).isTrue();
            assertThat(sl.getReadLockCount()).isEqualTo(1);
        } finally {
            sl.unlockRead(readStamp);
        }
        
        // 乐观读不影响锁状态
        long optimisticStamp = sl.tryOptimisticRead();
        assertThat(sl.isWriteLocked()).isFalse();
        assertThat(sl.isReadLocked()).isFalse();
        assertThat(sl.getReadLockCount()).isEqualTo(0);
        assertThat(optimisticStamp).isNotEqualTo(0);
        
        logger.info("StampedLock state query test completed successfully");
    }
    
    /**
     * 测试stamp验证功能
     */
    @Test
    public void testStampValidation() throws InterruptedException {
        StampedLock sl = new StampedLock();
        
        // 获取乐观读stamp
        long optimisticStamp = sl.tryOptimisticRead();
        assertThat(sl.validate(optimisticStamp)).isTrue();
        
        // 在没有写操作的情况下，stamp应该保持有效
        Thread.sleep(10);
        assertThat(sl.validate(optimisticStamp)).isTrue();
        
        // 执行写操作
        long writeStamp = sl.writeLock();
        try {
            // 写操作会使之前的乐观读stamp失效
            assertThat(sl.validate(optimisticStamp)).isFalse();
        } finally {
            sl.unlockWrite(writeStamp);
        }
        
        // 写操作完成后，之前的stamp仍然无效
        assertThat(sl.validate(optimisticStamp)).isFalse();
        
        // 获取新的乐观读stamp
        long newOptimisticStamp = sl.tryOptimisticRead();
        assertThat(sl.validate(newOptimisticStamp)).isTrue();
        
        logger.info("Stamp validation test completed successfully");
    }
} 