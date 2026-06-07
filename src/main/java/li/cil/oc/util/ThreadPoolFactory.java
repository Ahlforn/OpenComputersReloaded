package li.cil.oc.util;

import li.cil.oc.OpenComputersMod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public final class ThreadPoolFactory {
    public static final int PRIORITY;

    static {
        int custom;
        try {
            custom = li.cil.oc.Settings.get().threadPriority;
        } catch (Exception ignored) {
            custom = -1;
        }
        if (custom < 1) {
            PRIORITY = Thread.MIN_PRIORITY + (Thread.NORM_PRIORITY - Thread.MIN_PRIORITY) / 2;
        } else {
            PRIORITY = Math.max(Thread.MIN_PRIORITY, Math.min(Thread.MAX_PRIORITY, custom));
        }
    }

    public static final List<SafeThreadPool> safePools = new ArrayList<>();

    public static ScheduledExecutorService create(String name, int threads) {
        return Executors.newScheduledThreadPool(threads, new ThreadFactory() {
            private final String baseName = "OpenComputers-" + name + "-";
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            private final ThreadGroup group;

            {
                group = Thread.currentThread().getThreadGroup();
            }

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(group, r, baseName + threadNumber.getAndIncrement());
                if (!thread.isDaemon()) thread.setDaemon(true);
                if (thread.getPriority() != PRIORITY) thread.setPriority(PRIORITY);
                return thread;
            }
        });
    }

    public static SafeThreadPool createSafePool(String name, int threads) {
        SafeThreadPool pool = new SafeThreadPool(name, threads);
        safePools.add(pool);
        return pool;
    }

    private ThreadPoolFactory() {}

    public static final class SafeThreadPool {
        public final String name;
        public final int threads;
        private ScheduledExecutorService threadPool;

        public SafeThreadPool(String name, int threads) {
            this.name = name;
            this.threads = threads;
        }

        public Optional<Future<?>> withPool(Function<ScheduledExecutorService, Future<?>> f, boolean requiresPool) {
            if (threadPool == null) {
                OpenComputersMod.LOGGER.warn("Error handling file saving: Did the server never start?");
                if (requiresPool) {
                    OpenComputersMod.LOGGER.warn("Creating new thread pool.");
                    newThreadPool();
                } else {
                    return Optional.empty();
                }
            } else if (threadPool.isShutdown() || threadPool.isTerminated()) {
                OpenComputersMod.LOGGER.warn("Error handling file saving: Thread pool shut down!");
                if (requiresPool) {
                    OpenComputersMod.LOGGER.warn("Creating new thread pool.");
                    newThreadPool();
                } else {
                    return Optional.empty();
                }
            }
            return Optional.ofNullable(f.apply(threadPool));
        }

        public Optional<Future<?>> withPool(Function<ScheduledExecutorService, Future<?>> f) {
            return withPool(f, true);
        }

        public void newThreadPool() {
            if (threadPool != null && !threadPool.isTerminated()) {
                threadPool.shutdownNow();
            }
            threadPool = ThreadPoolFactory.create(name, threads);
        }

        public void waitForCompletion() {
            withPool(pool -> {
                try {
                    pool.shutdown();
                    boolean terminated = pool.awaitTermination(15, TimeUnit.SECONDS);
                    if (!terminated) {
                        OpenComputersMod.LOGGER.warn("Warning: Completing all tasks has already taken 15 seconds!");
                        terminated = pool.awaitTermination(105, TimeUnit.SECONDS);
                        if (!terminated) {
                            OpenComputersMod.LOGGER.error("Warning: Completing all tasks has already taken two minutes! Aborting");
                            pool.shutdownNow();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    OpenComputersMod.LOGGER.error("Thread pool wait interrupted", e);
                }
                return null;
            }, false);
        }
    }
}
