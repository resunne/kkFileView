package cn.keking.utils;

import cn.keking.config.ConfigConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.StringUtils;

import java.util.concurrent.*;

/**
 * 全局公共线程池
 */
@Slf4j
public class GlobalThreadPool {

    private static final String THREAD_NAME = "GLOBAL-POOL-THREAD-";

    private static ExecutorService executor;

    static {
        init();
    }

    private GlobalThreadPool() {
    }

    /**
     * 初始化全局线程池
     */
    synchronized public static void init() {
        if (null != executor) {
            executor.shutdownNow();
        }

        executor = new ThreadPoolExecutor(
                ConfigConstants.getGlobalThreadPoolCoreSize()
                , ConfigConstants.getGlobalThreadPoolMaxSize()
                , ConfigConstants.getGlobalThreadPoolAliveTime()
                , TimeUnit.MILLISECONDS
                , new LinkedBlockingQueue<>(ConfigConstants.getGlobalThreadPoolBlockQueueSize())
                , new CustomizableThreadFactory(THREAD_NAME)
                , new ThreadPoolExecutor.CallerRunsPolicy());
    }

    /**
     * 关闭全局线程池
     *
     * @param isNow 是否立即关闭而不等待正在执行的线程
     */
    synchronized public static void shutdown(boolean isNow) {
        if (null != executor) {
            if (isNow) {
                executor.shutdownNow();
            } else {
                executor.shutdown();
            }
        }
    }

    /**
     * 获得 {@link ExecutorService}
     *
     * @return {@link ExecutorService}
     */
    public static ExecutorService getExecutor() {
        return executor;
    }

    /**
     * 直接在全局线程池中执行线程
     *
     * @param runnable 可运行对象
     */
    public static void execute(Runnable runnable) {
        try {
            executor.execute(runnable);
        } catch (Exception e) {
            String errorMsg = String.format("线程执行失败：%s", StringUtils.hasText(e.getMessage()) ? e.getMessage() : "发生错误");
            log.error(errorMsg, e);
            throw new RuntimeException(errorMsg);
        }
    }

    /**
     * 执行有返回值的异步方法<br>
     * Future代表一个异步执行的操作，通过get()方法可以获得操作的结果，如果异步操作还没有完成，则，get()会使当前线程阻塞
     *
     * @param <T>  执行的Task
     * @param task {@link Callable}
     * @return Future
     */
    public static <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }

    /**
     * 执行有返回值的异步方法<br>
     * Future代表一个异步执行的操作，通过get()方法可以获得操作的结果，如果异步操作还没有完成，则，get()会使当前线程阻塞
     *
     * @param runnable 可运行对象
     * @return {@link Future}
     * @since 3.0.5
     */
    public static Future<?> submit(Runnable runnable) {
        return executor.submit(runnable);
    }
}
