package com.xiejj.terminal.utils;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * @author xiejiajun
 */
@Slf4j
public class ThreadUtils {

    public static ExecutorService newFixedThreadPool(int qty, String processName) {
        return Executors.newFixedThreadPool(qty, newThreadFactory(processName));
    }

    public static ThreadFactory newThreadFactory(String processName) {
        return newGenericThreadFactory("Curator-" + processName);
    }

    public static ThreadFactory newGenericThreadFactory(String processName) {
        Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                log.error("Unexpected exception in thread: " + t, e);
                Throwables.propagate(e);
            }
        };
        return (new ThreadFactoryBuilder()).setNameFormat(processName + "-%d").setDaemon(true).setUncaughtExceptionHandler(uncaughtExceptionHandler).build();
    }
}
