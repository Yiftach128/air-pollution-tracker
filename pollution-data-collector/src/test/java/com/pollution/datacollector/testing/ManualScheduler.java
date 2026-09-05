package com.pollution.datacollector.testing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A {@link ScheduledExecutorService} that runs nothing on its own: it keeps
 * what was scheduled, with its delay and period, and the test runs the
 * tasks by hand ({@link #runAll()}), on its own thread, whenever it wants a
 * tick. Nothing in a test waits for a timer.
 */
public final class ManualScheduler extends AbstractExecutorService implements ScheduledExecutorService {

    /** One scheduled task; {@code period} is {@code null} for a one-shot. */
    public record Scheduled(Runnable task, Duration initialDelay, Duration period) {
    }

    private final List<Scheduled> tasks = new ArrayList<>();
    private boolean shutdown;

    /** Everything scheduled so far, in scheduling order. */
    public synchronized List<Scheduled> tasks() {
        return List.copyOf(tasks);
    }

    /** Runs every scheduled task once, in scheduling order, as one tick of each would. */
    public void runAll() {
        for (Scheduled scheduled : tasks()) {
            scheduled.task().run();
        }
    }

    @Override
    public synchronized ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        tasks.add(new Scheduled(Objects.requireNonNull(command), toDuration(initialDelay, unit), toDuration(period, unit)));
        return new NeverDone();
    }

    @Override
    public synchronized ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return scheduleAtFixedRate(command, initialDelay, delay, unit);
    }

    @Override
    public synchronized ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        tasks.add(new Scheduled(Objects.requireNonNull(command), toDuration(delay, unit), null));
        return new NeverDone();
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        throw new UnsupportedOperationException("the collector schedules Runnables only");
    }

    /** Runs the task right away, on the caller's thread. */
    @Override
    public void execute(Runnable command) {
        command.run();
    }

    @Override
    public synchronized void shutdown() {
        shutdown = true;
    }

    @Override
    public synchronized List<Runnable> shutdownNow() {
        shutdown = true;
        return List.of();
    }

    @Override
    public synchronized boolean isShutdown() {
        return shutdown;
    }

    @Override
    public synchronized boolean isTerminated() {
        return shutdown;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }

    private static Duration toDuration(long amount, TimeUnit unit) {
        return Duration.ofNanos(unit.toNanos(amount));
    }

    /** The future of a task the scheduler never runs by itself: pending for good. */
    private static final class NeverDone implements ScheduledFuture<Object> {

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Object get() {
            throw new UnsupportedOperationException("a manually run task has no result");
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("a manually run task has no result");
        }
    }
}
