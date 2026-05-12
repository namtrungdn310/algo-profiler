package com.project.model;

import java.sql.Timestamp;

public class Analysis {

    private Long id;
    private Long submissionId;
    private String dataStructures;
    private String algorithms;
    private Double aiUsageScore;
    private String aiUsageLabel;
    private Double confidenceScore;
    private String summary;
    private Timestamp analyzedAt;

    public Analysis() {
    }

    public Analysis(Long id, Long submissionId, String dataStructures, String algorithms, Double aiUsageScore,
                    String aiUsageLabel, Double confidenceScore, String summary, Timestamp analyzedAt) {
        this.id = id;
        this.submissionId = submissionId;
        this.dataStructures = dataStructures;
        this.algorithms = algorithms;
        this.aiUsageScore = aiUsageScore;
        this.aiUsageLabel = aiUsageLabel;
        this.confidenceScore = confidenceScore;
        this.summary = summary;
        this.analyzedAt = analyzedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public String getDataStructures() {
        return dataStructures;
    }

    public void setDataStructures(String dataStructures) {
        this.dataStructures = dataStructures;
    }

    public String getAlgorithms() {
        return algorithms;
    }

    public void setAlgorithms(String algorithms) {
        this.algorithms = algorithms;
    }

    public Double getAiUsageScore() {
        return aiUsageScore;
    }

    public void setAiUsageScore(Double aiUsageScore) {
        this.aiUsageScore = aiUsageScore;
    }

    public String getAiUsageLabel() {
        return aiUsageLabel;
    }

    public void setAiUsageLabel(String aiUsageLabel) {
        this.aiUsageLabel = aiUsageLabel;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Timestamp getAnalyzedAt() {
        return analyzedAt;
    }

    public void setAnalyzedAt(Timestamp analyzedAt) {
        this.analyzedAt = analyzedAt;
    }
}
