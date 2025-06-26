package com.javastudy.lock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

/**
 * synchronized 关键字测试用例
 * 
 * synchronized是Java中最基本的同步机制，它提供了互斥访问和内存可见性保证。
 * 
 * 核心概念：
 * 1. 内置锁：每个Java对象都有一个内置锁（monitor lock）
 * 2. 互斥性：同一时间只有一个线程可以进入同步代码块
 * 3. 重入性：同一线程可以重复获取已持有的锁
 * 4. 内存可见性：synchronized保证了happens-before关系
 * 5. 自动释放：锁会在退出同步块时自动释放
 * 
 * 使用方式：
 * 1. 同步方法：synchronized method
 * 2. 同步代码块：synchronized(object)
 * 3. 静态同步方法：synchronized static method
 * 4. 类锁：synchronized(Class.class)
 * 
 * 适用场景：
 * - 简单的互斥访问需求
 * - 保护共享资源的完整性
 * - 实现线程安全的数据结构
 */
public class SynchronizedTest {
    
    private static final Logger logger = LoggerFactory.getLogger(SynchronizedTest.class);
    
    /**
     * 测试synchronized方法的互斥性
     */
    @Test
    @Timeout(5)
    public void testSynchronizedMethod() throws InterruptedException {
        Counter counter = new Counter();
        int threadCount = 10;
        int incrementsPerThread = 1000;
        
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // 启动多个线程并发递增计数器
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < incrementsPerThread; j++) {
                        counter.increment();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        executor.shutdown();
        
        // 验证结果：由于synchronized的保护，结果应该是准确的
        int expected = threadCount * incrementsPerThread;
        assertThat(counter.getValue()).isEqualTo(expected);
        
        logger.info("Synchronized method test completed. Final count: {}", counter.getValue());
    }
    
    /**
     * 测试synchronized代码块
     */
    @Test
    @Timeout(5)
    public void testSynchronizedBlock() throws InterruptedException {
        List<String> synchronizedList = new ArrayList<>();
        Object lock = new Object();
        int threadCount = 5;
        int itemsPerThread = 100;
        
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // 使用synchronized块保护ArrayList的并发访问
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < itemsPerThread; j++) {
                        synchronized (lock) {
                            synchronizedList.add("Thread-" + threadId + "-Item-" + j);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        latch.await();
        
        // 验证结果
        assertThat(synchronizedList).hasSize(threadCount * itemsPerThread);
        
        // 验证没有数据丢失（所有元素都是唯一的）
        List<String> sortedList = new ArrayList<>(synchronizedList);
        Collections.sort(sortedList);
        assertThat(sortedList).doesNotHaveDuplicates();
        
        logger.info("Synchronized block test completed. List size: {}", synchronizedList.size());
    }
    
    /**
     * 测试synchronized的重入性
     */
    @Test
    public void testSynchronizedReentrant() {
        ReentrantExample example = new ReentrantExample();
        
        // 调用会触发重入的方法
        String result = example.outerMethod();
        assertThat(result).isEqualTo("outer-inner-innermost");
        
        logger.info("Synchronized reentrant test completed. Result: {}", result);
    }
    
    /**
     * 测试静态synchronized方法（类锁）
     */
    @Test
    @Timeout(5)
    public void testStaticSynchronized() throws InterruptedException {
        int threadCount = 8;
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        // 重置静态计数器
        StaticCounter.reset();
        
        // 启动多个线程调用静态synchronized方法
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < 1000; j++) {
                        StaticCounter.increment();
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }
        
        latch.await();
        
        // 验证静态方法的同步效果
        assertThat(StaticCounter.getValue()).isEqualTo(threadCount * 1000);
        
        logger.info("Static synchronized test completed. Final count: {}", StaticCounter.getValue());
    }
    
    /**
     * 测试wait/notify机制
     * synchronized是wait/notify的前提条件
     */
    @Test
    @Timeout(5)
    public void testWaitNotify() throws InterruptedException {
        WaitNotifyExample example = new WaitNotifyExample();
        CountDownLatch consumerStarted = new CountDownLatch(1);
        CountDownLatch producerFinished = new CountDownLatch(1);
        CountDownLatch consumerFinished = new CountDownLatch(1);
        
        // 消费者线程
        Thread consumer = new Thread(() -> {
            try {
                consumerStarted.countDown();
                String data = example.consume();
                logger.info("Consumer received: {}", data);
                assertThat(data).isEqualTo("Important Data");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                consumerFinished.countDown();
            }
        });
        
        // 生产者线程
        Thread producer = new Thread(() -> {
            try {
                consumerStarted.await(); // 等待消费者开始等待
                Thread.sleep(100); // 确保消费者进入wait状态
                
                example.produce("Important Data");
                logger.info("Producer sent data");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                producerFinished.countDown();
            }
        });
        
        consumer.start();
        producer.start();
        
        // 等待完成
        boolean producerCompleted = producerFinished.await(3, TimeUnit.SECONDS);
        boolean consumerCompleted = consumerFinished.await(2, TimeUnit.SECONDS);
        
        assertThat(producerCompleted).isTrue();
        assertThat(consumerCompleted).isTrue();
        
        logger.info("Wait/notify test completed successfully");
    }
    
    /**
     * 测试synchronized的性能特征
     */
    @Test
    @Timeout(8)
    public void testSynchronizedPerformance() throws InterruptedException {
        int threadCount = 4;
        int operationsPerThread = 100000;
        
        // 测试synchronized方法的性能
        long syncTime = testPerformance(threadCount, operationsPerThread, true);
        
        // 测试无同步的性能（仅作对比，结果可能不准确）
        long unsyncTime = testPerformance(threadCount, operationsPerThread, false);
        
        logger.info("Synchronized performance: {} ms", syncTime);
        logger.info("Unsynchronized performance: {} ms", unsyncTime);
        logger.info("Synchronization overhead: {} ms", syncTime - unsyncTime);
        
        // synchronized肯定会有性能开销
        assertThat(syncTime).isGreaterThan(0);
    }
    
    private long testPerformance(int threadCount, int operationsPerThread, boolean useSynchronized) throws InterruptedException {
        PerformanceCounter counter = new PerformanceCounter();
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        if (useSynchronized) {
                            counter.synchronizedIncrement();
                        } else {
                            counter.unsynchronizedIncrement();
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
     * 测试synchronized的内存可见性
     */
    @Test
    @Timeout(3)
    public void testSynchronizedVisibility() throws InterruptedException {
        VisibilityExample example = new VisibilityExample();
        CountDownLatch writerStarted = new CountDownLatch(1);
        CountDownLatch readerFinished = new CountDownLatch(1);
        
        // 读线程
        Thread reader = new Thread(() -> {
            try {
                writerStarted.await();
                Thread.sleep(10); // 确保写线程有时间修改值
                
                boolean seen = example.readFlag();
                logger.info("Reader saw flag: {}", seen);
                
                // 由于synchronized的内存可见性保证，读线程应该能看到修改
                assertThat(seen).isTrue();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                readerFinished.countDown();
            }
        });
        
        // 写线程
        Thread writer = new Thread(() -> {
            writerStarted.countDown();
            example.setFlag(true);
            logger.info("Writer set flag to true");
        });
        
        reader.start();
        writer.start();
        
        boolean completed = readerFinished.await(2, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        
        logger.info("Synchronized visibility test completed successfully");
    }
    
    /**
     * 简单的计数器类，使用synchronized方法保护
     */
    private static class Counter {
        private int count = 0;
        
        public synchronized void increment() {
            count++;
        }
        
        public synchronized int getValue() {
            return count;
        }
    }
    
    /**
     * 演示synchronized重入性的示例
     */
    private static class ReentrantExample {
        
        public synchronized String outerMethod() {
            logger.debug("Entering outerMethod");
            return "outer-" + innerMethod();
        }
        
        public synchronized String innerMethod() {
            logger.debug("Entering innerMethod");
            return "inner-" + innermostMethod();
        }
        
        public synchronized String innermostMethod() {
            logger.debug("Entering innermostMethod");
            return "innermost";
        }
    }
    
    /**
     * 静态计数器，演示类锁的使用
     */
    private static class StaticCounter {
        private static int count = 0;
        
        public static synchronized void increment() {
            count++;
        }
        
        public static synchronized int getValue() {
            return count;
        }
        
        public static synchronized void reset() {
            count = 0;
        }
    }
    
    /**
     * wait/notify示例
     */
    private static class WaitNotifyExample {
        private String data = null;
        private boolean dataReady = false;
        
        public synchronized String consume() throws InterruptedException {
            while (!dataReady) {
                logger.debug("Consumer waiting for data...");
                wait(); // 释放锁并等待
            }
            
            String result = data;
            dataReady = false;
            data = null;
            
            return result;
        }
        
        public synchronized void produce(String newData) {
            data = newData;
            dataReady = true;
            logger.debug("Producer set data and notifying...");
            notify(); // 唤醒等待的线程
        }
    }
    
    /**
     * 性能测试用的计数器
     */
    private static class PerformanceCounter {
        private int syncCount = 0;
        private int unsyncCount = 0;
        
        public synchronized void synchronizedIncrement() {
            syncCount++;
        }
        
        public void unsynchronizedIncrement() {
            unsyncCount++; // 注意：这是线程不安全的，仅用于性能对比
        }
        
        public synchronized int getSyncCount() {
            return syncCount;
        }
        
        public int getUnsyncCount() {
            return unsyncCount;
        }
    }
    
    /**
     * 内存可见性测试示例
     */
    private static class VisibilityExample {
        private boolean flag = false;
        
        public synchronized void setFlag(boolean value) {
            flag = value;
        }
        
        public synchronized boolean readFlag() {
            return flag;
        }
    }
} 