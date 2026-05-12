package com.project.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class AppConfig {

    private static final Path CONFIG_PATH = Paths.get("config.properties");
    private static final String AI_API_KEY = "ai.api.key";
    private static final String AI_API_URL = "ai.api.url";
    private static final String AI_MODEL = "ai.model";
    private static final String DEFAULT_AI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent";
    private static final String DEFAULT_AI_MODEL = "gemini-1.5-flash";

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

    public static void ensureConfigFileExists() throws IOException {
        if (Files.exists(CONFIG_PATH)) {
            return;
        }

        Properties properties = new Properties();
        properties.setProperty(AI_API_KEY, "");
        properties.setProperty(AI_API_URL, DEFAULT_AI_API_URL);
        properties.setProperty(AI_MODEL, DEFAULT_AI_MODEL);
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
