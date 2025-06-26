package com.javastudy.lock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ThreadLocal测试类
 * 
 * ThreadLocal提供了线程本地存储，每个线程都有自己独立的变量副本。
 * 这是一种避免同步的重要技术，常用于保存线程上下文信息。
 * 
 * 核心概念：
 * 1. ThreadLocal：为每个线程提供独立的变量副本
 * 2. InheritableThreadLocal：子线程可以继承父线程的ThreadLocal值
 * 3. ThreadLocalMap：ThreadLocal的底层实现，每个线程维护一个map
 * 4. 内存泄漏：不当使用ThreadLocal可能导致内存泄漏
 * 5. 弱引用：ThreadLocalMap使用弱引用作为key来避免内存泄漏
 * 
 * 使用场景：
 * - 数据库连接管理
 * - 用户会话信息
 * - 事务上下文
 * - 日期格式化器
 * - 随机数生成器
 */
public class ThreadLocalTest {

    private static final Logger logger = LoggerFactory.getLogger(ThreadLocalTest.class);

    /**
     * 用户上下文信息
     */
    public static class UserContext {
        private final String userId;
        private final String userName;
        private final long timestamp;

        public UserContext(String userId, String userName) {
            this.userId = userId;
            this.userName = userName;
            this.timestamp = System.currentTimeMillis();
        }

        public String getUserId() { return userId; }
        public String getUserName() { return userName; }
        public long getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("UserContext{userId='%s', userName='%s', timestamp=%d}", 
                    userId, userName, timestamp);
        }
    }

    /**
     * 数据库连接模拟类
     */
    public static class DatabaseConnection {
        private final String connectionId;
        private final String threadName;
        private volatile boolean closed = false;

        public DatabaseConnection() {
            this.connectionId = UUID.randomUUID().toString().substring(0, 8);
            this.threadName = Thread.currentThread().getName();
        }

        public String getConnectionId() { return connectionId; }
        public String getThreadName() { return threadName; }
        public boolean isClosed() { return closed; }

        public void close() {
            this.closed = true;
            logger.debug("Database connection {} closed in thread {}", connectionId, threadName);
        }

        @Override
        public String toString() {
            return String.format("Connection{id='%s', thread='%s', closed=%s}", 
                    connectionId, threadName, closed);
        }
    }

    // ThreadLocal变量定义
    private static final ThreadLocal<String> threadLocalString = new ThreadLocal<>();
    private static final ThreadLocal<Integer> threadLocalInteger = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<UserContext> userContextThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<DatabaseConnection> connectionThreadLocal = 
            ThreadLocal.withInitial(DatabaseConnection::new);

    // InheritableThreadLocal变量
    private static final InheritableThreadLocal<String> inheritableThreadLocal = new InheritableThreadLocal<>();

    // 自定义ThreadLocal，带有资源清理
    private static final ThreadLocal<DatabaseConnection> managedConnectionThreadLocal = 
            new ThreadLocal<DatabaseConnection>() {
                @Override
                protected DatabaseConnection initialValue() {
                    DatabaseConnection conn = new DatabaseConnection();
                    logger.debug("Created new database connection: {}", conn);
                    return conn;
                }

                @Override
                public void remove() {
                    DatabaseConnection conn = get();
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                    super.remove();
                    logger.debug("Removed and cleaned up database connection");
                }
            };

    @BeforeEach
    public void setUp() {
        // 清理ThreadLocal，确保测试间的隔离
        threadLocalString.remove();
        threadLocalInteger.remove();
        userContextThreadLocal.remove();
        connectionThreadLocal.remove();
        inheritableThreadLocal.remove();
        managedConnectionThreadLocal.remove();
    }

    @AfterEach
    public void tearDown() {
        // 测试后清理，防止内存泄漏
        threadLocalString.remove();
        threadLocalInteger.remove();
        userContextThreadLocal.remove();
        connectionThreadLocal.remove();
        inheritableThreadLocal.remove();
        managedConnectionThreadLocal.remove();
    }

    /**
     * 测试ThreadLocal基本用法
     * 验证每个线程都有自己独立的变量副本
     */
    @Test
    @Timeout(10)
    public void testBasicThreadLocal() throws InterruptedException {
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        Map<String, String> results = new ConcurrentHashMap<>();

        // 创建多个线程，每个线程设置不同的ThreadLocal值
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    
                    String threadName = Thread.currentThread().getName();
                    String value = "Value-" + threadIndex;
                    
                    // 设置ThreadLocal值
                    threadLocalString.set(value);
                    
                    // 短暂休眠，模拟业务处理
                    Thread.sleep(100);
                    
                    // 获取ThreadLocal值
                    String retrievedValue = threadLocalString.get();
                    results.put(threadName, retrievedValue);
                    
                    logger.debug("Thread {} set and retrieved: {}", threadName, retrievedValue);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }, "TestThread-" + i).start();
        }

        startLatch.countDown(); // 启动所有线程
        boolean completed = doneLatch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // 验证每个线程都获取到了自己设置的值
        assertThat(results).hasSize(threadCount);
        for (int i = 0; i < threadCount; i++) {
            String expectedValue = "Value-" + i;
            String threadName = "TestThread-" + i;
            assertThat(results.get(threadName)).isEqualTo(expectedValue);
        }

        logger.info("Basic ThreadLocal test completed. Results: {}", results);
    }

    /**
     * 测试ThreadLocal初始值设置
     * 验证withInitial()方法和initialValue()方法的使用
     */
    @Test
    @Timeout(5)
    public void testThreadLocalWithInitialValue() throws InterruptedException {
        int threadCount = 3;
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Integer> initialValues = Collections.synchronizedList(new ArrayList<>());
        List<Integer> incrementedValues = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    // 获取初始值（应该是0）
                    Integer initialValue = threadLocalInteger.get();
                    initialValues.add(initialValue);
                    
                    // 递增值
                    threadLocalInteger.set(initialValue + 1);
                    Integer incrementedValue = threadLocalInteger.get();
                    incrementedValues.add(incrementedValue);
                    
                    logger.debug("Thread {} - Initial: {}, Incremented: {}", 
                            Thread.currentThread().getName(), initialValue, incrementedValue);
                    
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        boolean completed = latch.await(3, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // 验证所有线程都获得了初始值0
        assertThat(initialValues).hasSize(threadCount);
        assertThat(initialValues).allMatch(value -> value.equals(0));

        // 验证所有线程都成功递增到1
        assertThat(incrementedValues).hasSize(threadCount);
        assertThat(incrementedValues).allMatch(value -> value.equals(1));

        logger.info("ThreadLocal with initial value test completed");
    }

    /**
     * 测试InheritableThreadLocal
     * 验证子线程可以继承父线程的ThreadLocal值
     */
    @Test
    @Timeout(10)
    public void testInheritableThreadLocal() throws InterruptedException {
        String parentValue = "ParentValue";
        CountDownLatch childLatch = new CountDownLatch(3);
        Map<String, String> childResults = new ConcurrentHashMap<>();

        // 在主线程中设置InheritableThreadLocal值
        inheritableThreadLocal.set(parentValue);
        logger.debug("Main thread set value: {}", parentValue);

        // 创建子线程
        for (int i = 0; i < 3; i++) {
            final int childIndex = i;
            Thread childThread = new Thread(() -> {
                // 子线程应该能够继承父线程的值
                String inheritedValue = inheritableThreadLocal.get();
                String childThreadName = Thread.currentThread().getName();
                childResults.put(childThreadName, inheritedValue);
                
                // 子线程修改自己的值
                String childValue = "ChildValue-" + childIndex;
                inheritableThreadLocal.set(childValue);
                
                logger.debug("Child thread {} inherited: {}, then set: {}", 
                        childThreadName, inheritedValue, childValue);
                
                childLatch.countDown();
            }, "ChildThread-" + childIndex);
            
            childThread.start();
        }
        
        // 等待所有子线程完成
        boolean childrenCompleted = childLatch.await(5, TimeUnit.SECONDS);
        assertThat(childrenCompleted).isTrue();

        // 验证所有子线程都继承了父线程的值
        assertThat(childResults).hasSize(3);
        assertThat(childResults.values()).allMatch(value -> value.equals(parentValue));

        logger.info("InheritableThreadLocal test completed. Child results: {}", childResults);
    }

    /**
     * 测试用户上下文ThreadLocal
     * 演示ThreadLocal在实际业务中的应用
     */
    @Test
    @Timeout(10)
    public void testUserContextThreadLocal() throws InterruptedException {
        int userCount = 4;
        CountDownLatch latch = new CountDownLatch(userCount);
        Map<String, UserContext> userContexts = new ConcurrentHashMap<>();

        // 模拟多个用户的并发请求
        for (int i = 0; i < userCount; i++) {
            final int userId = i + 1;
            new Thread(() -> {
                try {
                    // 设置用户上下文
                    UserContext context = new UserContext("user" + userId, "User" + userId);
                    userContextThreadLocal.set(context);
                    
                    logger.debug("Set user context: {}", context);
                    
                    // 模拟业务处理
                    try {
                        simulateBusinessOperation();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    
                    // 获取用户上下文进行验证
                    UserContext retrievedContext = userContextThreadLocal.get();
                    userContexts.put(Thread.currentThread().getName(), retrievedContext);
                    
                } finally {
                    // 清理ThreadLocal
                    userContextThreadLocal.remove();
                    latch.countDown();
                }
            }, "UserThread-" + userId).start();
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // 验证每个线程都有正确的用户上下文
        assertThat(userContexts).hasSize(userCount);
        for (int i = 1; i <= userCount; i++) {
            String threadName = "UserThread-" + i;
            UserContext context = userContexts.get(threadName);
            assertThat(context).isNotNull();
            assertThat(context.getUserId()).isEqualTo("user" + i);
            assertThat(context.getUserName()).isEqualTo("User" + i);
        }

        logger.info("User context ThreadLocal test completed");
    }

    /**
     * 模拟业务操作，在其中使用ThreadLocal获取用户上下文
     */
    private void simulateBusinessOperation() throws InterruptedException {
        UserContext context = userContextThreadLocal.get();
        if (context != null) {
            logger.debug("Processing business logic for user: {}", context.getUserName());
            Thread.sleep(50); // 模拟业务处理时间
        }
    }

    /**
     * 测试数据库连接ThreadLocal
     * 演示ThreadLocal在资源管理中的应用
     */
    @Test
    @Timeout(10)
    public void testDatabaseConnectionThreadLocal() throws InterruptedException {
        int connectionCount = 3;
        CountDownLatch latch = new CountDownLatch(connectionCount);
        Set<String> connectionIds = Collections.synchronizedSet(new HashSet<>());
        Set<String> threadNames = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < connectionCount; i++) {
            new Thread(() -> {
                try {
                    // 获取数据库连接（如果不存在会自动创建）
                    DatabaseConnection conn1 = connectionThreadLocal.get();
                    connectionIds.add(conn1.getConnectionId());
                    threadNames.add(conn1.getThreadName());
                    
                    logger.debug("Thread {} got connection: {}", 
                            Thread.currentThread().getName(), conn1);
                    
                    // 再次获取，应该是同一个连接
                    DatabaseConnection conn2 = connectionThreadLocal.get();
                    assertThat(conn2).isSameAs(conn1);
                    
                    // 模拟数据库操作
                    Thread.sleep(100);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    // 清理连接
                    DatabaseConnection conn = connectionThreadLocal.get();
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                    connectionThreadLocal.remove();
                    latch.countDown();
                }
            }, "DBThread-" + i).start();
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // 验证每个线程都有独立的连接
        assertThat(connectionIds).hasSize(connectionCount);
        assertThat(threadNames).hasSize(connectionCount);

        logger.info("Database connection ThreadLocal test completed. Connections: {}", 
                connectionIds.size());
    }

    /**
     * 测试ThreadLocal内存泄漏防护
     * 演示正确的清理方式
     */
    @Test
    @Timeout(10)
    public void testThreadLocalMemoryLeakPrevention() throws InterruptedException {
        int iterationCount = 5;
        AtomicLong createdConnections = new AtomicLong(0);
        AtomicLong cleanedConnections = new AtomicLong(0);

        // 重写ThreadLocal以监控创建和清理
        ThreadLocal<DatabaseConnection> monitoredThreadLocal = new ThreadLocal<DatabaseConnection>() {
            @Override
            protected DatabaseConnection initialValue() {
                createdConnections.incrementAndGet();
                return new DatabaseConnection();
            }

            @Override
            public void remove() {
                DatabaseConnection conn = get();
                if (conn != null) {
                    conn.close();
                    cleanedConnections.incrementAndGet();
                }
                super.remove();
            }
        };

        CountDownLatch latch = new CountDownLatch(iterationCount);

        // 模拟多次请求处理
        for (int i = 0; i < iterationCount; i++) {
            new Thread(() -> {
                try {
                    // 使用ThreadLocal
                    DatabaseConnection conn = monitoredThreadLocal.get();
                    assertThat(conn).isNotNull();
                    
                    // 模拟业务处理
                    Thread.sleep(50);
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    // 重要：必须清理ThreadLocal防止内存泄漏
                    monitoredThreadLocal.remove();
                    latch.countDown();
                }
            }).start();
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // 验证创建和清理的连接数匹配
        assertThat(createdConnections.get()).isEqualTo(iterationCount);
        assertThat(cleanedConnections.get()).isEqualTo(iterationCount);

        logger.info("Memory leak prevention test completed. Created: {}, Cleaned: {}", 
                createdConnections.get(), cleanedConnections.get());
    }

    /**
     * 测试ThreadLocal性能
     * 对比ThreadLocal与synchronized的性能差异
     */
    @Test
    @Timeout(20)
    public void testThreadLocalPerformance() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 50000;

        // 测试ThreadLocal性能
        long threadLocalTime = testThreadLocalPerformance(threadCount, operationsPerThread);

        // 测试synchronized性能
        long synchronizedTime = testSynchronizedPerformance(threadCount, operationsPerThread);

        logger.info("Performance comparison for {} threads, {} operations each:", 
                threadCount, operationsPerThread);
        logger.info("ThreadLocal: {} ms", threadLocalTime);
        logger.info("Synchronized: {} ms", synchronizedTime);
        
        if (threadLocalTime < synchronizedTime) {
            logger.info("ThreadLocal is {:.2f}x faster", 
                    (double) synchronizedTime / threadLocalTime);
        }

        // ThreadLocal通常比synchronized快得多
        assertThat(threadLocalTime).isLessThan(synchronizedTime);
    }

    private long testThreadLocalPerformance(int threadCount, int operationsPerThread) 
            throws InterruptedException {
        ThreadLocal<AtomicLong> counterThreadLocal = ThreadLocal.withInitial(() -> new AtomicLong(0));
        
        CountDownLatch latch = new CountDownLatch(threadCount);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    AtomicLong counter = counterThreadLocal.get();
                    for (int j = 0; j < operationsPerThread; j++) {
                        counter.incrementAndGet();
                    }
                } finally {
                    counterThreadLocal.remove();
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        long endTime = System.currentTimeMillis();
        
        return endTime - startTime;
    }

    private long testSynchronizedPerformance(int threadCount, int operationsPerThread) 
            throws InterruptedException {
        AtomicLong sharedCounter = new AtomicLong(0);
        Object lock = new Object();
        
        CountDownLatch latch = new CountDownLatch(threadCount);
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        synchronized (lock) {
                            sharedCounter.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        long endTime = System.currentTimeMillis();
        
        return endTime - startTime;
    }

    /**
     * 测试ThreadLocal的线程安全日期格式化
     * 演示ThreadLocal解决SimpleDateFormat线程安全问题
     */
    @Test
    @Timeout(10)
    public void testThreadSafeDateFormat() throws InterruptedException {
        // SimpleDateFormat不是线程安全的，使用ThreadLocal保证线程安全
        ThreadLocal<SimpleDateFormat> dateFormatThreadLocal = 
                ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

        int threadCount = 5;
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<String> formattedDates = Collections.synchronizedSet(new HashSet<>());
        List<Date> testDates = Arrays.asList(
                new Date(System.currentTimeMillis()),
                new Date(System.currentTimeMillis() + 1000),
                new Date(System.currentTimeMillis() + 2000),
                new Date(System.currentTimeMillis() + 3000),
                new Date(System.currentTimeMillis() + 4000)
        );

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            new Thread(() -> {
                try {
                    SimpleDateFormat formatter = dateFormatThreadLocal.get();
                    Date testDate = testDates.get(threadIndex);
                    
                    // 多次格式化同一个日期，应该得到相同结果
                    for (int j = 0; j < 10; j++) {
                        String formatted = formatter.format(testDate);
                        formattedDates.add(formatted);
                        Thread.sleep(10);
                    }
                    
                    logger.debug("Thread {} formatted date: {}", 
                            Thread.currentThread().getName(), 
                            formatter.format(testDate));
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    dateFormatThreadLocal.remove();
                    latch.countDown();
                }
            }, "DateFormatThread-" + i).start();
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // 应该有5个不同的格式化日期
        assertThat(formattedDates).hasSize(threadCount);

        logger.info("Thread-safe date format test completed. Unique dates: {}", 
                formattedDates.size());
    }

    /**
     * 测试ThreadLocal清理最佳实践
     * 演示在Web应用中的正确使用方式
     */
    @Test
    @Timeout(10)
    public void testThreadLocalCleanupBestPractices() throws InterruptedException {
        // 模拟Web请求处理
        int requestCount = 10;
        CountDownLatch latch = new CountDownLatch(requestCount);
        AtomicInteger successfulCleanups = new AtomicInteger(0);

        for (int i = 0; i < requestCount; i++) {
            final int requestId = i;
            new Thread(() -> {
                try {
                    // 模拟请求开始，设置用户上下文
                    UserContext context = new UserContext("user" + requestId, "User" + requestId);
                    userContextThreadLocal.set(context);
                    
                    // 获取数据库连接
                    DatabaseConnection conn = managedConnectionThreadLocal.get();
                    assertThat(conn).isNotNull();
                    
                    // 模拟业务处理
                    processRequest(requestId);
                    
                    successfulCleanups.incrementAndGet();
                    
                } catch (Exception e) {
                    logger.error("Error processing request " + requestId, e);
                } finally {
                    // 重要：请求结束时必须清理所有ThreadLocal
                    cleanupThreadLocals();
                    latch.countDown();
                }
            }, "RequestThread-" + requestId).start();
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        assertThat(successfulCleanups.get()).isEqualTo(requestCount);

        logger.info("ThreadLocal cleanup best practices test completed. " +
                "Successful cleanups: {}", successfulCleanups.get());
    }

    private void processRequest(int requestId) throws InterruptedException {
        UserContext context = userContextThreadLocal.get();
        DatabaseConnection conn = managedConnectionThreadLocal.get();
        
        logger.debug("Processing request {} for user {} with connection {}", 
                requestId, context.getUserName(), conn.getConnectionId());
        
        Thread.sleep(50); // 模拟业务处理
    }

    /**
     * 清理所有ThreadLocal变量
     * 这是防止内存泄漏的关键步骤
     */
    private void cleanupThreadLocals() {
        userContextThreadLocal.remove();
        managedConnectionThreadLocal.remove();
        // 其他ThreadLocal变量也应该在这里清理
        logger.debug("Cleaned up ThreadLocal variables for thread: {}", 
                Thread.currentThread().getName());
    }

    /**
     * 测试ThreadLocal在不同场景下的行为
     */
    @Test
    @Timeout(5)
    public void testThreadLocalBehaviorScenarios() {
        // 测试1：初始状态下ThreadLocal返回null
        assertThat(threadLocalString.get()).isNull();

        // 测试2：设置值后可以正确获取
        threadLocalString.set("test-value");
        assertThat(threadLocalString.get()).isEqualTo("test-value");

        // 测试3：remove()后返回null
        threadLocalString.remove();
        assertThat(threadLocalString.get()).isNull();

        // 测试4：withInitial()提供默认值
        assertThat(threadLocalInteger.get()).isEqualTo(0);

        // 测试5：设置null值
        threadLocalString.set(null);
        assertThat(threadLocalString.get()).isNull();

        logger.info("ThreadLocal behavior scenarios test completed");
    }
} 