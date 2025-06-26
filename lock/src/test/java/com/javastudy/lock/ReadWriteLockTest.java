package com.javastudy.lock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

/**
 * ReadWriteLock 测试用例
 * 
 * ReadWriteLock是一种特殊的锁，它维护了一对锁：读锁和写锁。
 * 读锁支持并发访问，写锁是排他的。
 * 
 * 核心特性：
 * 1. 读锁共享：多个线程可以同时持有读锁
 * 2. 写锁排他：写锁与读锁、写锁都是互斥的
 * 3. 读写互斥：读锁与写锁是互斥的
 * 4. 锁降级：可以从写锁降级到读锁，但不能从读锁升级到写锁
 * 5. 公平性：支持公平和非公平模式
 * 
 * 适用场景：
 * - 读多写少的场景
 * - 需要保证数据一致性的缓存系统
 * - 配置信息的读取和更新
 */
public class ReadWriteLockTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ReadWriteLockTest.class);
    
    /**
     * 演示读锁的共享特性
     * 多个线程可以同时持有读锁
     */
    @Test
    @Timeout(5)
    public void testReadLockSharing() throws InterruptedException {
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        int readerCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch readingLatch = new CountDownLatch(readerCount);
        CountDownLatch finishLatch = new CountDownLatch(readerCount);
        List<Long> readStartTimes = Collections.synchronizedList(new ArrayList<>());
        List<Long> readEndTimes = Collections.synchronizedList(new ArrayList<>());
        
        // 启动多个读线程
        for (int i = 0; i < readerCount; i++) {
            final int readerId = i;
            new Thread(() -> {
                try {
                    startLatch.await(); // 等待信号同时开始
                    
                    rwLock.readLock().lock();
                    try {
                        long startTime = System.currentTimeMillis();
                        readStartTimes.add(startTime);
                        
                        logger.info("Reader {} acquired read lock at {}", readerId, startTime);
                        readingLatch.countDown(); // 通知已获取读锁
                        
                        Thread.sleep(1000); // 模拟读操作
                        
                        long endTime = System.currentTimeMillis();
                        readEndTimes.add(endTime);
                        logger.info("Reader {} released read lock at {}", readerId, endTime);
                    } finally {
                        rwLock.readLock().unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown(); // 启动所有读线程
        
        // 等待所有读线程获取到锁
        boolean allReadersAcquired = readingLatch.await(2, TimeUnit.SECONDS);
        assertThat(allReadersAcquired).isTrue();
        
        // 等待所有读线程完成
        boolean allFinished = finishLatch.await(3, TimeUnit.SECONDS);
        assertThat(allFinished).isTrue();
        
        // 验证读锁的并发性：所有读线程几乎同时开始
        assertThat(readStartTimes).hasSize(readerCount);
        long minStartTime = Collections.min(readStartTimes);
        long maxStartTime = Collections.max(readStartTimes);
        long timeDifference = maxStartTime - minStartTime;
        
        // 所有读线程应该在很短时间内都获得锁（因为读锁是共享的）
        assertThat(timeDifference).isLessThan(100); // 100ms内
        
        logger.info("Read lock sharing test completed. Time difference: {} ms", timeDifference);
    }
    
    /**
     * 演示写锁的排他特性
     * 写锁与其他锁（读锁、写锁）都是互斥的
     */
    @Test
    @Timeout(8)
    public void testWriteLockExclusivity() throws InterruptedException {
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        int writerCount = 3;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(writerCount);
        List<Long> writeStartTimes = Collections.synchronizedList(new ArrayList<>());
        List<Long> writeEndTimes = Collections.synchronizedList(new ArrayList<>());
        
        // 启动多个写线程
        for (int i = 0; i < writerCount; i++) {
            final int writerId = i;
            new Thread(() -> {
                try {
                    startLatch.await(); // 等待信号同时开始
                    
                    rwLock.writeLock().lock();
                    try {
                        long startTime = System.currentTimeMillis();
                        writeStartTimes.add(startTime);
                        
                        logger.info("Writer {} acquired write lock at {}", writerId, startTime);
                        
                        Thread.sleep(500); // 模拟写操作
                        
                        long endTime = System.currentTimeMillis();
                        writeEndTimes.add(endTime);
                        logger.info("Writer {} released write lock at {}", writerId, endTime);
                    } finally {
                        rwLock.writeLock().unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown(); // 启动所有写线程
        
        // 等待所有写线程完成
        boolean allFinished = finishLatch.await(5, TimeUnit.SECONDS);
        assertThat(allFinished).isTrue();
        
        // 验证写锁的排他性：写操作应该是串行的
        assertThat(writeStartTimes).hasSize(writerCount);
        assertThat(writeEndTimes).hasSize(writerCount);
        
        // 排序时间列表
        Collections.sort(writeStartTimes);
        Collections.sort(writeEndTimes);
        
        // 验证写操作是串行的：每个写操作完成后，下一个才能开始
        for (int i = 1; i < writerCount; i++) {
            long prevEndTime = writeEndTimes.get(i - 1);
            long currentStartTime = writeStartTimes.get(i);
            // 当前写操作的开始时间应该在前一个写操作结束之后
            assertThat(currentStartTime).isGreaterThanOrEqualTo(prevEndTime - 50); // 允许50ms误差
        }
        
        logger.info("Write lock exclusivity test completed successfully");
    }
    
    /**
     * 演示读写锁的互斥特性
     * 读锁和写锁是互斥的
     */
    @Test
    @Timeout(6)
    public void testReadWriteMutualExclusion() throws InterruptedException {
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        CountDownLatch writerStarted = new CountDownLatch(1);
        CountDownLatch writerFinished = new CountDownLatch(1);
        CountDownLatch readerAttempted = new CountDownLatch(1);
        CountDownLatch readerFinished = new CountDownLatch(1);
        
        // 写线程：先获取写锁
        Thread writerThread = new Thread(() -> {
            rwLock.writeLock().lock();
            try {
                logger.info("Writer acquired write lock");
                writerStarted.countDown();
                Thread.sleep(2000); // 持有写锁2秒
                logger.info("Writer about to release write lock");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                rwLock.writeLock().unlock();
                writerFinished.countDown();
                logger.info("Writer released write lock");
            }
        });
        
        // 读线程：尝试获取读锁（应该被阻塞）
        Thread readerThread = new Thread(() -> {
            try {
                writerStarted.await(); // 等待写线程获取锁
                Thread.sleep(100); // 确保写线程正在持有锁
                
                logger.info("Reader attempting to acquire read lock");
                readerAttempted.countDown();
                
                long startTime = System.currentTimeMillis();
                rwLock.readLock().lock();
                try {
                    long waitTime = System.currentTimeMillis() - startTime;
                    logger.info("Reader acquired read lock after waiting {} ms", waitTime);
                    
                    // 读线程应该在写线程释放锁后才能获取锁
                    assertThat(waitTime).isGreaterThan(1500); // 至少等待1.5秒
                } finally {
                    rwLock.readLock().unlock();
                    logger.info("Reader released read lock");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                readerFinished.countDown();
            }
        });
        
        writerThread.start();
        readerThread.start();
        
        // 验证读线程确实尝试获取锁
        boolean readerAttemptedLock = readerAttempted.await(3, TimeUnit.SECONDS);
        assertThat(readerAttemptedLock).isTrue();
        
        // 等待所有线程完成
        boolean writerCompleted = writerFinished.await(3, TimeUnit.SECONDS);
        boolean readerCompleted = readerFinished.await(1, TimeUnit.SECONDS);
        
        assertThat(writerCompleted).isTrue();
        assertThat(readerCompleted).isTrue();
        
        logger.info("Read-write mutual exclusion test completed successfully");
    }
    
    /**
     * 演示锁降级（从写锁降级到读锁）
     * 持有写锁的线程可以获取读锁，然后释放写锁，实现锁降级
     */
    @Test
    @Timeout(3)
    public void testLockDowngrade() throws InterruptedException {
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        AtomicInteger sharedData = new AtomicInteger(0);
        
        // 模拟锁降级场景
        rwLock.writeLock().lock();
        try {
            logger.info("Acquired write lock");
            
            // 修改数据
            sharedData.set(42);
            logger.info("Modified data to {}", sharedData.get());
            
            // 在持有写锁的同时获取读锁（锁降级的第一步）
            rwLock.readLock().lock();
            try {
                logger.info("Acquired read lock while holding write lock");
                
                // 现在可以释放写锁，但仍持有读锁
                rwLock.writeLock().unlock();
                logger.info("Released write lock, still holding read lock");
                
                // 现在只持有读锁，可以读取数据
                int value = sharedData.get();
                assertThat(value).isEqualTo(42);
                logger.info("Read data: {}", value);
                
                // 验证此时其他线程可以获取读锁但不能获取写锁
                CountDownLatch readerLatch = new CountDownLatch(1);
                Thread readerThread = new Thread(() -> {
                    if (rwLock.readLock().tryLock()) {
                        try {
                            logger.info("Another thread successfully acquired read lock");
                            readerLatch.countDown();
                        } finally {
                            rwLock.readLock().unlock();
                        }
                    }
                });
                
                readerThread.start();
                boolean readerSuccess = readerLatch.await(1, TimeUnit.SECONDS);
                assertThat(readerSuccess).isTrue();
                readerThread.join();
                
                // 验证其他线程无法获取写锁
                boolean writeAcquired = rwLock.writeLock().tryLock(100, TimeUnit.MILLISECONDS);
                assertThat(writeAcquired).isFalse();
                
            } finally {
                rwLock.readLock().unlock();
                logger.info("Released read lock - lock downgrade completed");
            }
        } catch (Exception e) {
            rwLock.writeLock().unlock();
            throw e;
        }
        
        logger.info("Lock downgrade test completed successfully");
    }
    
    /**
     * 演示不能进行锁升级（从读锁升级到写锁会导致死锁）
     */
    @Test
    @Timeout(3)
    public void testLockUpgradeNotSupported() throws InterruptedException {
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        
        rwLock.readLock().lock();
        try {
            logger.info("Acquired read lock");
            
            // 尝试在持有读锁的情况下获取写锁（这会失败）
            boolean writeAcquired = rwLock.writeLock().tryLock(100, TimeUnit.MILLISECONDS);
            assertThat(writeAcquired).isFalse();
            
            logger.info("Cannot acquire write lock while holding read lock - as expected");
        } finally {
            rwLock.readLock().unlock();
            logger.info("Released read lock");
        }
        
        // 现在应该可以获取写锁
        boolean writeAcquired = rwLock.writeLock().tryLock();
        assertThat(writeAcquired).isTrue();
        rwLock.writeLock().unlock();
        
        logger.info("Lock upgrade prevention test completed successfully");
    }
    
    /**
     * 测试读写锁在实际应用中的性能优势
     * 对比使用ReadWriteLock和普通Lock的性能差异
     */
    @Test
    @Timeout(10)
    public void testPerformanceComparison() throws InterruptedException {
        // 测试数据
        List<String> data = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            data.add("Item " + i);
        }
        
        int readerCount = 8;
        int writerCount = 2;
        int operationsPerThread = 100;
        
        // 使用ReadWriteLock
        long rwLockTime = testWithReadWriteLock(data, readerCount, writerCount, operationsPerThread);
        
        // 使用普通的ReentrantLock
        long normalLockTime = testWithNormalLock(data, readerCount, writerCount, operationsPerThread);
        
        logger.info("ReadWriteLock time: {} ms", rwLockTime);
        logger.info("Normal Lock time: {} ms", normalLockTime);
        
        // 在读多写少的场景下，ReadWriteLock应该有性能优势
        // 但由于测试环境的不确定性，我们只记录结果，不做严格断言
        double improvement = (double)(normalLockTime - rwLockTime) / normalLockTime * 100;
        logger.info("Performance improvement: {:.1f}%", improvement);
    }
    
    private long testWithReadWriteLock(List<String> data, int readerCount, int writerCount, int operationsPerThread) throws InterruptedException {
        ReadWriteLock rwLock = new ReentrantReadWriteLock();
        List<String> testData = new ArrayList<>(data);
        CountDownLatch latch = new CountDownLatch(readerCount + writerCount);
        
        long startTime = System.currentTimeMillis();
        
        // 启动读线程
        for (int i = 0; i < readerCount; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        rwLock.readLock().lock();
                        try {
                            // 模拟读操作
                            int size = testData.size();
                            if (size > 0) {
                                String item = testData.get(size / 2);
                            }
                        } finally {
                            rwLock.readLock().unlock();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        // 启动写线程
        for (int i = 0; i < writerCount; i++) {
            final int writerId = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        rwLock.writeLock().lock();
                        try {
                            // 模拟写操作
                            testData.add("Writer" + writerId + "-" + j);
                            if (testData.size() > 2000) {
                                testData.remove(0);
                            }
                        } finally {
                            rwLock.writeLock().unlock();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        latch.await();
        return System.currentTimeMillis() - startTime;
    }
    
    private long testWithNormalLock(List<String> data, int readerCount, int writerCount, int operationsPerThread) throws InterruptedException {
        Object lock = new Object();
        List<String> testData = new ArrayList<>(data);
        CountDownLatch latch = new CountDownLatch(readerCount + writerCount);
        
        long startTime = System.currentTimeMillis();
        
        // 启动读线程
        for (int i = 0; i < readerCount; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        synchronized (lock) {
                            // 模拟读操作
                            int size = testData.size();
                            if (size > 0) {
                                String item = testData.get(size / 2);
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        // 启动写线程
        for (int i = 0; i < writerCount; i++) {
            final int writerId = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        synchronized (lock) {
                            // 模拟写操作
                            testData.add("Writer" + writerId + "-" + j);
                            if (testData.size() > 2000) {
                                testData.remove(0);
                            }
                        }
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
     * 测试ReadWriteLock的状态查询方法
     */
    @Test
    public void testLockStateQueries() {
        ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
        
        // 初始状态
        assertThat(rwLock.getReadLockCount()).isEqualTo(0);
        assertThat(rwLock.isWriteLocked()).isFalse();
        assertThat(rwLock.getWriteHoldCount()).isEqualTo(0);
        
        // 获取读锁
        rwLock.readLock().lock();
        try {
            assertThat(rwLock.getReadLockCount()).isEqualTo(1);
            assertThat(rwLock.isWriteLocked()).isFalse();
            
            // 再次获取读锁（读锁可重入）
            rwLock.readLock().lock();
            try {
                assertThat(rwLock.getReadLockCount()).isEqualTo(2);
            } finally {
                rwLock.readLock().unlock();
            }
            assertThat(rwLock.getReadLockCount()).isEqualTo(1);
        } finally {
            rwLock.readLock().unlock();
        }
        
        assertThat(rwLock.getReadLockCount()).isEqualTo(0);
        
        // 获取写锁
        rwLock.writeLock().lock();
        try {
            assertThat(rwLock.isWriteLocked()).isTrue();
            assertThat(rwLock.getWriteHoldCount()).isEqualTo(1);
            assertThat(rwLock.isWriteLockedByCurrentThread()).isTrue();
            
            // 写锁重入
            rwLock.writeLock().lock();
            try {
                assertThat(rwLock.getWriteHoldCount()).isEqualTo(2);
            } finally {
                rwLock.writeLock().unlock();
            }
            assertThat(rwLock.getWriteHoldCount()).isEqualTo(1);
        } finally {
            rwLock.writeLock().unlock();
        }
        
        assertThat(rwLock.isWriteLocked()).isFalse();
        assertThat(rwLock.getWriteHoldCount()).isEqualTo(0);
        
        logger.info("ReadWriteLock state query test completed successfully");
    }
} 