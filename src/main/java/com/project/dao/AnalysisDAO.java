package com.project.dao;

import com.project.config.DatabaseConnection;
import com.project.model.Analysis;
import com.project.service.EvaluationService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AnalysisDAO {

    public Analysis insert(Analysis analysis) throws SQLException {
        String sql = """
                INSERT INTO ANALYSIS (
                    SUBMISSION_ID, DATA_STRUCTURES, ALGORITHMS, AI_USAGE_SCORE,
                    AI_USAGE_LABEL, CONFIDENCE_SCORE, SUMMARY
                )
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, analysis.getSubmissionId());
            statement.setString(2, analysis.getDataStructures());
            statement.setString(3, analysis.getAlgorithms());
            statement.setDouble(4, analysis.getAiUsageScore());
            statement.setString(5, analysis.getAiUsageLabel());
            statement.setDouble(6, analysis.getConfidenceScore());
            statement.setString(7, analysis.getSummary());
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    analysis.setId(generatedKeys.getLong(1));
                }
            }
        }
        return analysis;
    }

    public Optional<Analysis> findBySubmissionId(Long submissionId) throws SQLException {
        String sql = "SELECT * FROM ANALYSIS WHERE SUBMISSION_ID = ?";

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

    public List<EvaluationService.AnalysisDetail> findDetailsByUserId(Long userId) throws SQLException {
        String sql = """
                SELECT
                    a.SUBMISSION_ID,
                    s.SUBMISSION_ID AS CODEFORCES_SUBMISSION_ID,
                    s.PROBLEM_INDEX,
                    s.PROBLEM_NAME,
                    s.PROGRAMMING_LANGUAGE,
                    s.VERDICT,
                    a.DATA_STRUCTURES,
                    a.ALGORITHMS,
                    a.AI_USAGE_SCORE,
                    a.AI_USAGE_LABEL,
                    a.SUMMARY
                FROM ANALYSIS a
                INNER JOIN SUBMISSION s ON a.SUBMISSION_ID = s.ID
                WHERE s.USER_ID = ?
                ORDER BY s.SUBMITTED_AT DESC, s.ID DESC
                """;
        List<EvaluationService.AnalysisDetail> details = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    details.add(new EvaluationService.AnalysisDetail(
                            resultSet.getLong("SUBMISSION_ID"),
                            resultSet.getLong("CODEFORCES_SUBMISSION_ID"),
                            resultSet.getString("PROBLEM_INDEX"),
                            resultSet.getString("PROBLEM_NAME"),
                            resultSet.getString("PROGRAMMING_LANGUAGE"),
                            resultSet.getString("VERDICT"),
                            resultSet.getString("DATA_STRUCTURES"),
                            resultSet.getString("ALGORITHMS"),
                            resultSet.getDouble("AI_USAGE_SCORE"),
                            resultSet.getString("AI_USAGE_LABEL"),
                            resultSet.getString("SUMMARY")
                    ));
                }
            }
        }
        return details;
    }

    private Analysis mapRow(ResultSet resultSet) throws SQLException {
        return new Analysis(
                resultSet.getLong("ID"),
                resultSet.getLong("SUBMISSION_ID"),
                resultSet.getString("DATA_STRUCTURES"),
                resultSet.getString("ALGORITHMS"),
                resultSet.getDouble("AI_USAGE_SCORE"),
                resultSet.getString("AI_USAGE_LABEL"),
                resultSet.getDouble("CONFIDENCE_SCORE"),
                resultSet.getString("SUMMARY"),
                resultSet.getTimestamp("ANALYZED_AT")
        );
    }
}
