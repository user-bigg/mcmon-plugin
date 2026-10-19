package ru.mcmon.plugin.core;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class Futures {

    private static final ScheduledThreadPoolExecutor TIMER = timer();

    private Futures() {}

    private static ScheduledThreadPoolExecutor timer() {
        ScheduledThreadPoolExecutor timer = new ScheduledThreadPoolExecutor(1, task -> {
            Thread thread = new Thread(task, "mcmonitor-timer");
            thread.setDaemon(true);
            return thread;
        });
        timer.setRemoveOnCancelPolicy(true);
        timer.setKeepAliveTime(1, TimeUnit.MINUTES);
        timer.allowCoreThreadTimeOut(true);
        return timer;
    }

    static ScheduledFuture<?> after(Duration delay, Runnable task) {
        return TIMER.schedule(task, delay.toMillis(), TimeUnit.MILLISECONDS);
    }
}
