package com.javastudy.lock;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LMAX Disruptor测试类
 * 
 * Disruptor是LMAX公司开发的高性能无锁并发框架，专门用于线程间通信。
 * 它通过环形缓冲区(Ring Buffer)和预分配对象的方式，实现了极高的吞吐量和极低的延迟。
 * 
 * 核心概念：
 * 1. Ring Buffer：环形缓冲区，预分配固定数量的事件对象
 * 2. Event：事件对象，承载业务数据
 * 3. EventFactory：事件工厂，负责创建事件对象
 * 4. EventHandler：事件处理器，定义如何处理事件
 * 5. EventTranslator：事件转换器，将业务数据转换为事件
 * 6. Producer：生产者，发布事件到Ring Buffer
 * 7. Consumer：消费者，处理Ring Buffer中的事件
 * 8. WaitStrategy：等待策略，定义消费者等待新事件的方式
 */
public class DisruptorTest {

    private static final Logger logger = LoggerFactory.getLogger(DisruptorTest.class);

    /**
     * 基本事件类
     * 承载业务数据，在Ring Buffer中循环使用
     */
    public static class LongEvent {
        private long value;
        
        public void set(long value) {
            this.value = value;
        }
        
        public long getValue() {
            return value;
        }
    }

    /**
     * 事件工厂
     * 负责创建和初始化事件对象
     */
    public static class LongEventFactory implements EventFactory<LongEvent> {
        @Override
        public LongEvent newInstance() {
            return new LongEvent();
        }
    }

    /**
     * 事件处理器
     * 定义如何处理事件
     */
    public static class LongEventHandler implements EventHandler<LongEvent> {
        private final AtomicLong processedCount = new AtomicLong(0);
        private final String name;

        public LongEventHandler(String name) {
            this.name = name;
        }

        @Override
        public void onEvent(LongEvent event, long sequence, boolean endOfBatch) throws Exception {
            logger.debug("{} processing event: value={}, sequence={}, endOfBatch={}", 
                    name, event.getValue(), sequence, endOfBatch);
            processedCount.incrementAndGet();
        }

        public long getProcessedCount() {
            return processedCount.get();
        }
    }

    /**
     * 事件转换器
     * 将业务数据转换为事件
     */
    public static class LongEventTranslator implements EventTranslator<LongEvent> {
        private final long value;

        public LongEventTranslator(long value) {
            this.value = value;
        }

        @Override
        public void translateTo(LongEvent event, long sequence) {
            event.set(value);
        }
    }

    /**
     * 测试基本的生产者-消费者模式
     * 演示Disruptor的基本用法
     */
    @Test
    @Timeout(10)
    public void testBasicProducerConsumer() throws InterruptedException {
        // Ring Buffer大小，必须是2的幂
        int bufferSize = 1024;
        
        // 创建Disruptor
        Disruptor<LongEvent> disruptor = new Disruptor<>(
                new LongEventFactory(),
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE, // 单生产者
                new BlockingWaitStrategy() // 阻塞等待策略
        );

        // 设置事件处理器
        LongEventHandler handler = new LongEventHandler("BasicHandler");
        disruptor.handleEventsWith(handler);

        // 启动Disruptor
        disruptor.start();

        // 获取Ring Buffer
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        // 生产事件
        int eventCount = 1000;
        for (int i = 0; i < eventCount; i++) {
            final int value = i;
            // 发布事件
            ringBuffer.publishEvent(new LongEventTranslator(value));
        }

        // 等待所有事件被处理
        Thread.sleep(1000);

        // 验证结果
        assertThat(handler.getProcessedCount()).isEqualTo(eventCount);
        logger.info("Basic producer-consumer test completed. Processed {} events", 
                handler.getProcessedCount());

        // 关闭Disruptor
        disruptor.shutdown();
    }

    /**
     * 测试多消费者模式
     * 演示事件可以被多个消费者并行处理
     */
    @Test
    @Timeout(10)
    public void testMultipleConsumers() throws InterruptedException {
        int bufferSize = 1024;
        
        Disruptor<LongEvent> disruptor = new Disruptor<>(
                new LongEventFactory(),
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,
                new YieldingWaitStrategy() // 让出CPU的等待策略
        );

        // 创建多个事件处理器
        LongEventHandler handler1 = new LongEventHandler("Consumer-1");
        LongEventHandler handler2 = new LongEventHandler("Consumer-2");
        LongEventHandler handler3 = new LongEventHandler("Consumer-3");

        // 设置多个消费者并行处理同一事件
        disruptor.handleEventsWith(handler1, handler2, handler3);

        disruptor.start();
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        // 发布事件
        int eventCount = 500;
        for (int i = 0; i < eventCount; i++) {
            ringBuffer.publishEvent(new LongEventTranslator(i));
        }

        // 等待处理完成
        Thread.sleep(1000);

        // 验证每个消费者都处理了所有事件
        assertThat(handler1.getProcessedCount()).isEqualTo(eventCount);
        assertThat(handler2.getProcessedCount()).isEqualTo(eventCount);
        assertThat(handler3.getProcessedCount()).isEqualTo(eventCount);

        logger.info("Multiple consumers test completed. Each consumer processed {} events", 
                eventCount);

        disruptor.shutdown();
    }

    /**
     * 测试消费者链模式
     * 演示事件按顺序被不同的消费者处理
     */
    @Test
    @Timeout(10)
    public void testConsumerChain() throws InterruptedException {
        int bufferSize = 1024;
        
        Disruptor<LongEvent> disruptor = new Disruptor<>(
                new LongEventFactory(),
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,
                new BusySpinWaitStrategy() // 忙等待策略，最低延迟
        );

        LongEventHandler handler1 = new LongEventHandler("Stage-1");
        LongEventHandler handler2 = new LongEventHandler("Stage-2");
        LongEventHandler handler3 = new LongEventHandler("Stage-3");

        // 设置消费者链：handler1 -> handler2 -> handler3
        disruptor.handleEventsWith(handler1)
                .then(handler2)
                .then(handler3);

        disruptor.start();
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        int eventCount = 300;
        for (int i = 0; i < eventCount; i++) {
            ringBuffer.publishEvent(new LongEventTranslator(i));
        }

        Thread.sleep(1000);

        // 验证每个阶段都处理了所有事件
        assertThat(handler1.getProcessedCount()).isEqualTo(eventCount);
        assertThat(handler2.getProcessedCount()).isEqualTo(eventCount);
        assertThat(handler3.getProcessedCount()).isEqualTo(eventCount);

        logger.info("Consumer chain test completed. Each stage processed {} events", 
                eventCount);

        disruptor.shutdown();
    }

    /**
     * 测试多生产者模式
     * 演示多个线程同时向Ring Buffer发布事件
     */
    @Test
    @Timeout(10)
    public void testMultipleProducers() throws InterruptedException {
        int bufferSize = 1024;
        
        Disruptor<LongEvent> disruptor = new Disruptor<>(
                new LongEventFactory(),
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI, // 多生产者模式
                new BlockingWaitStrategy()
        );

        LongEventHandler handler = new LongEventHandler("MultiProducerHandler");
        disruptor.handleEventsWith(handler);

        disruptor.start();
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        // 创建多个生产者线程
        int producerCount = 4;
        int eventsPerProducer = 250;
        CountDownLatch producerLatch = new CountDownLatch(producerCount);
        
        for (int i = 0; i < producerCount; i++) {
            final int producerId = i;
            new Thread(() -> {
                try {
                    for (int j = 0; j < eventsPerProducer; j++) {
                        long value = producerId * 1000L + j;
                        ringBuffer.publishEvent(new LongEventTranslator(value));
                    }
                    logger.info("Producer {} finished publishing {} events", 
                            producerId, eventsPerProducer);
                } finally {
                    producerLatch.countDown();
                }
            }).start();
        }

        // 等待所有生产者完成
        boolean completed = producerLatch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // 等待所有事件被处理
        Thread.sleep(1000);

        int expectedTotal = producerCount * eventsPerProducer;
        assertThat(handler.getProcessedCount()).isEqualTo(expectedTotal);

        logger.info("Multiple producers test completed. Total events processed: {}", 
                handler.getProcessedCount());

        disruptor.shutdown();
    }

    /**
     * 测试不同的等待策略
     * 演示Disruptor支持的各种等待策略及其特点
     */
    @Test
    @Timeout(10)
    public void testWaitStrategies() throws InterruptedException {
        int bufferSize = 1024;
        int eventCount = 1000;

        // 测试BlockingWaitStrategy - 阻塞等待，CPU友好但延迟较高
        testWaitStrategy("BlockingWaitStrategy", new BlockingWaitStrategy(), 
                bufferSize, eventCount);

        // 测试YieldingWaitStrategy - 让出CPU，平衡CPU使用和延迟
        testWaitStrategy("YieldingWaitStrategy", new YieldingWaitStrategy(), 
                bufferSize, eventCount);

        // 测试BusySpinWaitStrategy - 忙等待，最低延迟但高CPU占用
        testWaitStrategy("BusySpinWaitStrategy", new BusySpinWaitStrategy(), 
                bufferSize, eventCount);

        // 测试SleepingWaitStrategy - 睡眠等待，最低CPU占用但延迟较高
        testWaitStrategy("SleepingWaitStrategy", new SleepingWaitStrategy(), 
                bufferSize, eventCount);
    }

    private void testWaitStrategy(String strategyName, WaitStrategy waitStrategy, 
                                 int bufferSize, int eventCount) throws InterruptedException {
        Disruptor<LongEvent> disruptor = new Disruptor<>(
                new LongEventFactory(),
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,
                waitStrategy
        );

        LongEventHandler handler = new LongEventHandler(strategyName + "-Handler");
        disruptor.handleEventsWith(handler);

        disruptor.start();
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        long startTime = System.currentTimeMillis();

        // 发布事件
        for (int i = 0; i < eventCount; i++) {
            ringBuffer.publishEvent(new LongEventTranslator(i));
        }

        // 等待处理完成
        Thread.sleep(500);

        long endTime = System.currentTimeMillis();

        assertThat(handler.getProcessedCount()).isEqualTo(eventCount);
        logger.info("{} test completed in {} ms, processed {} events", 
                strategyName, endTime - startTime, handler.getProcessedCount());

        disruptor.shutdown();
    }

    /**
     * 测试Disruptor与传统BlockingQueue的性能对比
     */
    @Test
    @Timeout(20)
    public void testPerformanceComparison() throws InterruptedException {
        int eventCount = 100000;

        // 测试Disruptor性能
        long disruptorTime = testDisruptorPerformance(eventCount);

        // 测试BlockingQueue性能
        long blockingQueueTime = testBlockingQueuePerformance(eventCount);

        logger.info("Performance comparison for {} events:", eventCount);
        logger.info("Disruptor: {} ms", disruptorTime);
        logger.info("BlockingQueue: {} ms", blockingQueueTime);
        logger.info("Disruptor is {:.2f}x faster", 
                (double) blockingQueueTime / disruptorTime);

        // Disruptor通常比BlockingQueue快得多
        assertThat(disruptorTime).isLessThan(blockingQueueTime * 2); // 至少2倍性能提升
    }

    private long testDisruptorPerformance(int eventCount) throws InterruptedException {
        int bufferSize = 1024;
        
        Disruptor<LongEvent> disruptor = new Disruptor<>(
                new LongEventFactory(),
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,
                new YieldingWaitStrategy()
        );

        AtomicLong processedCount = new AtomicLong(0);
        
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            processedCount.incrementAndGet();
        });

        disruptor.start();
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        long startTime = System.currentTimeMillis();

        // 生产事件
        for (int i = 0; i < eventCount; i++) {
            ringBuffer.publishEvent(new LongEventTranslator(i));
        }

        // 等待所有事件被处理
        while (processedCount.get() < eventCount) {
            Thread.sleep(1);
        }

        long endTime = System.currentTimeMillis();
        disruptor.shutdown();

        return endTime - startTime;
    }

    private long testBlockingQueuePerformance(int eventCount) throws InterruptedException {
        BlockingQueue<Long> queue = new ArrayBlockingQueue<>(1024);
        AtomicLong processedCount = new AtomicLong(0);
        CountDownLatch consumerReady = new CountDownLatch(1);
        
        // 启动消费者线程
        Thread consumer = new Thread(() -> {
            consumerReady.countDown();
            try {
                while (processedCount.get() < eventCount) {
                    Long value = queue.take();
                    processedCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        consumer.start();
        consumerReady.await();

        long startTime = System.currentTimeMillis();

        // 生产事件
        for (int i = 0; i < eventCount; i++) {
            queue.put((long) i);
        }

        // 等待所有事件被处理
        while (processedCount.get() < eventCount) {
            Thread.sleep(1);
        }

        long endTime = System.currentTimeMillis();
        consumer.interrupt();

        return endTime - startTime;
    }

    /**
     * 测试事件批处理
     * 演示Disruptor的批处理能力
     */
    @Test
    @Timeout(10)
    public void testBatchProcessing() throws InterruptedException {
        int bufferSize = 1024;
        
        Disruptor<LongEvent> disruptor = new Disruptor<>(
                new LongEventFactory(),
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,
                new YieldingWaitStrategy()
        );

        AtomicLong batchCount = new AtomicLong(0);
        AtomicLong eventCount = new AtomicLong(0);

        // 批处理事件处理器
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            eventCount.incrementAndGet();
            if (endOfBatch) {
                batchCount.incrementAndGet();
                logger.debug("Batch completed at sequence {}, total events: {}", 
                        sequence, eventCount.get());
            }
        });

        disruptor.start();
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        // 快速发布一批事件
        for (int i = 0; i < 100; i++) {
            ringBuffer.publishEvent(new LongEventTranslator(i));
        }

        Thread.sleep(100);

        // 再发布一批事件
        for (int i = 100; i < 200; i++) {
            ringBuffer.publishEvent(new LongEventTranslator(i));
        }

        Thread.sleep(500);

        assertThat(eventCount.get()).isEqualTo(200);
        assertThat(batchCount.get()).isGreaterThan(0);

        logger.info("Batch processing test completed. Events: {}, Batches: {}", 
                eventCount.get(), batchCount.get());

        disruptor.shutdown();
    }

    /**
     * 测试异常处理
     * 演示Disruptor的异常处理机制
     */
    @Test
    @Timeout(10)
    public void testExceptionHandling() throws InterruptedException {
        int bufferSize = 1024;
        
        Disruptor<LongEvent> disruptor = new Disruptor<>(
                new LongEventFactory(),
                bufferSize,
                DaemonThreadFactory.INSTANCE,
                ProducerType.SINGLE,
                new BlockingWaitStrategy()
        );

        AtomicLong processedCount = new AtomicLong(0);
        AtomicLong exceptionCount = new AtomicLong(0);

        // 设置会抛异常的事件处理器
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            processedCount.incrementAndGet();
            // 模拟每10个事件抛一次异常
            if (event.getValue() % 10 == 0) {
                throw new RuntimeException("Simulated exception for value: " + event.getValue());
            }
        });

        // 设置异常处理器
        disruptor.setDefaultExceptionHandler(new ExceptionHandler<LongEvent>() {
            @Override
            public void handleEventException(Throwable ex, long sequence, LongEvent event) {
                exceptionCount.incrementAndGet();
                logger.debug("Exception handled for sequence {}, event value {}: {}", 
                        sequence, event.getValue(), ex.getMessage());
            }

            @Override
            public void handleOnStartException(Throwable ex) {
                logger.error("Exception on start", ex);
            }

            @Override
            public void handleOnShutdownException(Throwable ex) {
                logger.error("Exception on shutdown", ex);
            }
        });

        disruptor.start();
        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        // 发布100个事件，其中10个会抛异常
        for (int i = 0; i < 100; i++) {
            ringBuffer.publishEvent(new LongEventTranslator(i));
        }

        Thread.sleep(1000);

        assertThat(processedCount.get()).isEqualTo(100);
        assertThat(exceptionCount.get()).isEqualTo(10); // 0, 10, 20, ..., 90

        logger.info("Exception handling test completed. Processed: {}, Exceptions: {}", 
                processedCount.get(), exceptionCount.get());

        disruptor.shutdown();
    }
} 