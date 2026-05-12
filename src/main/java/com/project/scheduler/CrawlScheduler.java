package com.project.scheduler;

import com.project.crawler.CodeforcesCrawler;
import com.project.dao.UserDAO;
import com.project.model.User;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class CrawlScheduler {

    public static final int DEFAULT_MAX_NEW_SUBMISSIONS_PER_USER = 20;

    private final UserDAO userDAO;
    private final CodeforcesCrawler crawler;
    private final AtomicBoolean started;
    private ScheduledExecutorService scheduler;

    public CrawlScheduler() {
        this(false);
    }

    public CrawlScheduler(boolean headless) {
        this.userDAO = new UserDAO();
        this.crawler = new CodeforcesCrawler(headless);
        this.started = new AtomicBoolean(false);
    }

    public void start() {
        start(0, 1, TimeUnit.DAYS);
    }

    public void start(long initialDelay, long period, TimeUnit unit) {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(new CrawlThreadFactory());
        scheduler.scheduleAtFixedRate(
                () -> crawlAllUsersSafely(DEFAULT_MAX_NEW_SUBMISSIONS_PER_USER),
                initialDelay,
                period,
                unit
        );
    }

    public int crawlAllUsersNow() {
        return crawlAllUsersNow(DEFAULT_MAX_NEW_SUBMISSIONS_PER_USER);
    }

    public int crawlAllUsersNow(int maxNewSubmissionsPerUser) {
        return crawlAllUsersSafely(maxNewSubmissionsPerUser);
    }

    public void stop() {
        started.set(false);
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public boolean isRunning() {
        return started.get();
    }

    private int crawlAllUsersSafely(int maxNewSubmissionsPerUser) {
        int totalInserted = 0;
        try {
            List<User> users = userDAO.findAll();
            for (User user : users) {
                try {
                    int insertedCount = crawler.crawl(user, maxNewSubmissionsPerUser);
                    totalInserted += insertedCount;
                    System.out.printf("Crawled handle=%s, new submissions=%d%n", user.getHandle(), insertedCount);
                } catch (Exception exception) {
                    System.err.printf("Failed to crawl handle=%s: %s%n", user.getHandle(), exception.getMessage());
                }
            }
        } catch (SQLException exception) {
            System.err.printf("Failed to load users for scheduled crawl: %s%n", exception.getMessage());
        }
        return totalInserted;
    }

    private static final class CrawlThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "crawl-scheduler");
            thread.setDaemon(true);
            return thread;
        }
    }
}
