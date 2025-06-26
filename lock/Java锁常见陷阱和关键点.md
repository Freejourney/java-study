# Java锁常见陷阱和关键点

## 目录
1. [死锁问题](#死锁问题)
2. [锁泄漏](#锁泄漏)
3. [性能陷阱](#性能陷阱)
4. [内存可见性问题](#内存可见性问题)
5. [锁的误用](#锁的误用)
6. [最佳实践](#最佳实践)

## 死锁问题

### 经典死锁场景

```java
// 危险代码：可能导致死锁
public class DeadlockExample {
    private final Object lock1 = new Object();
    private final Object lock2 = new Object();
    
    public void method1() {
        synchronized (lock1) {
            System.out.println("Thread " + Thread.currentThread().getName() + " acquired lock1");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
            
            synchronized (lock2) {
                System.out.println("Thread " + Thread.currentThread().getName() + " acquired lock2");
            }
        }
    }
    
    public void method2() {
        synchronized (lock2) {
            System.out.println("Thread " + Thread.currentThread().getName() + " acquired lock2");
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {}
            
            synchronized (lock1) {
                System.out.println("Thread " + Thread.currentThread().getName() + " acquired lock1");
            }
        }
    }
}
```

### 死锁的四个必要条件

1. **互斥条件**：资源不能被多个线程同时使用
2. **持有并等待**：线程持有资源的同时等待其他资源
3. **不可抢占**：资源不能被强制从线程中抢占
4. **循环等待**：线程间形成循环等待链

### 死锁预防策略

#### 1. 锁顺序法

```java
// 安全代码：使用固定的锁顺序
public class DeadlockPrevention {
    private final Object lock1 = new Object();
    private final Object lock2 = new Object();
    
    private void acquireLocksInOrder(Object firstLock, Object secondLock) {
        synchronized (firstLock) {
            synchronized (secondLock) {
                // 执行业务逻辑
            }
        }
    }
    
    public void method1() {
        Object first = System.identityHashCode(lock1) < System.identityHashCode(lock2) ? lock1 : lock2;
        Object second = first == lock1 ? lock2 : lock1;
        acquireLocksInOrder(first, second);
    }
    
    public void method2() {
        Object first = System.identityHashCode(lock1) < System.identityHashCode(lock2) ? lock1 : lock2;
        Object second = first == lock1 ? lock2 : lock1;
        acquireLocksInOrder(first, second);
    }
}
```

#### 2. 超时机制

```java
// 使用tryLock避免无限等待
public boolean transferMoney(Account from, Account to, int amount) {
    boolean fromLocked = false;
    boolean toLocked = false;
    
    try {
        fromLocked = from.getLock().tryLock(1, TimeUnit.SECONDS);
        if (!fromLocked) {
            return false;
        }
        
        toLocked = to.getLock().tryLock(1, TimeUnit.SECONDS);
        if (!toLocked) {
            return false;
        }
        
        // 执行转账操作
        from.debit(amount);
        to.credit(amount);
        return true;
        
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
    } finally {
        if (toLocked) {
            to.getLock().unlock();
        }
        if (fromLocked) {
            from.getLock().unlock();
        }
    }
}
```

#### 3. 死锁检测工具

```java
// 使用ThreadMXBean检测死锁
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class DeadlockDetector {
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    
    public void detectDeadlock() {
        long[] deadlockedThreads = threadMXBean.findDeadlockedThreads();
        if (deadlockedThreads != null) {
            System.err.println("发现死锁！涉及线程：");
            for (long threadId : deadlockedThreads) {
                ThreadInfo threadInfo = threadMXBean.getThreadInfo(threadId);
                System.err.println("线程：" + threadInfo.getThreadName());
            }
        }
    }
}
```

## 锁泄漏

### 忘记释放锁

```java
// 错误示例：可能导致锁泄漏
ReentrantLock lock = new ReentrantLock();

public void dangerousMethod() {
    lock.lock();
    if (someCondition) {
        return; // 忘记释放锁！
    }
    // 其他逻辑
    lock.unlock();
}

// 正确示例：使用try-finally确保锁释放
public void safeMethod() {
    lock.lock();
    try {
        if (someCondition) {
            return; // 锁会在finally中释放
        }
        // 其他逻辑
    } finally {
        lock.unlock(); // 确保锁总是被释放
    }
}
```

### 异常导致的锁泄漏

```java
// 错误示例：异常时锁未释放
public void riskyMethod() throws Exception {
    lock.lock();
    riskyOperation(); // 可能抛出异常
    lock.unlock(); // 异常时不会执行
}

// 正确示例：异常安全的锁使用
public void safeMethod() throws Exception {
    lock.lock();
    try {
        riskyOperation(); // 即使抛出异常，锁也会被释放
    } finally {
        lock.unlock();
    }
}
```

### 重入锁的计数问题

```java
// 错误示例：重入锁计数不匹配
public synchronized void method1() {
    method2(); // 重入
    // 如果在method2中意外调用了额外的unlock()，会导致问题
}

public synchronized void method2() {
    // 一些逻辑
}

// 正确示例：确保lock/unlock配对
private final ReentrantLock lock = new ReentrantLock();

public void method1() {
    lock.lock();
    try {
        method2();
    } finally {
        lock.unlock();
    }
}

public void method2() {
    lock.lock();
    try {
        // 逻辑
    } finally {
        lock.unlock();
    }
}
```

## 性能陷阱

### 锁粒度过粗

```java
// 性能问题：锁粒度过粗
public class CoarseGrainedLocking {
    private final Object lock = new Object();
    private int counter1 = 0;
    private int counter2 = 0;
    
    // 不相关的操作使用同一个锁
    public void incrementCounter1() {
        synchronized (lock) {
            counter1++; // 短暂操作
            complexCalculation(); // 长时间操作，阻塞其他线程
        }
    }
    
    public void incrementCounter2() {
        synchronized (lock) {
            counter2++; // 被不必要地阻塞
        }
    }
}

// 改进：细粒度锁
public class FineGrainedLocking {
    private final Object lock1 = new Object();
    private final Object lock2 = new Object();
    private int counter1 = 0;
    private int counter2 = 0;
    
    public void incrementCounter1() {
        int result = complexCalculation(); // 在锁外进行复杂计算
        synchronized (lock1) {
            counter1 += result; // 锁内只做必要操作
        }
    }
    
    public void incrementCounter2() {
        synchronized (lock2) {
            counter2++; // 不会被counter1的操作阻塞
        }
    }
}
```

### 锁竞争激烈

```java
// 性能问题：单一锁导致高竞争
public class HighContentionCounter {
    private volatile int count = 0;
    
    public synchronized int increment() {
        return ++count; // 所有线程竞争同一个锁
    }
}

// 改进：使用原子操作
public class AtomicCounter {
    private final AtomicInteger count = new AtomicInteger(0);
    
    public int increment() {
        return count.incrementAndGet(); // 无锁操作，性能更好
    }
}

// 改进：分段锁
public class SegmentedCounter {
    private final int segmentCount = Runtime.getRuntime().availableProcessors();
    private final AtomicInteger[] segments = new AtomicInteger[segmentCount];
    
    public SegmentedCounter() {
        for (int i = 0; i < segmentCount; i++) {
            segments[i] = new AtomicInteger(0);
        }
    }
    
    public void increment() {
        int segment = Thread.currentThread().hashCode() % segmentCount;
        segments[Math.abs(segment)].incrementAndGet(); // 分散竞争
    }
    
    public int getSum() {
        return Arrays.stream(segments).mapToInt(AtomicInteger::get).sum();
    }
}
```

### 不必要的同步

```java
// 性能问题：过度同步
public class OverSynchronized {
    private final List<String> list = new ArrayList<>();
    
    // 读操作也加锁，导致读操作无法并发
    public synchronized String get(int index) {
        return list.get(index);
    }
    
    public synchronized void add(String item) {
        list.add(item);
    }
}

// 改进：使用读写锁
public class ReadWriteLockExample {
    private final List<String> list = new ArrayList<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public String get(int index) {
        lock.readLock().lock();
        try {
            return list.get(index); // 多个读操作可以并发
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void add(String item) {
        lock.writeLock().lock();
        try {
            list.add(item);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
```

## 内存可见性问题

### volatile与锁的误用

```java
// 错误示例：volatile不能保证复合操作的原子性
public class VolatileProblem {
    private volatile int count = 0;
    
    public void increment() {
        count++; // 非原子操作：读取、加1、写回
    }
}

// 正确示例：使用锁保证原子性
public class SynchronizedCounter {
    private int count = 0;
    
    public synchronized void increment() {
        count++; // synchronized保证原子性和可见性
    }
}
```

### 部分同步的危险

```java
// 危险代码：部分字段同步
public class PartialSynchronization {
    private int x = 0;
    private int y = 0;
    
    public synchronized void setX(int x) {
        this.x = x;
    }
    
    public void setY(int y) { // 没有同步！
        this.y = y;
    }
    
    public synchronized int getSum() {
        return x + y; // y的可见性无法保证
    }
}

// 正确示例：全面同步
public class CompleteSynchronization {
    private int x = 0;
    private int y = 0;
    
    public synchronized void setX(int x) {
        this.x = x;
    }
    
    public synchronized void setY(int y) {
        this.y = y;
    }
    
    public synchronized int getSum() {
        return x + y;
    }
}
```

## 锁的误用

### synchronized与String

```java
// 危险代码：使用String作为锁对象
public class StringLockDanger {
    private final String lock = "LOCK"; // 字符串常量池中的对象
    
    public void method1() {
        synchronized (lock) { // 可能与其他地方的"LOCK"是同一个对象
            // 临界区
        }
    }
}

// 安全代码：使用专门的锁对象
public class SafeLock {
    private final Object lock = new Object(); // 专用锁对象
    
    public void method1() {
        synchronized (lock) {
            // 临界区
        }
    }
}
```

### this作为锁对象的问题

```java
// 潜在问题：this作为锁对象
public class ThisLockProblem {
    private int count = 0;
    
    public synchronized void increment() { // 等同于synchronized(this)
        count++;
    }
}

// 外部可能意外获取锁
ThisLockProblem obj = new ThisLockProblem();
synchronized (obj) { // 外部代码获取了同一个锁
    // 可能导致意外的同步行为
}

// 改进：使用私有锁对象
public class PrivateLock {
    private final Object lock = new Object();
    private int count = 0;
    
    public void increment() {
        synchronized (lock) { // 外部无法访问lock对象
            count++;
        }
    }
}
```

### 锁对象的可变性

```java
// 危险代码：可变的锁对象
public class MutableLock {
    private Object lock = new Object();
    
    public void changelock() {
        lock = new Object(); // 改变锁对象！
    }
    
    public void synchronizedMethod() {
        synchronized (lock) { // 锁对象可能被改变
            // 临界区
        }
    }
}

// 安全代码：不可变的锁对象
public class ImmutableLock {
    private final Object lock = new Object(); // final确保不可变
    
    public void synchronizedMethod() {
        synchronized (lock) {
            // 临界区
        }
    }
}
```

## 最佳实践

### 1. 锁的使用原则

```java
// 锁使用的基本模式
Lock lock = new ReentrantLock();

lock.lock();
try {
    // 临界区：尽可能短小
    // 避免在此处调用其他可能获取锁的方法
    // 避免长时间操作或阻塞操作
} finally {
    lock.unlock(); // 必须在finally中释放
}
```

### 2. 锁的选择指南

```java
// 选择合适的锁类型
public class LockSelection {
    
    // 简单互斥：使用synchronized
    public synchronized void simpleMethod() {
        // 简单的临界区
    }
    
    // 需要高级功能：使用ReentrantLock
    private final ReentrantLock advancedLock = new ReentrantLock();
    
    public void advancedMethod() {
        if (advancedLock.tryLock()) {
            try {
                // 临界区
            } finally {
                advancedLock.unlock();
            }
        }
    }
    
    // 读多写少：使用ReadWriteLock
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    
    public String read() {
        rwLock.readLock().lock();
        try {
            return data;
        } finally {
            rwLock.readLock().unlock();
        }
    }
}
```

### 3. 监控和诊断

```java
// 锁性能监控
public class LockMonitoring {
    private final ReentrantLock lock = new ReentrantLock();
    
    public void monitoredMethod() {
        long startTime = System.nanoTime();
        lock.lock();
        long lockAcquiredTime = System.nanoTime();
        
        try {
            // 临界区逻辑
        } finally {
            lock.unlock();
            long endTime = System.nanoTime();
            
            long waitTime = lockAcquiredTime - startTime;
            long holdTime = endTime - lockAcquiredTime;
            
            // 记录锁等待时间和持有时间
            if (waitTime > THRESHOLD) {
                logger.warn("Long lock wait time: {} ns", waitTime);
            }
        }
    }
}
```

### 4. 异常安全

```java
// 异常安全的锁使用
public class ExceptionSafeLocking {
    private final ReentrantLock lock = new ReentrantLock();
    
    public void exceptionSafeMethod() {
        lock.lock();
        try {
            riskyOperation(); // 可能抛出异常
            anotherRiskyOperation(); // 可能抛出另一个异常
        } catch (SpecificException e) {
            // 处理特定异常
            handleSpecificException(e);
        } catch (Exception e) {
            // 处理其他异常
            handleGenericException(e);
            throw e; // 重新抛出
        } finally {
            lock.unlock(); // 无论如何都会释放锁
        }
    }
}
```

### 5. 测试锁相关代码

```java
// 并发测试工具类
public class ConcurrencyTestUtil {
    
    public static void testConcurrency(Runnable task, int threadCount, int iterations) 
            throws InterruptedException {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await(); // 等待统一开始
                    for (int j = 0; j < iterations; j++) {
                        task.run();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown(); // 开始测试
        endLatch.await(); // 等待所有线程完成
    }
}
```

## LMAX Disruptor的注意事项

### Disruptor特有的陷阱

#### 1. Ring Buffer大小设置不当

```java
// 错误：不是2的幂
int bufferSize = 1000; // 会导致性能问题

// 正确：必须是2的幂
int bufferSize = 1024; // 2^10
int bufferSize = 2048; // 2^11
```

#### 2. 等待策略选择不当

```java
// 错误：在CPU敏感环境使用BusySpinWaitStrategy
// 会导致CPU占用率过高
new BusySpinWaitStrategy(); // 不适合共享CPU环境

// 正确：根据环境选择合适策略
new BlockingWaitStrategy(); // CPU友好
new YieldingWaitStrategy(); // 平衡选择
```

#### 3. 事件对象状态污染

```java
// 错误：事件对象状态在处理间残留
public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
    // 没有清理上一次的状态，可能导致数据污染
    processOrder(event);
}

// 正确：确保事件对象状态清理
public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
    try {
        processOrder(event);
    } finally {
        event.clear(); // 清理状态
    }
}
```

#### 4. 生产者类型选择错误

```java
// 错误：多生产者场景使用SINGLE
Disruptor<Event> disruptor = new Disruptor<>(
    factory, bufferSize, executor,
    ProducerType.SINGLE, // 但实际有多个生产者
    waitStrategy
);

// 正确：根据实际情况选择
ProducerType.MULTI // 多生产者
ProducerType.SINGLE // 单生产者
```

### Disruptor最佳实践

#### 1. 合理的异常处理

```java
disruptor.setDefaultExceptionHandler(new ExceptionHandler<Event>() {
    @Override
    public void handleEventException(Throwable ex, long sequence, Event event) {
        logger.error("事件处理异常: sequence={}, event={}", sequence, event, ex);
        // 不要在这里抛出异常，会导致消费者停止
    }

    @Override
    public void handleOnStartException(Throwable ex) {
        logger.error("Disruptor启动异常", ex);
    }

    @Override
    public void handleOnShutdownException(Throwable ex) {
        logger.error("Disruptor关闭异常", ex);
    }
});
```

#### 2. 优雅关闭

```java
// 优雅关闭Disruptor
try {
    disruptor.shutdown(10, TimeUnit.SECONDS);
} catch (TimeoutException e) {
    logger.warn("Disruptor关闭超时，强制关闭");
    disruptor.halt();
}
```

#### 3. 监控和诊断

```java
// 监控Ring Buffer状态
RingBuffer<Event> ringBuffer = disruptor.getRingBuffer();
long remainingCapacity = ringBuffer.remainingCapacity();
if (remainingCapacity < bufferSize * 0.1) {
    logger.warn("Ring Buffer即将满载: remaining={}", remainingCapacity);
}
```

### 何时使用Disruptor

#### 适合场景
- 极高吞吐量要求（百万级TPS）
- 低延迟要求（微秒级）
- 高频交易系统
- 实时事件处理
- 大量读操作的场景

#### 不适合场景
- 简单的生产者-消费者模式
- 低并发场景
- 内存受限环境
- 对GC敏感的应用（虽然Disruptor减少GC，但预分配会占用内存）

## ThreadLocal相关陷阱

### 1. 内存泄漏陷阱

#### 问题描述
ThreadLocal最常见的陷阱是内存泄漏，特别是在Web应用和线程池环境中。

#### 陷阱示例
```java
// 危险：忘记清理ThreadLocal
public class BadService {
    private static final ThreadLocal<LargeObject> cache = new ThreadLocal<>();
    
    public void handleRequest() {
        cache.set(new LargeObject()); // 设置大对象
        // 处理请求...
        // 忘记调用 cache.remove()！
    }
}
```

#### 原因分析
- 线程池中的线程会被复用
- ThreadLocal变量会一直保持对大对象的引用
- 导致大对象无法被GC回收

#### 解决方案
```java
// 正确：始终清理ThreadLocal
public class GoodService {
    private static final ThreadLocal<LargeObject> cache = new ThreadLocal<>();
    
    public void handleRequest() {
        try {
            cache.set(new LargeObject());
            // 处理请求...
        } finally {
            cache.remove(); // 确保清理
        }
    }
}
```

### 2. 父子线程传递陷阱

#### 问题描述
普通ThreadLocal无法在父子线程间传递值，InheritableThreadLocal虽然可以但有性能问题。

#### 陷阱示例
```java
public class InheritanceTrap {
    private static final ThreadLocal<String> context = new ThreadLocal<>();
    
    public void parentMethod() {
        context.set("parent-value");
        
        new Thread(() -> {
            String value = context.get(); // null！子线程获取不到
            System.out.println(value);
        }).start();
    }
}
```

#### 解决方案
```java
// 方案1：使用InheritableThreadLocal
private static final InheritableThreadLocal<String> context = 
    new InheritableThreadLocal<>();

// 方案2：显式传递
public void parentMethod() {
    String contextValue = context.get();
    
    new Thread(() -> {
        // 显式传递上下文
        processWithContext(contextValue);
    }).start();
}
```

### 3. 线程池复用陷阱

#### 问题描述
在线程池环境中，线程会被复用，可能导致上一个任务的ThreadLocal值影响下一个任务。

#### 陷阱示例
```java
public class ThreadPoolTrap {
    private static final ThreadLocal<User> currentUser = new ThreadLocal<>();
    
    @Async
    public void asyncTask1() {
        currentUser.set(new User("Alice"));
        // 处理任务，但忘记清理
    }
    
    @Async 
    public void asyncTask2() {
        User user = currentUser.get(); // 可能得到Alice！
        // 这个任务本应该没有用户上下文
    }
}
```

#### 解决方案
```java
// 在任务开始时重置ThreadLocal
@Async
public void asyncTask() {
    currentUser.remove(); // 先清理
    try {
        // 处理任务
    } finally {
        currentUser.remove(); // 任务结束后清理
    }
}
```

### 4. Web应用中的陷阱

#### 问题描述
在Web应用中，请求处理线程可能被复用，导致不同请求间的数据污染。

#### 解决方案：过滤器统一管理
```java
public class ThreadLocalCleanupFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        try {
            chain.doFilter(request, response);
        } finally {
            // 清理所有可能的ThreadLocal变量
            UserContext.clear();
            SecurityContext.clear();
            RequestContext.clear();
        }
    }
}
```

### 5. 性能陷阱

#### 问题描述
过度使用ThreadLocal或在ThreadLocal中存储大对象会影响性能。

#### 最佳实践
```java
// 避免存储大对象
private static final ThreadLocal<ExpensiveObject> expensive = new ThreadLocal<>(); // 不好

// 改为存储轻量级标识
private static final ThreadLocal<String> objectId = new ThreadLocal<>();

// 通过ID获取真正的对象
public ExpensiveObject getObject() {
    String id = objectId.get();
    return id != null ? cache.get(id) : null;
}
```

### ThreadLocal最佳实践总结

1. **始终清理**：在finally块中调用remove()
2. **尽早清理**：不要等到线程结束才清理
3. **统一管理**：在Web应用中使用过滤器统一清理
4. **避免大对象**：不要在ThreadLocal中存储大对象
5. **谨慎继承**：考虑是否真的需要InheritableThreadLocal
6. **监控内存**：定期检查是否有ThreadLocal导致的内存泄漏

## 总结

Java并发编程的使用需要特别注意以下关键点：

1. **死锁预防**：使用锁顺序、超时机制、死锁检测
2. **锁泄漏避免**：始终在finally中释放锁
3. **性能优化**：选择合适的锁粒度和类型
4. **内存可见性**：正确理解volatile和synchronized的作用
5. **锁对象选择**：避免使用String、this作为锁对象
6. **异常安全**：确保异常情况下锁能正确释放
7. **ThreadLocal管理**：及时清理避免内存泄漏，谨慎在线程池中使用
8. **测试验证**：充分测试并发场景
9. **正确使用Disruptor**：合理设置参数、处理异常、选择合适场景

遵循这些最佳实践，可以帮助你编写出安全、高效的并发代码。对于极高性能要求的场景，Disruptor提供了无锁的解决方案，但也需要正确理解和使用。 