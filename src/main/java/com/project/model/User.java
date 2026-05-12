package com.project.model;

import java.sql.Timestamp;

public class User {

    private Long id;
    private String handle;
    private String displayName;
    private Integer rating;
    private Integer maxRating;
    private String rankTitle;
    private Double totalScore;
    private Timestamp lastCrawledAt;
    private Timestamp createdAt;

    public User() {
    }

    public User(Long id, String handle, String displayName, Integer rating, Integer maxRating,
                String rankTitle, Double totalScore, Timestamp lastCrawledAt, Timestamp createdAt) {
        this.id = id;
        this.handle = handle;
        this.displayName = displayName;
        this.rating = rating;
        this.maxRating = maxRating;
        this.rankTitle = rankTitle;
        this.totalScore = totalScore;
        this.lastCrawledAt = lastCrawledAt;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public Integer getMaxRating() {
        return maxRating;
    }

    public void setMaxRating(Integer maxRating) {
        this.maxRating = maxRating;
    }

    public String getRankTitle() {
        return rankTitle;
    }

    public void setRankTitle(String rankTitle) {
        this.rankTitle = rankTitle;
    }

    public Double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(Double totalScore) {
        this.totalScore = totalScore;
    }

    public Timestamp getLastCrawledAt() {
        return lastCrawledAt;
    }

    public void setLastCrawledAt(Timestamp lastCrawledAt) {
        this.lastCrawledAt = lastCrawledAt;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}
