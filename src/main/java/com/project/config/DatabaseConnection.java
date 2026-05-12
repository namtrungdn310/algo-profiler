package com.project.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseConnection {

    private static final String DB_DIRECTORY = "database";
    private static final String JDBC_URL = "jdbc:h2:./database/cf_data;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE";
    private static final String JDBC_USER = "sa";
    private static final String JDBC_PASSWORD = "";

    static {
        initializeDatabase();
    }

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
    }

    private static void initializeDatabase() {
        try {
            ensureDatabaseDirectoryExists();
            createTablesIfNeeded();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to initialize H2 database.", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to prepare database directory.", exception);
        }
    }

    private static void ensureDatabaseDirectoryExists() throws Exception {
        Path databasePath = Paths.get(DB_DIRECTORY);
        if (Files.notExists(databasePath)) {
            Files.createDirectories(databasePath);
        }
    }

    private static void createTablesIfNeeded() throws SQLException {
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS CF_USER (
                        ID BIGINT AUTO_INCREMENT PRIMARY KEY,
                        HANDLE VARCHAR(100) NOT NULL UNIQUE,
                        DISPLAY_NAME VARCHAR(255),
                        RATING INT,
                        MAX_RATING INT,
                        RANK_TITLE VARCHAR(100),
                        TOTAL_SCORE DOUBLE DEFAULT 0 NOT NULL,
                        LAST_CRAWLED_AT TIMESTAMP,
                        CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS SUBMISSION (
                        ID BIGINT AUTO_INCREMENT PRIMARY KEY,
                        USER_ID BIGINT NOT NULL,
                        CONTEST_ID INT NOT NULL,
                        SUBMISSION_ID BIGINT NOT NULL,
                        PROBLEM_INDEX VARCHAR(20),
                        PROBLEM_NAME VARCHAR(255),
                        PROGRAMMING_LANGUAGE VARCHAR(100),
                        VERDICT VARCHAR(50),
                        SUBMITTED_AT TIMESTAMP,
                        SOURCE_CODE CLOB,
                        CODE_HASH VARCHAR(128),
                        IS_ANALYZED BOOLEAN DEFAULT FALSE NOT NULL,
                        CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        CONSTRAINT FK_SUBMISSION_USER FOREIGN KEY (USER_ID) REFERENCES CF_USER(ID),
                        CONSTRAINT UK_SUBMISSION_CODEFORCES UNIQUE (SUBMISSION_ID)
                    )
                    """);

            statement.execute("""
                    CREATE TABLE IF NOT EXISTS ANALYSIS (
                        ID BIGINT AUTO_INCREMENT PRIMARY KEY,
                        SUBMISSION_ID BIGINT NOT NULL,
                        DATA_STRUCTURES VARCHAR(1000),
                        ALGORITHMS VARCHAR(1000),
                        AI_USAGE_SCORE DOUBLE,
                        AI_USAGE_LABEL VARCHAR(50),
                        CONFIDENCE_SCORE DOUBLE,
                        SUMMARY CLOB,
                        ANALYZED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                        CONSTRAINT FK_ANALYSIS_SUBMISSION FOREIGN KEY (SUBMISSION_ID) REFERENCES SUBMISSION(ID),
                        CONSTRAINT UK_ANALYSIS_SUBMISSION UNIQUE (SUBMISSION_ID)
                    )
                    """);

            statement.execute("""
                    ALTER TABLE SUBMISSION
                    ADD COLUMN IF NOT EXISTS IS_ANALYZED BOOLEAN DEFAULT FALSE NOT NULL
                    """);

            statement.execute("""
                    ALTER TABLE CF_USER
                    ADD COLUMN IF NOT EXISTS TOTAL_SCORE DOUBLE DEFAULT 0 NOT NULL
                    """);
        }
    }
}
