package com.javastudy.lock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Queue;
import java.util.ArrayDeque;

import static org.assertj.core.api.Assertions.*;

/**
 * Condition 测试用例
 * 
 * Condition提供了一种线程间的协调机制，允许线程在某些条件不满足时等待，
 * 并在条件满足时被唤醒。它比Object的wait/notify机制更加灵活和强大。
 * 
 * 核心概念：
 * 1. await()：当前线程等待，直到被唤醒或中断
 * 2. signal()：唤醒一个等待的线程
 * 3. signalAll()：唤醒所有等待的线程
 * 4. 与锁绑定：Condition必须与Lock一起使用
 * 5. 多条件：一个锁可以关联多个Condition
 * 
 * 适用场景：
 * - 生产者消费者模式
 * - 有界缓冲区
 * - 线程池的任务调度
 * - 复杂的线程协调场景
 */
public class ConditionTest {
    
    private static final Logger logger = LoggerFactory.getLogger(ConditionTest.class);
    
    /**
     * 基本的生产者消费者模式
     * 演示Condition的基本用法
     */
    @Test
    @Timeout(5)
    public void testBasicProducerConsumer() throws InterruptedException {
        BoundedBuffer<String> buffer = new BoundedBuffer<>(3);
        int itemCount = 10;
        
        CountDownLatch producerDone = new CountDownLatch(1);
        CountDownLatch consumerDone = new CountDownLatch(1);
        
        // 生产者线程
        Thread producer = new Thread(() -> {
            try {
                for (int i = 0; i < itemCount; i++) {
                    String item = "Item-" + i;
                    buffer.put(item);
                    logger.info("Produced: {}", item);
                    Thread.sleep(50); // 模拟生产时间
                }
                logger.info("Producer finished");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                producerDone.countDown();
            }
        });
        
        // 消费者线程
        Thread consumer = new Thread(() -> {
            try {
                for (int i = 0; i < itemCount; i++) {
                    String item = buffer.take();
                    logger.info("Consumed: {}", item);
                    assertThat(item).isEqualTo("Item-" + i);
                    Thread.sleep(80); // 模拟消费时间
                }
                logger.info("Consumer finished");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                consumerDone.countDown();
            }
        });
        
        producer.start();
        consumer.start();
        
        // 等待完成
        boolean producerCompleted = producerDone.await(3, TimeUnit.SECONDS);
        boolean consumerCompleted = consumerDone.await(3, TimeUnit.SECONDS);
        
        assertThat(producerCompleted).isTrue();
        assertThat(consumerCompleted).isTrue();
        assertThat(buffer.size()).isEqualTo(0);
        
        logger.info("Basic producer-consumer test completed successfully");
    }
    
    /**
     * 多生产者多消费者模式
     * 演示Condition在复杂场景下的使用
     */
    @Test
    @Timeout(8)
    public void testMultipleProducersConsumers() throws InterruptedException {
        BoundedBuffer<Integer> buffer = new BoundedBuffer<>(5);
        int producerCount = 3;
        int consumerCount = 2;
        int itemsPerProducer = 10;
        
        CountDownLatch allProducersDone = new CountDownLatch(producerCount);
        CountDownLatch allConsumersDone = new CountDownLatch(consumerCount);
        AtomicInteger totalProduced = new AtomicInteger(0);
        AtomicInteger totalConsumed = new AtomicInteger(0);
        
        // 启动多个生产者
        for (int i = 0; i < producerCount; i++) {
            final int producerId = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < itemsPerProducer; j++) {
                        int item = producerId * 1000 + j;
                        buffer.put(item);
                        totalProduced.incrementAndGet();
                        logger.info("Producer {} produced: {}", producerId, item);
                        Thread.sleep(20);
                    }
                    logger.info("Producer {} finished", producerId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allProducersDone.countDown();
                }
            }).start();
        }
        
        // 启动多个消费者
        for (int i = 0; i < consumerCount; i++) {
            final int consumerId = i;
            new Thread(() -> {
                try {
                    while (true) {
                        Integer item = buffer.take();
                        if (item != null && item == -1) break; // 结束信号
                        
                        totalConsumed.incrementAndGet();
                        logger.info("Consumer {} consumed: {}", consumerId, item);
                        Thread.sleep(30);
                    }
                    logger.info("Consumer {} finished", consumerId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allConsumersDone.countDown();
                }
            }).start();
        }
        
        // 等待所有生产者完成
        boolean producersCompleted = allProducersDone.await(5, TimeUnit.SECONDS);
        assertThat(producersCompleted).isTrue();
        
        // 发送结束信号给消费者
        for (int i = 0; i < consumerCount; i++) {
            buffer.put(-1); // 使用-1作为结束信号而不是null
        }
        
        // 等待所有消费者完成
        boolean consumersCompleted = allConsumersDone.await(3, TimeUnit.SECONDS);
        assertThat(consumersCompleted).isTrue();
        
        // 验证结果
        int expectedTotal = producerCount * itemsPerProducer;
        assertThat(totalProduced.get()).isEqualTo(expectedTotal);
        assertThat(totalConsumed.get()).isEqualTo(expectedTotal);
        
        logger.info("Multiple producers-consumers test completed. Total items: {}", expectedTotal);
    }
    
    /**
     * 测试Condition的超时等待
     */
    @Test
    @Timeout(3)
    public void testConditionTimeout() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        Condition condition = lock.newCondition();
        
        lock.lock();
        try {
            // 等待一个永远不会发生的条件，应该超时
            long startTime = System.currentTimeMillis();
            boolean signaled = condition.await(500, TimeUnit.MILLISECONDS);
            long elapsedTime = System.currentTimeMillis() - startTime;
            
            assertThat(signaled).isFalse(); // 应该超时返回false
            assertThat(elapsedTime).isGreaterThanOrEqualTo(500); // 确实等待了指定时间
            assertThat(elapsedTime).isLessThan(600); // 但没有等待太久
            
            logger.info("Condition timeout test completed after {} ms", elapsedTime);
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 测试多个Condition的使用
     * 演示一个锁如何关联多个条件
     */
    @Test
    @Timeout(5)
    public void testMultipleConditions() throws InterruptedException {
        MultiConditionExample example = new MultiConditionExample();
        
        CountDownLatch readersReady = new CountDownLatch(2);
        CountDownLatch writersReady = new CountDownLatch(2);
        CountDownLatch allDone = new CountDownLatch(4);
        
        // 启动读线程
        for (int i = 0; i < 2; i++) {
            final int readerId = i;
            new Thread(() -> {
                try {
                    readersReady.countDown();
                    String data = example.read();
                    logger.info("Reader {} read: {}", readerId, data);
                    assertThat(data).isNotNull();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allDone.countDown();
                }
            }).start();
        }
        
        // 启动写线程
        for (int i = 0; i < 2; i++) {
            final int writerId = i;
            new Thread(() -> {
                try {
                    writersReady.countDown();
                    example.write("Data from writer " + writerId);
                    logger.info("Writer {} completed write", writerId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    allDone.countDown();
                }
            }).start();
        }
        
        // 等待所有线程准备就绪
        readersReady.await();
        writersReady.await();
        Thread.sleep(100);
        
        // 允许读写操作
        example.enableOperations();
        
        // 等待所有操作完成
        boolean completed = allDone.await(3, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        
        logger.info("Multiple conditions test completed successfully");
    }
    
    /**
     * 有界缓冲区实现
     * 使用两个Condition分别处理"非满"和"非空"条件
     */
    private static class BoundedBuffer<T> {
        private final Queue<T> queue = new ArrayDeque<>();
        private final int capacity;
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition notFull = lock.newCondition();  // 缓冲区未满条件
        private final Condition notEmpty = lock.newCondition(); // 缓冲区非空条件
        
        public BoundedBuffer(int capacity) {
            this.capacity = capacity;
        }
        
        public void put(T item) throws InterruptedException {
            lock.lock();
            try {
                // 等待缓冲区不满
                while (queue.size() == capacity) {
                    logger.debug("Buffer full, producer waiting...");
                    notFull.await();
                }
                
                queue.offer(item);
                logger.debug("Item added to buffer, size: {}", queue.size());
                
                // 通知等待的消费者
                notEmpty.signal();
            } finally {
                lock.unlock();
            }
        }
        
        public T take() throws InterruptedException {
            lock.lock();
            try {
                // 等待缓冲区非空
                while (queue.isEmpty()) {
                    logger.debug("Buffer empty, consumer waiting...");
                    notEmpty.await();
                }
                
                T item = queue.poll();
                logger.debug("Item taken from buffer, size: {}", queue.size());
                
                // 通知等待的生产者
                notFull.signal();
                
                return item;
            } finally {
                lock.unlock();
            }
        }
        
        public int size() {
            lock.lock();
            try {
                return queue.size();
            } finally {
                lock.unlock();
            }
        }
    }
    
    /**
     * 多条件示例
     * 演示如何使用多个Condition来协调不同类型的操作
     */
    private static class MultiConditionExample {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition canRead = lock.newCondition();
        private final Condition canWrite = lock.newCondition();
        private String data = null;
        private boolean operationsEnabled = false;
        
        public String read() throws InterruptedException {
            lock.lock();
            try {
                // 等待可以读取
                while (!operationsEnabled || data == null) {
                    logger.debug("Waiting for read condition...");
                    canRead.await();
                }
                
                String result = data;
                data = null; // 读取后清空数据
                
                // 通知写线程可以写入
                canWrite.signal();
                
                return result;
            } finally {
                lock.unlock();
            }
        }
        
        public void write(String newData) throws InterruptedException {
            lock.lock();
            try {
                // 等待可以写入
                while (!operationsEnabled || data != null) {
                    logger.debug("Waiting for write condition...");
                    canWrite.await();
                }
                
                data = newData;
                
                // 通知读线程可以读取
                canRead.signal();
            } finally {
                lock.unlock();
            }
        }
        
        public void enableOperations() {
            lock.lock();
            try {
                operationsEnabled = true;
                // 同时唤醒读和写线程
                canRead.signalAll();
                canWrite.signalAll();
            } finally {
                lock.unlock();
            }
        }
    }
} 