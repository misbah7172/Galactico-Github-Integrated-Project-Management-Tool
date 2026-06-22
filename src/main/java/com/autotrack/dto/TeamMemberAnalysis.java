package com.autotrack.dto;

import com.autotrack.model.User;
import com.autotrack.model.Task;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO representing AI analysis of a team member's performance and capacity.
 */
public class TeamMemberAnalysis {
    
    private Long userId;
    private String username;
    private String email;
    
    // Current workload metrics
    private int activeTasks;
    private int completedTasks;
    private int overdueTasks;
    private double currentCapacityUtilization; // 0.0 to 1.0
    
    // Performance metrics
    private double productivityScore;
    private double codeQualityScore;
    private int totalCommits;
    private int totalLinesChanged;
    private double approvalRate;
    
    // Timeline adherence
    private double onTimeDeliveryRate; // 0.0 to 1.0
    private double averageDelayDays;
    private int earlyDeliveries;
    private int lateDeliveries;
    
    // Expertise areas (inferred from commit history)
    private List<String> topFileTypes;
    private List<String> expertiseTags;
    private String primaryExpertise;
    
    // AI-calculated scores
    private double overallPerformanceScore;
    private double availabilityScore;
    private double recommendationScore;
    
    // Penalty/Bonus tracking
    private double penaltyPoints;
    private double bonusPoints;
    private double netScore;
    
    // Recommendation
    private boolean recommendedForNewTasks;
    private int suggestedMaxStoryPoints;
    private List<String> strengths;
    private List<String> concerns;
    
    // Timestamp
    private LocalDateTime analysisTimestamp;
    
    public TeamMemberAnalysis() {
        this.analysisTimestamp = LocalDateTime.now();
    }
    
    public TeamMemberAnalysis(User user) {
        this();
        this.userId = user.getId();
        this.username = user.getNickname();
        this.email = user.getEmail();
    }
    
    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public int getActiveTasks() { return activeTasks; }
    public void setActiveTasks(int activeTasks) { this.activeTasks = activeTasks; }
    
    public int getCompletedTasks() { return completedTasks; }
    public void setCompletedTasks(int completedTasks) { this.completedTasks = completedTasks; }
    
    public int getOverdueTasks() { return overdueTasks; }
    public void setOverdueTasks(int overdueTasks) { this.overdueTasks = overdueTasks; }
    
    public double getCurrentCapacityUtilization() { return currentCapacityUtilization; }
    public void setCurrentCapacityUtilization(double currentCapacityUtilization) { 
        this.currentCapacityUtilization = currentCapacityUtilization; 
    }
    
    public double getProductivityScore() { return productivityScore; }
    public void setProductivityScore(double productivityScore) { this.productivityScore = productivityScore; }
    
    public double getCodeQualityScore() { return codeQualityScore; }
    public void setCodeQualityScore(double codeQualityScore) { this.codeQualityScore = codeQualityScore; }
    
    public int getTotalCommits() { return totalCommits; }
    public void setTotalCommits(int totalCommits) { this.totalCommits = totalCommits; }
    
    public int getTotalLinesChanged() { return totalLinesChanged; }
    public void setTotalLinesChanged(int totalLinesChanged) { this.totalLinesChanged = totalLinesChanged; }
    
    public double getApprovalRate() { return approvalRate; }
    public void setApprovalRate(double approvalRate) { this.approvalRate = approvalRate; }
    
    public double getOnTimeDeliveryRate() { return onTimeDeliveryRate; }
    public void setOnTimeDeliveryRate(double onTimeDeliveryRate) { this.onTimeDeliveryRate = onTimeDeliveryRate; }
    
    public double getAverageDelayDays() { return averageDelayDays; }
    public void setAverageDelayDays(double averageDelayDays) { this.averageDelayDays = averageDelayDays; }
    
    public int getEarlyDeliveries() { return earlyDeliveries; }
    public void setEarlyDeliveries(int earlyDeliveries) { this.earlyDeliveries = earlyDeliveries; }
    
    public int getLateDeliveries() { return lateDeliveries; }
    public void setLateDeliveries(int lateDeliveries) { this.lateDeliveries = lateDeliveries; }
    
    public List<String> getTopFileTypes() { return topFileTypes; }
    public void setTopFileTypes(List<String> topFileTypes) { this.topFileTypes = topFileTypes; }
    
    public List<String> getExpertiseTags() { return expertiseTags; }
    public void setExpertiseTags(List<String> expertiseTags) { this.expertiseTags = expertiseTags; }
    
    public String getPrimaryExpertise() { return primaryExpertise; }
    public void setPrimaryExpertise(String primaryExpertise) { this.primaryExpertise = primaryExpertise; }
    
    public double getOverallPerformanceScore() { return overallPerformanceScore; }
    public void setOverallPerformanceScore(double overallPerformanceScore) { 
        this.overallPerformanceScore = overallPerformanceScore; 
    }
    
    public double getAvailabilityScore() { return availabilityScore; }
    public void setAvailabilityScore(double availabilityScore) { this.availabilityScore = availabilityScore; }
    
    public double getRecommendationScore() { return recommendationScore; }
    public void setRecommendationScore(double recommendationScore) { this.recommendationScore = recommendationScore; }
    
    public double getPenaltyPoints() { return penaltyPoints; }
    public void setPenaltyPoints(double penaltyPoints) { this.penaltyPoints = penaltyPoints; }
    
    public double getBonusPoints() { return bonusPoints; }
    public void setBonusPoints(double bonusPoints) { this.bonusPoints = bonusPoints; }
    
    public double getNetScore() { return netScore; }
    public void setNetScore(double netScore) { this.netScore = netScore; }
    
    public boolean isRecommendedForNewTasks() { return recommendedForNewTasks; }
    public void setRecommendedForNewTasks(boolean recommendedForNewTasks) { 
        this.recommendedForNewTasks = recommendedForNewTasks; 
    }
    
    public int getSuggestedMaxStoryPoints() { return suggestedMaxStoryPoints; }
    public void setSuggestedMaxStoryPoints(int suggestedMaxStoryPoints) { 
        this.suggestedMaxStoryPoints = suggestedMaxStoryPoints; 
    }
    
    public List<String> getStrengths() { return strengths; }
    public void setStrengths(List<String> strengths) { this.strengths = strengths; }
    
    public List<String> getConcerns() { return concerns; }
    public void setConcerns(List<String> concerns) { this.concerns = concerns; }
    
    public LocalDateTime getAnalysisTimestamp() { return analysisTimestamp; }
    public void setAnalysisTimestamp(LocalDateTime analysisTimestamp) { 
        this.analysisTimestamp = analysisTimestamp; 
    }
}
