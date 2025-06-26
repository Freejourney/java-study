package com.javastudy.lock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Assertions.*;

/**
 * 同步工具类测试用例
 * 
 * Java并发包提供了几个非常有用的同步工具类：
 * 1. CountDownLatch：倒计时门闩，用于等待一组线程完成
 * 2. CyclicBarrier：循环屏障，用于让一组线程互相等待到达某个同步点
 * 3. Semaphore：信号量，用于控制对资源的并发访问数量
 * 4. Exchanger：交换器，用于两个线程间的数据交换
 * 5. Phaser：相位器，更灵活的同步屏障
 * 
 * 这些工具类在不同的并发场景下各有优势，是构建复杂并发应用的基础。
 */
public class SynchronizationUtilitiesTest {
    
    private static final Logger logger = LoggerFactory.getLogger(SynchronizationUtilitiesTest.class);
    
    /**
     * CountDownLatch 测试
     * 
     * CountDownLatch是一个同步辅助类，允许一个或多个线程等待其他线程完成操作。
     * 
     * 核心概念：
     * 1. 计数器：初始化时设定一个计数值
     * 2. countDown()：计数器减1
     * 3. await()：等待计数器归零
     * 4. 一次性：计数器归零后不能重置
     * 
     * 适用场景：
     * - 主线程等待多个工作线程完成初始化
     * - 多个线程等待某个服务启动完成
     * - 实现并发任务的协调
     */
    @Test
    @Timeout(5)
    public void testCountDownLatch() throws InterruptedException {
        int workerCount = 5;
        CountDownLatch startSignal = new CountDownLatch(1); // 启动信号
        CountDownLatch doneSignal = new CountDownLatch(workerCount); // 完成信号
        
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        
        // 创建工作线程
        for (int i = 0; i < workerCount; i++) {
            final int workerId = i;
            new Thread(() -> {
                try {
                    // 等待启动信号
                    startSignal.await();
                    logger.info("Worker {} started working", workerId);
                    
                    // 模拟工作
                    Thread.sleep(100 + workerId * 50);
                    results.add("Worker-" + workerId + "-completed");
                    
                    logger.info("Worker {} completed work", workerId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    // 工作完成，计数器减1
                    doneSignal.countDown();
                }
            }).start();
        }
        
        logger.info("All workers created, sending start signal");
        
        // 发送启动信号，所有工作线程同时开始
        startSignal.countDown();
        
        // 等待所有工作线程完成
        boolean allCompleted = doneSignal.await(3, TimeUnit.SECONDS);
        assertThat(allCompleted).isTrue();
        
        // 验证结果
        assertThat(results).hasSize(workerCount);
        logger.info("All workers completed. Results: {}", results);
        
        // 验证CountDownLatch的状态
        assertThat(doneSignal.getCount()).isEqualTo(0);
        
        // 再次调用await()应该立即返回，因为计数已经为0
        boolean immediateReturn = doneSignal.await(100, TimeUnit.MILLISECONDS);
        assertThat(immediateReturn).isTrue();
        
        logger.info("CountDownLatch test completed successfully");
    }
    
    /**
     * 测试CountDownLatch的边界情况
     */
    @Test
    @Timeout(3)
    public void testCountDownLatchEdgeCases() throws InterruptedException {
        // 测试计数为0的CountDownLatch
        CountDownLatch zeroLatch = new CountDownLatch(0);
        assertThat(zeroLatch.getCount()).isEqualTo(0);
        
        // 应该立即返回
        boolean immediate = zeroLatch.await(10, TimeUnit.MILLISECONDS);
        assertThat(immediate).isTrue();
        
        // 测试多次countDown()的效果
        CountDownLatch multiLatch = new CountDownLatch(3);
        assertThat(multiLatch.getCount()).isEqualTo(3);
        
        multiLatch.countDown();
        assertThat(multiLatch.getCount()).isEqualTo(2);
        
        multiLatch.countDown();
        assertThat(multiLatch.getCount()).isEqualTo(1);
        
        multiLatch.countDown();
        assertThat(multiLatch.getCount()).isEqualTo(0);
        
        // 额外的countDown()不会使计数变为负数
        multiLatch.countDown();
        assertThat(multiLatch.getCount()).isEqualTo(0);
        
        logger.info("CountDownLatch edge cases test completed successfully");
    }
    
    /**
     * CyclicBarrier 测试
     * 
     * CyclicBarrier是一个同步辅助类，允许一组线程互相等待，直到到达某个公共屏障点。
     * 
     * 核心概念：
     * 1. 屏障点：所有参与线程都到达的同步点
     * 2. 循环性：屏障可以重复使用
     * 3. 屏障动作：所有线程到达屏障点时执行的可选动作
     * 4. 破坏：屏障可以被重置或破坏
     * 
     * 适用场景：
     * - 多阶段任务的同步点
     * - 并行计算中的分阶段处理
     * - 游戏中的回合制同步
     */
    @Test
    @Timeout(8)
    public void testCyclicBarrier() throws InterruptedException {
        int partyCount = 4;
        AtomicInteger barrierActionCount = new AtomicInteger(0);
        
        // 创建带屏障动作的CyclicBarrier
        CyclicBarrier barrier = new CyclicBarrier(partyCount, () -> {
            int round = barrierActionCount.incrementAndGet();
            logger.info("Barrier action executed for round {}", round);
        });
        
        CountDownLatch allThreadsDone = new CountDownLatch(partyCount);
        List<String> executionOrder = Collections.synchronizedList(new ArrayList<>());
        
        // 创建参与线程
        for (int i = 0; i < partyCount; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    // 第一阶段工作
                    Thread.sleep((threadId + 1) * 100);
                    executionOrder.add("Thread-" + threadId + "-Phase1");
                    logger.info("Thread {} completed phase 1", threadId);
                    
                    // 等待所有线程完成第一阶段
                    int arrival1 = barrier.await(2, TimeUnit.SECONDS);
                    logger.info("Thread {} passed barrier 1, arrival index: {}", threadId, arrival1);
                    
                    // 第二阶段工作
                    Thread.sleep((partyCount - threadId) * 50);
                    executionOrder.add("Thread-" + threadId + "-Phase2");
                    logger.info("Thread {} completed phase 2", threadId);
                    
                    // 等待所有线程完成第二阶段
                    int arrival2 = barrier.await(2, TimeUnit.SECONDS);
                    logger.info("Thread {} passed barrier 2, arrival index: {}", threadId, arrival2);
                    
                    executionOrder.add("Thread-" + threadId + "-Finished");
                    
                } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                    logger.error("Thread {} failed at barrier", threadId, e);
                } finally {
                    allThreadsDone.countDown();
                }
            }).start();
        }
        
        // 等待所有线程完成
        boolean completed = allThreadsDone.await(6, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        
        // 验证屏障动作执行了两次（两个阶段）
        assertThat(barrierActionCount.get()).isEqualTo(2);
        
        // 验证执行顺序：所有线程的Phase1应该在任何Phase2之前完成
        List<String> phase1Events = executionOrder.stream()
                .filter(event -> event.contains("Phase1"))
                .toList();
        List<String> phase2Events = executionOrder.stream()
                .filter(event -> event.contains("Phase2"))
                .toList();
        
        assertThat(phase1Events).hasSize(partyCount);
        assertThat(phase2Events).hasSize(partyCount);
        
        logger.info("Execution order: {}", executionOrder);
        logger.info("CyclicBarrier test completed successfully");
    }
    
    /**
     * 测试CyclicBarrier的重置和破坏功能
     */
    @Test
    @Timeout(5)
    public void testCyclicBarrierResetAndBreak() throws InterruptedException {
        int partyCount = 3;
        CyclicBarrier barrier = new CyclicBarrier(partyCount);
        
        CountDownLatch interruptedLatch = new CountDownLatch(2);
        
        // 启动两个线程等待屏障
        for (int i = 0; i < 2; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    logger.info("Thread {} waiting at barrier", threadId);
                    barrier.await();
                    logger.info("Thread {} passed barrier", threadId);
                } catch (BrokenBarrierException e) {
                    logger.info("Thread {} encountered broken barrier", threadId);
                    interruptedLatch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    interruptedLatch.countDown();
                }
            }).start();
        }
        
        Thread.sleep(100); // 让线程开始等待
        
        // 重置屏障，这会导致等待的线程抛出BrokenBarrierException
        barrier.reset();
        logger.info("Barrier reset");
        
        // 等待线程处理BrokenBarrierException
        boolean interrupted = interruptedLatch.await(2, TimeUnit.SECONDS);
        assertThat(interrupted).isTrue();
        
        // 验证屏障状态
        assertThat(barrier.isBroken()).isFalse(); // reset后屏障应该恢复正常
        assertThat(barrier.getNumberWaiting()).isEqualTo(0);
        
        logger.info("CyclicBarrier reset and break test completed successfully");
    }
    
    /**
     * Semaphore 测试
     * 
     * Semaphore是一个计数信号量，用于控制对某组资源的访问权限。
     * 
     * 核心概念：
     * 1. 许可证：信号量维护一组许可证
     * 2. acquire()：获取许可证，如果没有可用许可证则阻塞
     * 3. release()：释放许可证
     * 4. 公平性：支持公平和非公平模式
     * 
     * 适用场景：
     * - 数据库连接池
     * - 限制并发访问数量
     * - 资源池管理
     */
    @Test
    @Timeout(8)
    public void testSemaphore() throws InterruptedException {
        int permits = 3; // 允许3个线程同时访问资源
        int totalThreads = 6;
        Semaphore semaphore = new Semaphore(permits);
        
        CountDownLatch allThreadsDone = new CountDownLatch(totalThreads);
        List<String> accessLog = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger concurrentAccess = new AtomicInteger(0);
        AtomicInteger maxConcurrentAccess = new AtomicInteger(0);
        
        // 创建访问资源的线程
        for (int i = 0; i < totalThreads; i++) {
            final int threadId = i;
            new Thread(() -> {
                try {
                    logger.info("Thread {} requesting access", threadId);
                    
                    // 获取许可证
                    semaphore.acquire();
                    
                    // 记录并发访问数量
                    int current = concurrentAccess.incrementAndGet();
                    maxConcurrentAccess.updateAndGet(max -> Math.max(max, current));
                    
                    accessLog.add("Thread-" + threadId + "-acquired");
                    logger.info("Thread {} acquired permit, concurrent access: {}", threadId, current);
                    
                    // 模拟资源使用
                    Thread.sleep(200);
                    
                    accessLog.add("Thread-" + threadId + "-released");
                    concurrentAccess.decrementAndGet();
                    logger.info("Thread {} releasing permit", threadId);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    // 释放许可证
                    semaphore.release();
                    allThreadsDone.countDown();
                }
            }).start();
        }
        
        // 等待所有线程完成
        boolean completed = allThreadsDone.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        
        // 验证结果
        assertThat(maxConcurrentAccess.get()).isLessThanOrEqualTo(permits);
        assertThat(accessLog).hasSize(totalThreads * 2); // 每个线程有acquire和release两个事件
        
        // 验证信号量状态
        assertThat(semaphore.availablePermits()).isEqualTo(permits);
        assertThat(semaphore.hasQueuedThreads()).isFalse();
        
        logger.info("Max concurrent access: {}", maxConcurrentAccess.get());
        logger.info("Access log: {}", accessLog);
        logger.info("Semaphore test completed successfully");
    }
    
    /**
     * 测试Semaphore的tryAcquire功能
     */
    @Test
    @Timeout(3)
    public void testSemaphoreTryAcquire() throws InterruptedException {
        Semaphore semaphore = new Semaphore(2);
        
        // 成功获取2个许可证
        boolean acquired1 = semaphore.tryAcquire();
        boolean acquired2 = semaphore.tryAcquire();
        assertThat(acquired1).isTrue();
        assertThat(acquired2).isTrue();
        assertThat(semaphore.availablePermits()).isEqualTo(0);
        
        // 尝试获取第3个许可证应该失败
        boolean acquired3 = semaphore.tryAcquire();
        assertThat(acquired3).isFalse();
        
        // 尝试带超时的获取
        boolean acquiredWithTimeout = semaphore.tryAcquire(100, TimeUnit.MILLISECONDS);
        assertThat(acquiredWithTimeout).isFalse();
        
        // 释放一个许可证
        semaphore.release();
        assertThat(semaphore.availablePermits()).isEqualTo(1);
        
        // 现在应该能成功获取
        boolean acquiredAfterRelease = semaphore.tryAcquire();
        assertThat(acquiredAfterRelease).isTrue();
        
        // 清理
        semaphore.release();
        semaphore.release();
        
        logger.info("Semaphore tryAcquire test completed successfully");
    }
    
    /**
     * Exchanger 测试
     * 
     * Exchanger提供了一个同步点，两个线程可以在此交换数据。
     * 
     * 核心概念：
     * 1. 交换点：两个线程的汇合点
     * 2. 数据交换：每个线程提供数据并接收对方的数据
     * 3. 阻塞：如果只有一个线程到达，会阻塞等待另一个线程
     * 
     * 适用场景：
     * - 生产者消费者之间的数据交换
     * - 两个线程间的数据传递
     * - 流水线处理中的数据传递
     */
    @Test
    @Timeout(5)
    public void testExchanger() throws InterruptedException {
        Exchanger<String> exchanger = new Exchanger<>();
        CountDownLatch bothThreadsDone = new CountDownLatch(2);
        
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        
        // 线程1：生产者
        Thread producer = new Thread(() -> {
            try {
                String data = "Data from Producer";
                logger.info("Producer offering: {}", data);
                
                String received = exchanger.exchange(data);
                logger.info("Producer received: {}", received);
                results.add("Producer-" + received);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                bothThreadsDone.countDown();
            }
        });
        
        // 线程2：消费者
        Thread consumer = new Thread(() -> {
            try {
                String data = "Data from Consumer";
                logger.info("Consumer offering: {}", data);
                
                String received = exchanger.exchange(data);
                logger.info("Consumer received: {}", received);
                results.add("Consumer-" + received);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                bothThreadsDone.countDown();
            }
        });
        
        producer.start();
        Thread.sleep(100); // 让生产者先开始等待
        consumer.start();
        
        // 等待两个线程完成
        boolean completed = bothThreadsDone.await(3, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        
        // 验证数据交换结果
        assertThat(results).containsExactlyInAnyOrder(
            "Producer-Data from Consumer",
            "Consumer-Data from Producer"
        );
        
        logger.info("Exchange results: {}", results);
        logger.info("Exchanger test completed successfully");
    }
    
    /**
     * 综合测试：使用多种同步工具协调复杂的并发场景
     */
    @Test
    @Timeout(10)
    public void testComplexSynchronizationScenario() throws InterruptedException {
        int workerCount = 4;
        int resourcePermits = 2;
        
        // 各种同步工具
        CountDownLatch initializationComplete = new CountDownLatch(workerCount);
        CyclicBarrier phaseBarrier = new CyclicBarrier(workerCount);
        Semaphore resourceSemaphore = new Semaphore(resourcePermits);
        CountDownLatch allWorkComplete = new CountDownLatch(workerCount);
        
        List<String> executionLog = Collections.synchronizedList(new ArrayList<>());
        
        // 创建工作线程
        for (int i = 0; i < workerCount; i++) {
            final int workerId = i;
            new Thread(() -> {
                try {
                    // 阶段1：初始化
                    Thread.sleep(workerId * 50); // 模拟不同的初始化时间
                    executionLog.add("Worker-" + workerId + "-initialized");
                    logger.info("Worker {} initialized", workerId);
                    initializationComplete.countDown();
                    
                    // 等待所有工作线程初始化完成
                    initializationComplete.await();
                    logger.info("Worker {} proceeding after all initialized", workerId);
                    
                    // 阶段2：第一轮工作（需要资源访问）
                    resourceSemaphore.acquire();
                    try {
                        executionLog.add("Worker-" + workerId + "-working-phase1");
                        logger.info("Worker {} doing phase 1 work with resource", workerId);
                        Thread.sleep(100);
                    } finally {
                        resourceSemaphore.release();
                    }
                    
                    // 等待所有线程完成第一阶段
                    phaseBarrier.await();
                    logger.info("Worker {} passed phase 1 barrier", workerId);
                    
                    // 阶段3：第二轮工作
                    executionLog.add("Worker-" + workerId + "-working-phase2");
                    logger.info("Worker {} doing phase 2 work", workerId);
                    Thread.sleep(50);
                    
                    // 等待所有线程完成第二阶段
                    phaseBarrier.await();
                    logger.info("Worker {} passed phase 2 barrier", workerId);
                    
                    executionLog.add("Worker-" + workerId + "-completed");
                    logger.info("Worker {} completed all work", workerId);
                    
                } catch (InterruptedException | BrokenBarrierException e) {
                    logger.error("Worker {} encountered error", workerId, e);
                    Thread.currentThread().interrupt();
                } finally {
                    allWorkComplete.countDown();
                }
            }).start();
        }
        
        // 等待所有工作完成
        boolean completed = allWorkComplete.await(8, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        
        // 验证执行日志
        assertThat(executionLog).hasSize(workerCount * 4); // 每个worker有4个日志条目
        
        // 验证所有初始化在第一阶段工作之前完成
        List<String> initEvents = executionLog.stream()
                .filter(event -> event.contains("initialized"))
                .toList();
        List<String> phase1Events = executionLog.stream()
                .filter(event -> event.contains("phase1"))
                .toList();
        
        assertThat(initEvents).hasSize(workerCount);
        assertThat(phase1Events).hasSize(workerCount);
        
        logger.info("Execution log: {}", executionLog);
        logger.info("Complex synchronization scenario test completed successfully");
    }
} 