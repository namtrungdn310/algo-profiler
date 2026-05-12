package com.project.model;

import java.sql.Timestamp;

public class Submission {

    private Long id;
    private Long userId;
    private Integer contestId;
    private Long submissionId;
    private String problemIndex;
    private String problemName;
    private String programmingLanguage;
    private String verdict;
    private Timestamp submittedAt;
    private String sourceCode;
    private String codeHash;
    private boolean analyzed;
    private Timestamp createdAt;

    public Submission() {
    }

    public Submission(Long id, Long userId, Integer contestId, Long submissionId, String problemIndex,
                      String problemName, String programmingLanguage, String verdict, Timestamp submittedAt,
                      String sourceCode, String codeHash, boolean analyzed, Timestamp createdAt) {
        this.id = id;
        this.userId = userId;
        this.contestId = contestId;
        this.submissionId = submissionId;
        this.problemIndex = problemIndex;
        this.problemName = problemName;
        this.programmingLanguage = programmingLanguage;
        this.verdict = verdict;
        this.submittedAt = submittedAt;
        this.sourceCode = sourceCode;
        this.codeHash = codeHash;
        this.analyzed = analyzed;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getContestId() {
        return contestId;
    }

    public void setContestId(Integer contestId) {
        this.contestId = contestId;
    }

    public Long getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(Long submissionId) {
        this.submissionId = submissionId;
    }

    public String getProblemIndex() {
        return problemIndex;
    }

    public void setProblemIndex(String problemIndex) {
        this.problemIndex = problemIndex;
    }

    public String getProblemName() {
        return problemName;
    }

    public void setProblemName(String problemName) {
        this.problemName = problemName;
    }

    public String getProgrammingLanguage() {
        return programmingLanguage;
    }

    public void setProgrammingLanguage(String programmingLanguage) {
        this.programmingLanguage = programmingLanguage;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public Timestamp getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Timestamp submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public boolean isAnalyzed() {
        return analyzed;
    }

    public void setAnalyzed(boolean analyzed) {
        this.analyzed = analyzed;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
