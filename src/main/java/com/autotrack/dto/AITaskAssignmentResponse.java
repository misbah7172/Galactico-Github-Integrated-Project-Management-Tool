package com.autotrack.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for AI task assignment with full explainability.
 */
public class AITaskAssignmentResponse {

    private String requestId;
    private Long taskId;
    private String taskTitle;
    
    private Recommendation selectedRecommendation;
    private List<Recommendation> allRecommendations;
    
    private String decisionMode;
    private LocalDateTime timestamp;
    
    private TeamSummary teamSummary;
    
    public AITaskAssignmentResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public String getTaskTitle() { return taskTitle; }
    public void setTaskTitle(String taskTitle) { this.taskTitle = taskTitle; }

    public Recommendation getSelectedRecommendation() { return selectedRecommendation; }
    public void setSelectedRecommendation(Recommendation selectedRecommendation) { this.selectedRecommendation = selectedRecommendation; }

    public List<Recommendation> getAllRecommendations() { return allRecommendations; }
    public void setAllRecommendations(List<Recommendation> allRecommendations) { this.allRecommendations = allRecommendations; }

    public String getDecisionMode() { return decisionMode; }
    public void setDecisionMode(String decisionMode) { this.decisionMode = decisionMode; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public TeamSummary getTeamSummary() { return teamSummary; }
    public void setTeamSummary(TeamSummary teamSummary) { this.teamSummary = teamSummary; }

    /**
     * Individual recommendation for one team member.
     */
    public static class Recommendation {
        private Long userId;
        private String username;
        private String email;
        
        private Double recommendationScore;
        private Double performanceComponent;
        private Double availabilityComponent;
        private Double expertiseComponent;
        private Double penaltyBonusComponent;
        
        private String reasoning;
        private List<String> strengths;
        private List<String> concerns;
        
        private Integer rankPosition;
        private Boolean isSelected;
        
        private MemberSnapshot memberSnapshot;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public Double getRecommendationScore() { return recommendationScore; }
        public void setRecommendationScore(Double recommendationScore) { this.recommendationScore = recommendationScore; }

        public Double getPerformanceComponent() { return performanceComponent; }
        public void setPerformanceComponent(Double performanceComponent) { this.performanceComponent = performanceComponent; }

        public Double getAvailabilityComponent() { return availabilityComponent; }
        public void setAvailabilityComponent(Double availabilityComponent) { this.availabilityComponent = availabilityComponent; }

        public Double getExpertiseComponent() { return expertiseComponent; }
        public void setExpertiseComponent(Double expertiseComponent) { this.expertiseComponent = expertiseComponent; }

        public Double getPenaltyBonusComponent() { return penaltyBonusComponent; }
        public void setPenaltyBonusComponent(Double penaltyBonusComponent) { this.penaltyBonusComponent = penaltyBonusComponent; }

        public String getReasoning() { return reasoning; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }

        public List<String> getStrengths() { return strengths; }
        public void setStrengths(List<String> strengths) { this.strengths = strengths; }

        public List<String> getConcerns() { return concerns; }
        public void setConcerns(List<String> concerns) { this.concerns = concerns; }

        public Integer getRankPosition() { return rankPosition; }
        public void setRankPosition(Integer rankPosition) { this.rankPosition = rankPosition; }

        public Boolean getIsSelected() { return isSelected; }
        public void setIsSelected(Boolean isSelected) { this.isSelected = isSelected; }

        public MemberSnapshot getMemberSnapshot() { return memberSnapshot; }
        public void setMemberSnapshot(MemberSnapshot memberSnapshot) { this.memberSnapshot = memberSnapshot; }
    }

    /**
     * Snapshot of member's current state at time of recommendation.
     */
    public static class MemberSnapshot {
        private Integer activeTasks;
        private Integer activeStoryPoints;
        private Double capacityUtilization;
        private Integer historicalVelocity;
        private Double productivityScore;
        private Double codeQualityScore;
        private Double onTimeDeliveryRate;
        private Integer totalCommits;
        private String primaryExpertise;
        private Double penaltyPoints;
        private Double bonusPoints;

        public Integer getActiveTasks() { return activeTasks; }
        public void setActiveTasks(Integer activeTasks) { this.activeTasks = activeTasks; }

        public Integer getActiveStoryPoints() { return activeStoryPoints; }
        public void setActiveStoryPoints(Integer activeStoryPoints) { this.activeStoryPoints = activeStoryPoints; }

        public Double getCapacityUtilization() { return capacityUtilization; }
        public void setCapacityUtilization(Double capacityUtilization) { this.capacityUtilization = capacityUtilization; }

        public Integer getHistoricalVelocity() { return historicalVelocity; }
        public void setHistoricalVelocity(Integer historicalVelocity) { this.historicalVelocity = historicalVelocity; }

        public Double getProductivityScore() { return productivityScore; }
        public void setProductivityScore(Double productivityScore) { this.productivityScore = productivityScore; }

        public Double getCodeQualityScore() { return codeQualityScore; }
        public void setCodeQualityScore(Double codeQualityScore) { this.codeQualityScore = codeQualityScore; }

        public Double getOnTimeDeliveryRate() { return onTimeDeliveryRate; }
        public void setOnTimeDeliveryRate(Double onTimeDeliveryRate) { this.onTimeDeliveryRate = onTimeDeliveryRate; }

        public Integer getTotalCommits() { return totalCommits; }
        public void setTotalCommits(Integer totalCommits) { this.totalCommits = totalCommits; }

        public String getPrimaryExpertise() { return primaryExpertise; }
        public void setPrimaryExpertise(String primaryExpertise) { this.primaryExpertise = primaryExpertise; }

        public Double getPenaltyPoints() { return penaltyPoints; }
        public void setPenaltyPoints(Double penaltyPoints) { this.penaltyPoints = penaltyPoints; }

        public Double getBonusPoints() { return bonusPoints; }
        public void setBonusPoints(Double bonusPoints) { this.bonusPoints = bonusPoints; }
    }

    /**
     * Summary statistics about the team's overall health.
     */
    public static class TeamSummary {
        private Integer totalMembers;
        private Integer availableMembers;
        private Double averageCapacityUtilization;
        private Double averageProductivityScore;
        private Double teamNetScore;
        private String teamHealth;  // HEALTHY, WARNING, CRITICAL

        public Integer getTotalMembers() { return totalMembers; }
        public void setTotalMembers(Integer totalMembers) { this.totalMembers = totalMembers; }

        public Integer getAvailableMembers() { return availableMembers; }
        public void setAvailableMembers(Integer availableMembers) { this.availableMembers = availableMembers; }

        public Double getAverageCapacityUtilization() { return averageCapacityUtilization; }
        public void setAverageCapacityUtilization(Double averageCapacityUtilization) { this.averageCapacityUtilization = averageCapacityUtilization; }

        public Double getAverageProductivityScore() { return averageProductivityScore; }
        public void setAverageProductivityScore(Double averageProductivityScore) { this.averageProductivityScore = averageProductivityScore; }

        public Double getTeamNetScore() { return teamNetScore; }
        public void setTeamNetScore(Double teamNetScore) { this.teamNetScore = teamNetScore; }

        public String getTeamHealth() { return teamHealth; }
        public void setTeamHealth(String teamHealth) { this.teamHealth = teamHealth; }
    }
}
