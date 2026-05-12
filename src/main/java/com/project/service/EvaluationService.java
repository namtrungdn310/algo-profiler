package com.project.service;

import com.project.dao.AnalysisDAO;
import com.project.dao.UserDAO;
import com.project.model.Analysis;
import com.project.model.User;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EvaluationService {

    private static final double HIGH_AI_THRESHOLD = 70.0;
    private static final double HIGH_AI_PENALTY = 20.0;

    private final UserDAO userDAO;
    private final AnalysisDAO analysisDAO;

    public EvaluationService() {
        this.userDAO = new UserDAO();
        this.analysisDAO = new AnalysisDAO();
    }

    public List<UserEvaluation> evaluateAllUsers() throws SQLException {
        List<User> users = userDAO.findAll();
        List<UserEvaluation> evaluations = new ArrayList<>();

        for (User user : users) {
            UserEvaluation evaluation = evaluateUser(user);
            userDAO.updateTotalScore(user.getId(), evaluation.totalScore());
            user.setTotalScore(evaluation.totalScore());
            evaluations.add(evaluation);
        }

        return evaluations;
    }

    public UserEvaluation evaluateUser(User user) throws SQLException {
        List<AnalysisDetail> details = analysisDAO.findDetailsByUserId(user.getId());
        Set<String> uniqueTopics = new LinkedHashSet<>();
        double aiSum = 0;

        for (AnalysisDetail detail : details) {
            uniqueTopics.addAll(splitTopics(detail.dataStructures()));
            uniqueTopics.addAll(splitTopics(detail.algorithms()));
            aiSum += detail.aiUsageScore();
        }

        double averageAi = details.isEmpty() ? 0 : aiSum / details.size();
        double totalScore = uniqueTopics.size() * 10.0;
        String riskLabel = "An toàn";

        if (averageAi > HIGH_AI_THRESHOLD) {
            totalScore = Math.max(0, totalScore - HIGH_AI_PENALTY);
            riskLabel = "Nguy cơ dùng AI cao";
        } else if (averageAi >= 40) {
            riskLabel = "Cần xem xét";
        }

        return new UserEvaluation(
                user.getId(),
                user.getHandle(),
                totalScore,
                averageAi,
                riskLabel,
                details
        );
    }

    private List<String> splitTopics(String csv) {
        List<String> values = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return values;
        }

        String[] parts = csv.split(",");
        for (String part : parts) {
            String normalized = part.trim();
            if (!normalized.isBlank()) {
                values.add(normalized.toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    public record UserEvaluation(
            Long userId,
            String handle,
            double totalScore,
            double averageAiProbability,
            String aiRiskLabel,
            List<AnalysisDetail> details
    ) {
    }

    public record AnalysisDetail(
            Long submissionId,
            Long codeforcesSubmissionId,
            String problemCode,
            String problemName,
            String programmingLanguage,
            String verdict,
            String dataStructures,
            String algorithms,
            double aiUsageScore,
            String aiUsageLabel,
            String summary
    ) {
    }
}
