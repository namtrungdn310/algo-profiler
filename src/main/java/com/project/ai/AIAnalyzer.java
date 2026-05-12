package com.project.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.project.config.AppConfig;
import com.project.dao.AnalysisDAO;
import com.project.dao.SubmissionDAO;
import com.project.model.Analysis;
import com.project.model.Submission;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class AIAnalyzer {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int DEFAULT_BATCH_SIZE = 10;
    private static final int MAX_SOURCE_LENGTH = 16_000;
    private static final String SYSTEM_PROMPT = """
            Phân tích code này, trả về CHỈ MỘT CHUỖI JSON thuần túy (không có markdown backticks).
            Định dạng: {"data_structures": [...], "algorithms": [...], "ai_generated_probability": <số 0-100>}
            """;

    private final SubmissionDAO submissionDAO;
    private final AnalysisDAO analysisDAO;
    private final HttpClient httpClient;
    private final Gson gson;

    public AIAnalyzer() {
        this.submissionDAO = new SubmissionDAO();
        this.analysisDAO = new AnalysisDAO();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
        this.gson = new Gson();
    }

    public int analyzePendingSubmissions() throws SQLException {
        return analyzePendingSubmissions(DEFAULT_BATCH_SIZE);
    }

    public int analyzePendingSubmissions(int limit) throws SQLException {
        ensureApiKeyConfigured();

        List<Submission> submissions = submissionDAO.findUnanalyzedSubmissions(limit);
        int processedCount = 0;

        for (Submission submission : submissions) {
            try {
                AIAnalysisResult result = analyzeSubmission(submission);
                analysisDAO.insert(toAnalysis(submission, result));
            } catch (Exception exception) {
                analysisDAO.insert(buildErrorAnalysis(submission, exception));
            } finally {
                submissionDAO.updateAnalyzedStatus(submission.getId(), true);
                processedCount++;
            }
        }

        return processedCount;
    }

    public Analysis analyzeAndStore(Submission submission) throws SQLException {
        ensureApiKeyConfigured();
        try {
            AIAnalysisResult result = analyzeSubmission(submission);
            Analysis analysis = toAnalysis(submission, result);
            analysisDAO.insert(analysis);
            submissionDAO.updateAnalyzedStatus(submission.getId(), true);
            return analysis;
        } catch (Exception exception) {
            Analysis analysis = buildErrorAnalysis(submission, exception);
            analysisDAO.insert(analysis);
            submissionDAO.updateAnalyzedStatus(submission.getId(), true);
            return analysis;
        }
    }

    public void testConnection() throws IOException, InterruptedException {
        ensureApiKeyConfigured();
        String apiUrl = buildGeminiUrl();
        
        JsonObject requestBody = new JsonObject();
        JsonArray contents = new JsonArray();
        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");
        JsonArray userParts = new JsonArray();
        JsonObject userText = new JsonObject();
        userText.addProperty("text", "Ping");
        userParts.add(userText);
        userContent.add("parts", userParts);
        contents.add(userContent);
        requestBody.add("contents", contents);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            throw new IOException("AI API lỗi HTTP " + response.statusCode() + ": " + truncate(response.body(), 200));
        }
    }

    public AIAnalysisResult analyzeSubmission(Submission submission) throws IOException, InterruptedException {
        ensureApiKeyConfigured();

        String apiUrl = buildGeminiUrl();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(submission), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 429) {
            throw new IOException("Hết quota hoặc bị rate limit từ AI API (HTTP 429).");
        }
        if (response.statusCode() >= 400) {
            throw new IOException("AI API lỗi HTTP " + response.statusCode() + ": " + truncate(response.body(), 300));
        }

        return parseAnalysisResult(response.body());
    }

    private void ensureApiKeyConfigured() {
        String apiKey = AppConfig.getAiApiKey();
        if (apiKey.isBlank()) {
            throw new IllegalStateException("API Key đang trống. Hãy vào giao diện Cài đặt để nhập API Key.");
        }
    }

    private String buildGeminiUrl() {
        String apiKey = AppConfig.getAiApiKey();
        return AppConfig.getAiApiUrl() + "?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
    }

    private String buildRequestBody(Submission submission) {
        JsonObject request = new JsonObject();

        JsonObject systemInstruction = new JsonObject();
        JsonArray systemParts = new JsonArray();
        JsonObject systemText = new JsonObject();
        systemText.addProperty("text", SYSTEM_PROMPT);
        systemParts.add(systemText);
        systemInstruction.add("parts", systemParts);
        request.add("system_instruction", systemInstruction);

        JsonArray contents = new JsonArray();
        JsonObject userContent = new JsonObject();
        userContent.addProperty("role", "user");
        JsonArray userParts = new JsonArray();
        JsonObject userText = new JsonObject();
        userText.addProperty("text", buildUserPrompt(submission));
        userParts.add(userText);
        userContent.add("parts", userParts);
        contents.add(userContent);
        request.add("contents", contents);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("temperature", 0.1);
        generationConfig.addProperty("responseMimeType", "application/json");
        request.add("generationConfig", generationConfig);

        return gson.toJson(request);
    }

    private String buildUserPrompt(Submission submission) {
        return """
                Thông tin bài nộp:
                - submission_id: %d
                - problem: %s %s
                - language: %s
                - verdict: %s

                Source code:
                %s
                """.formatted(
                submission.getSubmissionId(),
                safeString(submission.getProblemIndex()),
                safeString(submission.getProblemName()),
                safeString(submission.getProgrammingLanguage()),
                safeString(submission.getVerdict()),
                truncate(safeString(submission.getSourceCode()), MAX_SOURCE_LENGTH)
        );
    }

    private AIAnalysisResult parseAnalysisResult(String responseBody) throws IOException {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray candidates = root.getAsJsonArray("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new IOException("AI API không trả về candidates.");
            }

            JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
            JsonObject content = firstCandidate.getAsJsonObject("content");
            JsonArray parts = content.getAsJsonArray("parts");
            if (parts == null || parts.isEmpty()) {
                throw new IOException("AI API không trả về nội dung phân tích.");
            }

            String rawJson = parts.get(0).getAsJsonObject().get("text").getAsString().trim();
            JsonObject analysisObject = JsonParser.parseString(stripCodeFence(rawJson)).getAsJsonObject();

            List<String> dataStructures = readStringArray(analysisObject.getAsJsonArray("data_structures"));
            List<String> algorithms = readStringArray(analysisObject.getAsJsonArray("algorithms"));
            double aiProbability = clampProbability(analysisObject.get("ai_generated_probability").getAsDouble());

            return new AIAnalysisResult(dataStructures, algorithms, aiProbability);
        } catch (IllegalStateException | NullPointerException | JsonSyntaxException exception) {
            throw new IOException("Không parse được JSON trả về từ AI: " + exception.getMessage(), exception);
        }
    }

    private Analysis toAnalysis(Submission submission, AIAnalysisResult result) {
        Analysis analysis = new Analysis();
        analysis.setSubmissionId(submission.getId());
        analysis.setDataStructures(String.join(", ", result.dataStructures()));
        analysis.setAlgorithms(String.join(", ", result.algorithms()));
        analysis.setAiUsageScore(result.aiGeneratedProbability());
        analysis.setAiUsageLabel(toAiUsageLabel(result.aiGeneratedProbability()));
        analysis.setConfidenceScore(100.0);
        analysis.setSummary(buildSummary(result));
        return analysis;
    }

    private Analysis buildErrorAnalysis(Submission submission, Exception exception) {
        Analysis analysis = new Analysis();
        analysis.setSubmissionId(submission.getId());
        analysis.setDataStructures("Lỗi phân tích");
        analysis.setAlgorithms("Lỗi phân tích");
        analysis.setAiUsageScore(0.0);
        analysis.setAiUsageLabel("Lỗi phân tích");
        analysis.setConfidenceScore(0.0);
        analysis.setSummary("Lỗi phân tích: " + truncate(exception.getMessage(), 250));
        return analysis;
    }

    private String buildSummary(AIAnalysisResult result) {
        StringJoiner joiner = new StringJoiner(" | ");
        joiner.add("CTDL: " + formatList(result.dataStructures()));
        joiner.add("Thuật toán: " + formatList(result.algorithms()));
        joiner.add("Khả năng AI: " + String.format("%.1f%%", result.aiGeneratedProbability()));
        return joiner.toString();
    }

    private String formatList(List<String> values) {
        return values.isEmpty() ? "Không xác định" : String.join(", ", values);
    }

    private List<String> readStringArray(JsonArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) {
            return values;
        }
        for (JsonElement element : array) {
            String value = element.getAsString().trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private double clampProbability(double probability) {
        return Math.max(0, Math.min(100, probability));
    }

    private String toAiUsageLabel(double probability) {
        if (probability > 80) {
            return "Nguy cơ rất cao";
        }
        if (probability > 60) {
            return "Nguy cơ cao";
        }
        if (probability > 40) {
            return "Trung bình";
        }
        return "Thấp";
    }

    private String stripCodeFence(String rawValue) {
        String trimmed = rawValue.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineBreak = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLineBreak < 0 || lastFence <= firstLineBreak) {
            return trimmed;
        }
        return trimmed.substring(firstLineBreak + 1, lastFence).trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    public record AIAnalysisResult(List<String> dataStructures, List<String> algorithms, double aiGeneratedProbability) {
    }
}
