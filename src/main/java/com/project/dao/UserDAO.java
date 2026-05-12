package com.project.dao;

import com.project.config.DatabaseConnection;
import com.project.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserDAO {

    public User insert(User user) throws SQLException {
        String sql = """
                INSERT INTO CF_USER (
                    HANDLE, DISPLAY_NAME, RATING, MAX_RATING, RANK_TITLE,
                    TOTAL_SCORE, CRAWL_ENABLED, LAST_CRAWLED_AT
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.getHandle());
            statement.setString(2, user.getDisplayName());
            setNullableInteger(statement, 3, user.getRating());
            setNullableInteger(statement, 4, user.getMaxRating());
            statement.setString(5, user.getRankTitle());
            statement.setDouble(6, user.getTotalScore() == null ? 0D : user.getTotalScore());
            statement.setBoolean(7, user.isCrawlEnabled());
            statement.setTimestamp(8, user.getLastCrawledAt());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    user.setId(generatedKeys.getLong(1));
                }
            }
        }
        return user;
    }

    public boolean existsByHandle(String handle) throws SQLException {
        String sql = "SELECT 1 FROM CF_USER WHERE HANDLE = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, handle);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public Optional<User> findByHandle(String handle) throws SQLException {
        String sql = "SELECT * FROM CF_USER WHERE HANDLE = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, handle);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<User> findById(Long id) throws SQLException {
        String sql = "SELECT * FROM CF_USER WHERE ID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public List<User> findAll() throws SQLException {
        String sql = "SELECT * FROM CF_USER ORDER BY CREATED_AT DESC";
        List<User> users = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                users.add(mapRow(resultSet));
            }
        }
        return users;
    }

    public List<User> findCrawlEnabledUsers() throws SQLException {
        String sql = "SELECT * FROM CF_USER WHERE CRAWL_ENABLED = TRUE ORDER BY CREATED_AT DESC";
        List<User> users = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                users.add(mapRow(resultSet));
            }
        }
        return users;
    }

    public int countAll() throws SQLException {
        return countBySql("SELECT COUNT(*) FROM CF_USER");
    }

    public int countCrawlEnabled() throws SQLException {
        return countBySql("SELECT COUNT(*) FROM CF_USER WHERE CRAWL_ENABLED = TRUE");
    }

    public boolean updateLastCrawledAt(Long userId, Timestamp lastCrawledAt) throws SQLException {
        String sql = "UPDATE CF_USER SET LAST_CRAWLED_AT = ? WHERE ID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setTimestamp(1, lastCrawledAt);
            statement.setLong(2, userId);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean updateProfile(User user) throws SQLException {
        String sql = """
                UPDATE CF_USER
                SET DISPLAY_NAME = ?, RATING = ?, MAX_RATING = ?, RANK_TITLE = ?,
                    TOTAL_SCORE = ?, CRAWL_ENABLED = ?, LAST_CRAWLED_AT = ?
                WHERE ID = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.getDisplayName());
            setNullableInteger(statement, 2, user.getRating());
            setNullableInteger(statement, 3, user.getMaxRating());
            statement.setString(4, user.getRankTitle());
            statement.setDouble(5, user.getTotalScore() == null ? 0D : user.getTotalScore());
            statement.setBoolean(6, user.isCrawlEnabled());
            statement.setTimestamp(7, user.getLastCrawledAt());
            statement.setLong(8, user.getId());
            return statement.executeUpdate() > 0;
        }
    }

    public boolean updateTotalScore(Long userId, double totalScore) throws SQLException {
        String sql = "UPDATE CF_USER SET TOTAL_SCORE = ? WHERE ID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDouble(1, totalScore);
            statement.setLong(2, userId);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean updateCrawlEnabled(Long userId, boolean crawlEnabled) throws SQLException {
        String sql = "UPDATE CF_USER SET CRAWL_ENABLED = ? WHERE ID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, crawlEnabled);
            statement.setLong(2, userId);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean deleteById(Long id) throws SQLException {
        String deleteAnalysisSql = """
                DELETE FROM ANALYSIS
                WHERE SUBMISSION_ID IN (
                    SELECT ID FROM SUBMISSION WHERE USER_ID = ?
                )
                """;
        String deleteSubmissionsSql = "DELETE FROM SUBMISSION WHERE USER_ID = ?";
        String deleteUserSql = "DELETE FROM CF_USER WHERE ID = ?";

        try (Connection connection = DatabaseConnection.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement deleteAnalysis = connection.prepareStatement(deleteAnalysisSql);
                 PreparedStatement deleteSubmissions = connection.prepareStatement(deleteSubmissionsSql);
                 PreparedStatement deleteUser = connection.prepareStatement(deleteUserSql)) {
                deleteAnalysis.setLong(1, id);
                deleteAnalysis.executeUpdate();

                deleteSubmissions.setLong(1, id);
                deleteSubmissions.executeUpdate();

                deleteUser.setLong(1, id);
                boolean deleted = deleteUser.executeUpdate() > 0;
                connection.commit();
                return deleted;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private User mapRow(ResultSet resultSet) throws SQLException {
        return new User(
                resultSet.getLong("ID"),
                resultSet.getString("HANDLE"),
                resultSet.getString("DISPLAY_NAME"),
                getNullableInteger(resultSet, "RATING"),
                getNullableInteger(resultSet, "MAX_RATING"),
                resultSet.getString("RANK_TITLE"),
                resultSet.getDouble("TOTAL_SCORE"),
                resultSet.getBoolean("CRAWL_ENABLED"),
                resultSet.getTimestamp("LAST_CRAWLED_AT"),
                resultSet.getTimestamp("CREATED_AT")
        );
    }

    private int countBySql(String sql) throws SQLException {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private void setNullableInteger(PreparedStatement statement, int parameterIndex, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(parameterIndex, java.sql.Types.INTEGER);
            return;
        }
        statement.setInt(parameterIndex, value);
    }

    private Integer getNullableInteger(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }
}
