package com.project.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Properties;

public final class AppConfig {

    private static final Path CONFIG_PATH = Paths.get("config.properties");
    private static final String AI_API_KEY = "ai.api.key";
    private static final String AI_API_URL = "ai.api.url";
    private static final String AI_MODEL = "ai.model";
    private static final String CRAWL_SCHEDULE_ENABLED = "crawl.schedule.enabled";
    private static final String CRAWL_DAILY_TIME = "crawl.daily.time";
    private static final String CRAWL_MAX_NEW_PER_USER = "crawl.max_new_per_user";
    private static final String DEFAULT_AI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";
    private static final String DEFAULT_AI_MODEL = "gemini-1.5-flash";
    private static final String DEFAULT_CRAWL_DAILY_TIME = "02:00";
    private static final int DEFAULT_CRAWL_MAX_NEW_PER_USER = 5;
    private static final DateTimeFormatter CRAWL_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private AppConfig() {
    }

    public static String getAiApiKey() {
        return loadProperties().getProperty(AI_API_KEY, "").trim();
    }

    public static void setAiApiKey(String apiKey) throws IOException {
        Properties properties = loadProperties();
        properties.setProperty(AI_API_KEY, apiKey == null ? "" : apiKey.trim());
        saveProperties(properties);
    }

    public static String getAiApiUrl() {
        return loadProperties().getProperty(AI_API_URL, DEFAULT_AI_API_URL).trim();
    }

    public static void setAiApiUrl(String apiUrl) throws IOException {
        Properties properties = loadProperties();
        properties.setProperty(AI_API_URL, apiUrl == null || apiUrl.isBlank() ? DEFAULT_AI_API_URL : apiUrl.trim());
        saveProperties(properties);
    }

    public static String getAiModel() {
        return loadProperties().getProperty(AI_MODEL, DEFAULT_AI_MODEL).trim();
    }

    public static boolean isCrawlScheduleEnabled() {
        return Boolean.parseBoolean(loadProperties().getProperty(CRAWL_SCHEDULE_ENABLED, "false").trim());
    }

    public static LocalTime getCrawlDailyTime() {
        String rawTime = loadProperties().getProperty(CRAWL_DAILY_TIME, DEFAULT_CRAWL_DAILY_TIME).trim();
        try {
            return LocalTime.parse(rawTime, CRAWL_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            return LocalTime.parse(DEFAULT_CRAWL_DAILY_TIME, CRAWL_TIME_FORMATTER);
        }
    }

    public static String getCrawlDailyTimeText() {
        return getCrawlDailyTime().format(CRAWL_TIME_FORMATTER);
    }

    public static int getCrawlMaxNewPerUser() {
        String rawLimit = loadProperties().getProperty(
                CRAWL_MAX_NEW_PER_USER,
                String.valueOf(DEFAULT_CRAWL_MAX_NEW_PER_USER)
        ).trim();
        try {
            int limit = Integer.parseInt(rawLimit);
            return limit <= 0 ? DEFAULT_CRAWL_MAX_NEW_PER_USER : limit;
        } catch (NumberFormatException exception) {
            return DEFAULT_CRAWL_MAX_NEW_PER_USER;
        }
    }

    public static void setCrawlSchedule(boolean enabled, String dailyTime, int maxNewPerUser) throws IOException {
        LocalTime parsedTime;
        try {
            parsedTime = LocalTime.parse(dailyTime == null ? "" : dailyTime.trim(), CRAWL_TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Giờ crawl phải đúng định dạng HH:mm, ví dụ 02:30.", exception);
        }

        Properties properties = loadProperties();
        properties.setProperty(CRAWL_SCHEDULE_ENABLED, Boolean.toString(enabled));
        properties.setProperty(CRAWL_DAILY_TIME, parsedTime.format(CRAWL_TIME_FORMATTER));
        properties.setProperty(CRAWL_MAX_NEW_PER_USER, String.valueOf(Math.max(1, maxNewPerUser)));
        saveProperties(properties);
    }

    public static void ensureConfigFileExists() throws IOException {
        if (Files.exists(CONFIG_PATH)) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty(AI_API_KEY, "");
        properties.setProperty(AI_API_URL, DEFAULT_AI_API_URL);
        properties.setProperty(AI_MODEL, DEFAULT_AI_MODEL);
        properties.setProperty(CRAWL_SCHEDULE_ENABLED, "false");
        properties.setProperty(CRAWL_DAILY_TIME, DEFAULT_CRAWL_DAILY_TIME);
        properties.setProperty(CRAWL_MAX_NEW_PER_USER, String.valueOf(DEFAULT_CRAWL_MAX_NEW_PER_USER));
        saveProperties(properties);
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        try {
            ensureConfigFileExists();
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể đọc file config.properties", exception);
        }
        return properties;
    }

    private static void saveProperties(Properties properties) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(outputStream, "AlgoProfiler configuration");
        }
    }
}
