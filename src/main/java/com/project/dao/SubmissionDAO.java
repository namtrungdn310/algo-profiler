package com.project.dao;

import com.project.config.DatabaseConnection;
import com.project.model.Submission;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SubmissionDAO {

    public Submission insert(Submission submission) throws SQLException {
        String sql = """
                INSERT INTO SUBMISSION (
                    USER_ID, CONTEST_ID, SUBMISSION_ID, PROBLEM_INDEX, PROBLEM_NAME,
                    PROGRAMMING_LANGUAGE, VERDICT, SUBMITTED_AT, SOURCE_CODE, CODE_HASH, IS_ANALYZED
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, submission.getUserId());
            setNullableInteger(statement, 2, submission.getContestId());
            statement.setLong(3, submission.getSubmissionId());
            statement.setString(4, submission.getProblemIndex());
            statement.setString(5, submission.getProblemName());
            statement.setString(6, submission.getProgrammingLanguage());
            statement.setString(7, submission.getVerdict());
            statement.setTimestamp(8, submission.getSubmittedAt());
            statement.setString(9, submission.getSourceCode());
            statement.setString(10, submission.getCodeHash());
            statement.setBoolean(11, submission.isAnalyzed());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    submission.setId(generatedKeys.getLong(1));
                }
            }
        }
        return submission;
    }

    public Optional<Submission> findBySubmissionId(Long submissionId) throws SQLException {
        String sql = "SELECT * FROM SUBMISSION WHERE SUBMISSION_ID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, submissionId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapRow(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    public List<Submission> findByUserId(Long userId) throws SQLException {
        String sql = "SELECT * FROM SUBMISSION WHERE USER_ID = ? ORDER BY SUBMITTED_AT DESC, ID DESC";
        List<Submission> submissions = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    submissions.add(mapRow(resultSet));
                }
            }
        }
        return submissions;
    }

    public List<Submission> findAll() throws SQLException {
        String sql = "SELECT * FROM SUBMISSION ORDER BY SUBMITTED_AT DESC, ID DESC";
        List<Submission> submissions = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                submissions.add(mapRow(resultSet));
            }
        }
        return submissions;
    }

    public boolean existsBySubmissionId(Long submissionId) throws SQLException {
        String sql = "SELECT 1 FROM SUBMISSION WHERE SUBMISSION_ID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, submissionId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public int countByUserId(Long userId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM SUBMISSION WHERE USER_ID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt(1);
                }
            }
        }
        return 0;
    }

    public List<Submission> findUnanalyzedSubmissions(int limit) throws SQLException {
        String sql = """
                SELECT * FROM SUBMISSION
                WHERE IS_ANALYZED = FALSE
                ORDER BY SUBMITTED_AT DESC, ID DESC
                LIMIT ?
                """;
        List<Submission> submissions = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    submissions.add(mapRow(resultSet));
                }
            }
        }
        return submissions;
    }

    public boolean updateAnalyzedStatus(Long id, boolean analyzed) throws SQLException {
        String sql = "UPDATE SUBMISSION SET IS_ANALYZED = ? WHERE ID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, analyzed);
            statement.setLong(2, id);
            return statement.executeUpdate() > 0;
        }
    }

    private Submission mapRow(ResultSet resultSet) throws SQLException {
        return new Submission(
                resultSet.getLong("ID"),
                resultSet.getLong("USER_ID"),
                getNullableInteger(resultSet, "CONTEST_ID"),
                resultSet.getLong("SUBMISSION_ID"),
                resultSet.getString("PROBLEM_INDEX"),
                resultSet.getString("PROBLEM_NAME"),
                resultSet.getString("PROGRAMMING_LANGUAGE"),
                resultSet.getString("VERDICT"),
                resultSet.getTimestamp("SUBMITTED_AT"),
                resultSet.getString("SOURCE_CODE"),
                resultSet.getString("CODE_HASH"),
                resultSet.getBoolean("IS_ANALYZED"),
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
