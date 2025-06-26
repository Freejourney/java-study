package com.javastudy.lock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * ReentrantLock 测试用例
 * 
 * ReentrantLock是Java中最常用的显式锁，它提供了与synchronized关键字相同的功能，
 * 但具有更大的灵活性和更多的功能特性。
 * 
 * 核心特性：
 * 1. 可重入性（Reentrancy）：同一个线程可以多次获取同一个锁
 * 2. 可中断性（Interruptible）：支持响应中断
 * 3. 可超时性（Timeout）：支持超时获取锁
 * 4. 公平性（Fairness）：支持公平锁和非公平锁
 * 5. 条件变量（Condition）：支持多个条件变量
 */
public class ReentrantLockTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ReentrantLockTest.class);
    
    /**
     * 测试基本的锁机制
     * 演示如何使用lock()和unlock()方法保护临界区
     */
    @Test
    @Timeout(5)
    public void testBasicLockUnlock() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        AtomicInteger counter = new AtomicInteger(0);
        int threadCount = 10;
        int incrementsPerThread = 1000;
        
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // 启动多个线程并发地修改计数器
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        lock.lock(); // 获取锁
                        try {
                            // 临界区：非原子操作，需要同步保护
                            int current = counter.get();
                            // 模拟一些处理时间（移除sleep以避免测试超时）
                            counter.set(current + 1);
                        } finally {
                            lock.unlock(); // 释放锁（必须在finally块中）
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await(); // 等待所有线程完成
        executor.shutdown();
        
        // 验证结果
        assertThat(counter.get()).isEqualTo(threadCount * incrementsPerThread);
        assertThat(lock.isLocked()).isFalse(); // 确保所有锁都被释放
        logger.info("Basic lock test completed. Final counter value: {}", counter.get());
    }
    
    /**
     * 测试可重入性
     * 证明同一个线程可以多次获取同一个锁
     */
    @Test
    public void testReentrantFeature() {
        ReentrantLock lock = new ReentrantLock();
        
        lock.lock();
        try {
            assertThat(lock.getHoldCount()).isEqualTo(1); // 持有计数为1
            assertThat(lock.isHeldByCurrentThread()).isTrue(); // 当前线程持有锁
            
            // 再次获取锁（重入）
            lock.lock();
            try {
                assertThat(lock.getHoldCount()).isEqualTo(2); // 持有计数为2
                
                // 第三次获取锁
                lock.lock();
                try {
                    assertThat(lock.getHoldCount()).isEqualTo(3); // 持有计数为3
                    logger.info("Successfully acquired lock 3 times by the same thread");
                } finally {
                    lock.unlock(); // 持有计数减1
                }
                assertThat(lock.getHoldCount()).isEqualTo(2);
            } finally {
                lock.unlock(); // 持有计数减1
            }
            assertThat(lock.getHoldCount()).isEqualTo(1);
        } finally {
            lock.unlock(); // 持有计数减1，锁被完全释放
        }
        
        assertThat(lock.getHoldCount()).isEqualTo(0); // 锁完全释放
        assertThat(lock.isLocked()).isFalse();
    }
    
    /**
     * 测试tryLock()方法
     * 演示非阻塞获取锁的方式
     */
    @Test
    @Timeout(3)
    public void testTryLock() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch latch = new CountDownLatch(1);
        
        // 第一个线程获取锁并持有一段时间
        Thread thread1 = new Thread(() -> {
            if (lock.tryLock()) {
                try {
                    logger.info("Thread 1 acquired lock");
                    latch.countDown(); // 通知主线程
                    Thread.sleep(1000); // 持有锁1秒
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    lock.unlock();
                    logger.info("Thread 1 released lock");
                }
            }
        });
        
        thread1.start();
        latch.await(); // 等待thread1获取锁
        
        // 主线程尝试获取锁（应该失败）
        boolean acquired = lock.tryLock();
        assertThat(acquired).isFalse(); // 锁已被占用，tryLock应该返回false
        logger.info("Main thread failed to acquire lock as expected");
        
        thread1.join(); // 等待thread1完成
        
        // 现在锁应该可用了
        acquired = lock.tryLock();
        assertThat(acquired).isTrue(); // 锁现在应该可用
        lock.unlock();
        logger.info("Main thread successfully acquired lock after thread1 finished");
    }
    
    /**
     * 测试带超时的tryLock()方法
     * 演示超时获取锁的功能
     */
    @Test
    @Timeout(5)
    public void testTryLockWithTimeout() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch startLatch = new CountDownLatch(1);
        
        // 启动一个线程持有锁2秒
        Thread holdingThread = new Thread(() -> {
            lock.lock();
            try {
                logger.info("Holding thread acquired lock");
                startLatch.countDown();
                Thread.sleep(2000); // 持有锁2秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
                logger.info("Holding thread released lock");
            }
        });
        
        holdingThread.start();
        startLatch.await(); // 等待锁被占用
        
        // 尝试在1秒内获取锁（应该超时）
        long startTime = System.currentTimeMillis();
        boolean acquired = lock.tryLock(1, TimeUnit.SECONDS);
        long elapsedTime = System.currentTimeMillis() - startTime;
        
        assertThat(acquired).isFalse(); // 应该超时失败
        assertThat(elapsedTime).isGreaterThanOrEqualTo(1000); // 确实等待了约1秒
        assertThat(elapsedTime).isLessThan(1500); // 但没有等待太久
        logger.info("TryLock with timeout failed as expected after {} ms", elapsedTime);
        
        holdingThread.join();
        
        // 现在应该可以立即获取锁
        acquired = lock.tryLock(1, TimeUnit.SECONDS);
        assertThat(acquired).isTrue();
        lock.unlock();
        logger.info("Successfully acquired lock after holding thread finished");
    }
    
    /**
     * 测试可中断的锁获取
     * 演示lockInterruptibly()方法的使用
     */
    @Test
    @Timeout(3)
    public void testLockInterruptibly() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch interruptedLatch = new CountDownLatch(1);
        
        // 启动一个线程持有锁
        Thread holdingThread = new Thread(() -> {
            lock.lock();
            try {
                logger.info("Holding thread acquired lock");
                startLatch.countDown();
                Thread.sleep(5000); // 持有锁5秒
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        });
        
        // 启动另一个线程尝试可中断地获取锁
        Thread waitingThread = new Thread(() -> {
            try {
                startLatch.await(); // 等待锁被占用
                logger.info("Waiting thread attempting to acquire lock interruptibly");
                lock.lockInterruptibly(); // 可中断地获取锁
                try {
                    logger.info("Waiting thread acquired lock");
                } finally {
                    lock.unlock();
                }
            } catch (InterruptedException e) {
                logger.info("Waiting thread was interrupted while waiting for lock");
                interruptedLatch.countDown();
            }
        });
        
        holdingThread.start();
        waitingThread.start();
        
        startLatch.await(); // 等待锁被占用
        Thread.sleep(100); // 确保waiting线程开始等待锁
        
        // 中断等待线程
        waitingThread.interrupt();
        
        // 验证等待线程被中断
        boolean wasInterrupted = interruptedLatch.await(1, TimeUnit.SECONDS);
        assertThat(wasInterrupted).isTrue();
        
        holdingThread.interrupt(); // 清理holding线程
        holdingThread.join();
        waitingThread.join();
        
        logger.info("Lock interruption test completed successfully");
    }
    
    /**
     * 测试公平锁与非公平锁的行为差异
     */
    @Test
    @Timeout(10)
    public void testFairVsUnfairLock() throws InterruptedException {
        // 测试非公平锁（默认）
        testLockFairness(new ReentrantLock(false), "Non-fair");
        
        // 测试公平锁
        testLockFairness(new ReentrantLock(true), "Fair");
    }
    
    private void testLockFairness(ReentrantLock lock, String lockType) throws InterruptedException {
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);
        AtomicInteger executionOrder = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await(); // 等待所有线程准备就绪
                    
                    lock.lock();
                    try {
                        int order = executionOrder.incrementAndGet();
                        logger.info("{} lock - Thread {} executed in order {}", 
                                   lockType, threadId, order);
                        Thread.sleep(10); // 短暂持有锁
                    } finally {
                        lock.unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            }).start();
        }
        
        Thread.sleep(100); // 让所有线程都开始等待
        startLatch.countDown(); // 同时释放所有线程
        
        boolean finished = finishLatch.await(5, TimeUnit.SECONDS);
        assertThat(finished).isTrue();
        
        logger.info("{} lock test completed", lockType);
    }
    
    /**
     * 测试锁的状态查询方法
     */
    @Test
    public void testLockStateQueries() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        
        // 初始状态
        assertThat(lock.isLocked()).isFalse();
        assertThat(lock.isHeldByCurrentThread()).isFalse();
        assertThat(lock.getHoldCount()).isEqualTo(0);
        assertThat(lock.getQueueLength()).isEqualTo(0);
        assertThat(lock.hasQueuedThreads()).isFalse();
        
        // 获取锁后的状态
        lock.lock();
        try {
            assertThat(lock.isLocked()).isTrue();
            assertThat(lock.isHeldByCurrentThread()).isTrue();
            assertThat(lock.getHoldCount()).isEqualTo(1);
            
            // 重入锁
            lock.lock();
            try {
                assertThat(lock.getHoldCount()).isEqualTo(2);
            } finally {
                lock.unlock();
            }
            assertThat(lock.getHoldCount()).isEqualTo(1);
        } finally {
            lock.unlock();
        }
        
        // 释放锁后的状态
        assertThat(lock.isLocked()).isFalse();
        assertThat(lock.isHeldByCurrentThread()).isFalse();
        assertThat(lock.getHoldCount()).isEqualTo(0);
        
        logger.info("Lock state query test completed successfully");
    }
} 