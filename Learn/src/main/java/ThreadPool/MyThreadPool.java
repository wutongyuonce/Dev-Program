package ThreadPool;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class MyThreadPool {
    private int corePoolSize;
    private int maxPoolSize;
    private long keepAliveTime;
    private TimeUnit unit;
    private BlockingQueue<Runnable> workQueue;

    // 当前活跃线程数（简化计数）
    private int currentThreadCount = 0;

    public MyThreadPool(int corePoolSize, int maxPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.unit = unit;
        this.workQueue = workQueue;
    }

    public void execute(Runnable command) {
        if (command == null) throw new NullPointerException();

        // 1. 如果当前线程数 < core，创建核心线程执行任务
        if (currentThreadCount < corePoolSize) {
            addWorker(command, true); // true 表示核心线程
            return;
        }

        // 2. 尝试入队
        if (workQueue.offer(command)) {
            return;
        }

        // 3. 入队失败（队列满），尝试创建非核心线程
        if (currentThreadCount < maxPoolSize) {
            addWorker(command, false); // false 表示非核心线程
            return;
        }

        // 4. 拒绝策略
        throw new RuntimeException("ThreadPool is full and queue is full: task rejected");
    }

    // 创建并启动一个工作线程，firstTask 是它要执行的第一个任务
    private void addWorker(Runnable firstTask, boolean isCore) {
        Worker worker = new Worker(firstTask, isCore);
        Thread thread = new Thread(worker);
        thread.start();
        currentThreadCount++; // 注意：此处非线程安全，仅用于单线程提交场景
    }

    // 工作线程封装
    private class Worker implements Runnable {
        private final Runnable firstTask;
        private final boolean isCore;

        Worker(Runnable firstTask, boolean isCore) {
            this.firstTask = firstTask;
            this.isCore = isCore;
        }

        @Override
        public void run() {
            Runnable task = firstTask; // 先执行传入的任务

            try {
                while (task != null || (task = getTask()) != null) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    task = null; // 执行完置空，下一轮从队列取
                }
            } finally {
                // 线程退出，减少计数（非线程安全）
                currentThreadCount--;
                if (!isCore) {
                    System.out.println(Thread.currentThread().getName() + " 非核心线程结束了！");
                }
            }
        }

        // 从队列获取任务：核心线程用 take()（永久阻塞），非核心用 poll(timeout)
        private Runnable getTask() {
            try {
                if (isCore) {
                    return workQueue.take(); // 阻塞直到有任务
                } else {
                    return workQueue.poll(keepAliveTime, unit); // 超时回收
                }
            } catch (InterruptedException e) {
                return null; // 被中断时退出
            }
        }
    }

    // 测试
    public static void main(String[] args) throws InterruptedException {
        MyThreadPool pool = new MyThreadPool(
                2,                      // corePoolSize
                4,                      // maxPoolSize
                1,                      // keepAliveTime
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(2) // 有界队列
        );

        for (int i = 0; i < 6; i++) {
            final int taskId = i;
            pool.execute(() -> {
                try {
                    Thread.sleep(1000); // 模拟任务耗时
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println(Thread.currentThread().getName() + " executed task " + taskId);
            });
        }

        System.out.println("主线程没有被阻塞");

        // 等待所有任务完成（便于观察输出）
        Thread.sleep(5000);
    }
    // 结果：
    // 主线程没有被阻塞
    // Thread-0 executed task 0
    // Thread-1 executed task 1
    // Thread-2 executed task 2
    // Thread-3 executed task 3
    // Thread-0 executed task 4
    // Thread-1 executed task 5
    // Thread-2 非核心线程结束了！
    // Thread-3 非核心线程结束了！
}
