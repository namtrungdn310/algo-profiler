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
                INSERT INTO CF_USER (HANDLE, DISPLAY_NAME, RATING, MAX_RATING, RANK_TITLE, TOTAL_SCORE, LAST_CRAWLED_AT)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, user.getHandle());
            statement.setString(2, user.getDisplayName());
            setNullableInteger(statement, 3, user.getRating());
            setNullableInteger(statement, 4, user.getMaxRating());
            statement.setString(5, user.getRankTitle());
            statement.setDouble(6, user.getTotalScore() == null ? 0D : user.getTotalScore());
            statement.setTimestamp(7, user.getLastCrawledAt());
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
                SET DISPLAY_NAME = ?, RATING = ?, MAX_RATING = ?, RANK_TITLE = ?, TOTAL_SCORE = ?, LAST_CRAWLED_AT = ?
                WHERE ID = ?
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.getDisplayName());
            setNullableInteger(statement, 2, user.getRating());
            setNullableInteger(statement, 3, user.getMaxRating());
            statement.setString(4, user.getRankTitle());
            statement.setDouble(5, user.getTotalScore() == null ? 0D : user.getTotalScore());
            statement.setTimestamp(6, user.getLastCrawledAt());
            statement.setLong(7, user.getId());
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

    public boolean deleteById(Long id) throws SQLException {
        String sql = "DELETE FROM CF_USER WHERE ID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            return statement.executeUpdate() > 0;
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
                resultSet.getTimestamp("LAST_CRAWLED_AT"),
                resultSet.getTimestamp("CREATED_AT")
        );
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
