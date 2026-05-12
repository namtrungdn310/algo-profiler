package com.project.scheduler;

import com.project.crawler.CodeforcesBrowserSession;
import com.project.crawler.CodeforcesCrawler;
import com.project.dao.UserDAO;
import com.project.model.User;
import org.openqa.selenium.WebDriver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class CrawlScheduler {

    public static final int DEFAULT_MAX_NEW_SUBMISSIONS_PER_USER = 5;
    private static final Path DEBUG_LOG_PATH = Path.of("crawler-debug.log");

    private final UserDAO userDAO;
    private final CodeforcesCrawler crawler;
    private final boolean headless;
    private final AtomicBoolean started;
    private final Consumer<ScheduledCrawlReport> afterCrawlCallback;
    private ScheduledExecutorService scheduler;

    public CrawlScheduler() {
        this(false);
    }

    public CrawlScheduler(boolean headless) {
        this(headless, (Consumer<ScheduledCrawlReport>) null);
    }

    public CrawlScheduler(boolean headless, Runnable afterCrawlCallback) {
        this(headless, afterCrawlCallback == null ? null : report -> afterCrawlCallback.run());
    }

    public CrawlScheduler(boolean headless, Consumer<ScheduledCrawlReport> afterCrawlCallback) {
        this.userDAO = new UserDAO();
        this.crawler = new CodeforcesCrawler(headless);
        this.headless = headless;
        this.started = new AtomicBoolean(false);
        this.afterCrawlCallback = afterCrawlCallback;
    }

    public void start() {
        start(0, 1, TimeUnit.DAYS);
    }

    public void startDaily(LocalTime crawlTime, int maxNewSubmissionsPerUser) {
        long initialDelaySeconds = calculateInitialDelaySeconds(crawlTime);
        startInSeconds(
                initialDelaySeconds,
                TimeUnit.DAYS.toSeconds(1),
                maxNewSubmissionsPerUser
        );
        logScheduler(
                "Scheduled crawl enabled at %s every day, maxNewSubmissionsPerUser=%d, firstRunIn=%d seconds".formatted(
                crawlTime,
                maxNewSubmissionsPerUser,
                initialDelaySeconds
                )
        );
    }

    public void start(long initialDelay, long period, TimeUnit unit) {
        start(initialDelay, period, unit, DEFAULT_MAX_NEW_SUBMISSIONS_PER_USER);
    }

    public void start(long initialDelay, long period, TimeUnit unit, int maxNewSubmissionsPerUser) {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(new CrawlThreadFactory());
        logScheduler("Scheduler started initialDelay=%d period=%d unit=%s maxNewSubmissionsPerUser=%d".formatted(
                initialDelay, period, unit, maxNewSubmissionsPerUser));
        scheduler.scheduleAtFixedRate(
                () -> runScheduledCrawl(maxNewSubmissionsPerUser),
                initialDelay,
                period,
                unit
        );
    }

    private void startInSeconds(long initialDelaySeconds, long periodSeconds, int maxNewSubmissionsPerUser) {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(new CrawlThreadFactory());
        logScheduler("Scheduler started initialDelaySeconds=%d periodSeconds=%d maxNewSubmissionsPerUser=%d".formatted(
                initialDelaySeconds, periodSeconds, maxNewSubmissionsPerUser));
        scheduler.scheduleAtFixedRate(
                () -> runScheduledCrawl(maxNewSubmissionsPerUser),
                initialDelaySeconds,
                periodSeconds,
                TimeUnit.SECONDS
        );
    }

    public ScheduledCrawlReport crawlAllUsersNow() {
        return crawlAllUsersNow(DEFAULT_MAX_NEW_SUBMISSIONS_PER_USER);
    }

    public ScheduledCrawlReport crawlAllUsersNow(int maxNewSubmissionsPerUser) {
        return crawlAllUsersSafely(maxNewSubmissionsPerUser);
    }

    public void stop() {
        started.set(false);
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
            logScheduler("Scheduler stopped");
        }
    }

    public boolean isRunning() {
        return started.get();
    }

    private void runScheduledCrawl(int maxNewSubmissionsPerUser) {
        logScheduler("Scheduled crawl tick started maxNewSubmissionsPerUser=%d".formatted(maxNewSubmissionsPerUser));
        ScheduledCrawlReport report = crawlAllUsersSafely(maxNewSubmissionsPerUser);
        logScheduler("Scheduled crawl tick finished " + report.toDebugString());
        if (afterCrawlCallback != null) {
            afterCrawlCallback.accept(report);
        }
    }

    private ScheduledCrawlReport crawlAllUsersSafely(int maxNewSubmissionsPerUser) {
        ScheduledCrawlReport.Builder builder = new ScheduledCrawlReport.Builder();
        WebDriver sharedDriver = null;
        try {
            List<User> users = userDAO.findCrawlEnabledUsers();
            builder.userCount(users.size());
            logScheduler("Loaded crawl-enabled users count=%d".formatted(users.size()));

            if (!users.isEmpty()) {
                sharedDriver = CodeforcesBrowserSession.createCrawlerDriver(headless);
            }

            for (User user : users) {
                try {
                    logScheduler("Scheduled crawl user start handle=%s".formatted(user.getHandle()));
                    CodeforcesCrawler.CrawlReport report = crawler.crawlDetailed(user, maxNewSubmissionsPerUser, sharedDriver);
                    builder.add(report);
                    logScheduler("Scheduled crawl user finished " + report.toDebugString());
                    Thread.sleep(2000);
                } catch (Exception exception) {
                    builder.failedUserCount(builder.failedUserCount() + 1);
                    logScheduler("Scheduled crawl user failed handle=%s error=%s".formatted(
                            user.getHandle(), exception.getMessage()));
                }
            }
        } catch (SQLException exception) {
            builder.failedUserCount(builder.failedUserCount() + 1);
            logScheduler("Failed to load users for scheduled crawl: " + exception.getMessage());
        } finally {
            if (sharedDriver != null) {
                CodeforcesBrowserSession.releaseDriver(sharedDriver);
            }
        }
        return builder.build();
    }

    private long calculateInitialDelaySeconds(LocalTime crawlTime) {
        LocalDateTime now = LocalDateTime.now();
        if (now.getHour() == crawlTime.getHour() && now.getMinute() == crawlTime.getMinute()) {
            return 3L;
        }

        LocalDateTime nextRun = now.withHour(crawlTime.getHour())
                .withMinute(crawlTime.getMinute())
                .withSecond(0)
                .withNano(0);
        if (!nextRun.isAfter(now)) {
            nextRun = nextRun.plusDays(1);
        }
        return Math.max(1L, Duration.between(now, nextRun).toSeconds());
    }

    private void logScheduler(String message) {
        String line = "[%s] [scheduler] %s%n".formatted(Instant.now(), message);
        System.out.print(line);
        try {
            Files.writeString(
                    DEBUG_LOG_PATH,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
        }
    }

    private static final class CrawlThreadFactory implements ThreadFactory {

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "crawl-scheduler");
            thread.setDaemon(true);
            return thread;
        }
    }

    public record ScheduledCrawlReport(
            int userCount,
            int checkedAcceptedCount,
            int existingCount,
            int sourceUnavailableCount,
            int missingSourceElementCount,
            int blockedCount,
            int insertedCount,
            int failedUserCount
    ) {
        public String toDisplayText() {
            return """
                    Đã chạy crawl định kỳ.
                    Handle đã bật crawl: %d
                    Accepted đã kiểm tra: %d
                    Đã có sẵn trong DB: %d
                    Source N/A / không có quyền xem: %d
                    Không đọc được DOM source: %d
                    Bị Codeforces chặn: %d
                    Handle lỗi: %d
                    Source mới lưu vào DB: %d
                    """.formatted(
                    userCount,
                    checkedAcceptedCount,
                    existingCount,
                    sourceUnavailableCount,
                    missingSourceElementCount,
                    blockedCount,
                    failedUserCount,
                    insertedCount
            ).trim();
        }

        private String toDebugString() {
            return "users=%d checkedAccepted=%d existing=%d sourceUnavailable=%d missingSourceElement=%d blocked=%d failedUsers=%d inserted=%d"
                    .formatted(userCount, checkedAcceptedCount, existingCount, sourceUnavailableCount,
                            missingSourceElementCount, blockedCount, failedUserCount, insertedCount);
        }

        private static final class Builder {
            private int userCount;
            private int checkedAcceptedCount;
            private int existingCount;
            private int sourceUnavailableCount;
            private int missingSourceElementCount;
            private int blockedCount;
            private int insertedCount;
            private int failedUserCount;

            private void userCount(int userCount) {
                this.userCount = userCount;
            }

            private int failedUserCount() {
                return failedUserCount;
            }

            private void failedUserCount(int failedUserCount) {
                this.failedUserCount = failedUserCount;
            }

            private void add(CodeforcesCrawler.CrawlReport report) {
                checkedAcceptedCount += report.checkedAcceptedCount();
                existingCount += report.existingCount();
                sourceUnavailableCount += report.sourceUnavailableCount();
                missingSourceElementCount += report.missingSourceElementCount();
                blockedCount += report.blockedCount();
                insertedCount += report.insertedCount();
            }

            private ScheduledCrawlReport build() {
                return new ScheduledCrawlReport(
                        userCount,
                        checkedAcceptedCount,
                        existingCount,
                        sourceUnavailableCount,
                        missingSourceElementCount,
                        blockedCount,
                        insertedCount,
                        failedUserCount
                );
            }
        }
    }
}
