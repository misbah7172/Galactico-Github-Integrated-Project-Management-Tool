package com.autotrack.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persistent snapshot of a contributor's AI-computed scoring at a point in time.
 * Stores the full penalty/bonus breakdown so the team lead can audit why a member
 * was recommended (or not) for a task.
 *
 * A new snapshot is created each time the AI evaluates a project's team. Old
 * snapshots are kept for historical trending (the controller exposes a trend endpoint).
 */
@Entity
@Table(name = "contribution_scores",
       indexes = {
           @Index(name = "idx_contrib_score_lookup",
                  columnList = "project_id, user_id, scored_at")
       })
public class ContributionScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ----- Core scores (all 0..100) -----
    @Column(name = "productivity_score")
    private Double productivityScore = 0.0;

    @Column(name = "code_quality_score")
    private Double codeQualityScore = 0.0;

    @Column(name = "on_time_delivery_score")
    private Double onTimeDeliveryScore = 0.0;

    @Column(name = "commit_velocity_score")
    private Double commitVelocityScore = 0.0;

    @Column(name = "expertise_match_score")
    private Double expertiseMatchScore = 0.0;

    // ----- Penalty / Bonus (can be negative / positive, points) -----
    @Column(name = "overdue_penalty")
    private Double overduePenalty = 0.0;

    @Column(name = "decline_penalty")
    private Double declinePenalty = 0.0;

    @Column(name = "inactivity_penalty")
    private Double inactivityPenalty = 0.0;

    @Column(name = "early_delivery_bonus")
    private Double earlyDeliveryBonus = 0.0;

    @Column(name = "quality_bonus")
    private Double qualityBonus = 0.0;

    @Column(name = "collaboration_bonus")
    private Double collaborationBonus = 0.0;

    @Column(name = "total_penalty")
    private Double totalPenalty = 0.0;

    @Column(name = "total_bonus")
    private Double totalBonus = 0.0;

    // ----- Aggregated scores -----
    @Column(name = "overall_performance_score")
    private Double overallPerformanceScore = 0.0;

    @Column(name = "availability_score")
    private Double availabilityScore = 0.0;

    @Column(name = "net_score")
    private Double netScore = 0.0;

    // ----- Workload snapshot -----
    @Column(name = "active_task_count")
    private Integer activeTaskCount = 0;

    @Column(name = "active_story_points")
    private Integer activeStoryPoints = 0;

    @Column(name = "historical_velocity")
    private Integer historicalVelocity = 0;

    @Column(name = "capacity_utilization")
    private Double capacityUtilization = 0.0;

    // ----- Raw metrics (for debugging) -----
    @Column(name = "total_commits")
    private Integer totalCommits = 0;

    @Column(name = "approval_rate")
    private Double approvalRate = 0.0;

    @Column(name = "on_time_rate")
    private Double onTimeRate = 0.0;

    @Column(name = "early_deliveries")
    private Integer earlyDeliveries = 0;

    @Column(name = "late_deliveries")
    private Integer lateDeliveries = 0;

    @Column(name = "declined_tasks")
    private Integer declinedTasks = 0;

    @Column(name = "days_since_last_commit")
    private Integer daysSinceLastCommit = 0;

    @Column(name = "primary_expertise")
    private String primaryExpertise;

    // ----- Configurable weights used when scoring -----
    @Column(name = "weights_used", columnDefinition = "TEXT")
    private String weightsUsed;  // JSON: {"productivity":0.3,"quality":0.25,...}

    @Column(name = "scored_at", nullable = false)
    private LocalDateTime scoredAt;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;  // Human-readable explanation

    public ContributionScore() {
        this.scoredAt = LocalDateTime.now();
    }

    public ContributionScore(Project project, User user) {
        this();
        this.project = project;
        this.user = user;
    }

    // ---------- Getters & Setters ----------
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Project getProject() { return project; }
    public void setProject(Project project) { this.project = project; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Double getProductivityScore() { return productivityScore; }
    public void setProductivityScore(Double v) { this.productivityScore = v; }

    public Double getCodeQualityScore() { return codeQualityScore; }
    public void setCodeQualityScore(Double v) { this.codeQualityScore = v; }

    public Double getOnTimeDeliveryScore() { return onTimeDeliveryScore; }
    public void setOnTimeDeliveryScore(Double v) { this.onTimeDeliveryScore = v; }

    public Double getCommitVelocityScore() { return commitVelocityScore; }
    public void setCommitVelocityScore(Double v) { this.commitVelocityScore = v; }

    public Double getExpertiseMatchScore() { return expertiseMatchScore; }
    public void setExpertiseMatchScore(Double v) { this.expertiseMatchScore = v; }

    public Double getOverduePenalty() { return overduePenalty; }
    public void setOverduePenalty(Double v) { this.overduePenalty = v; }

    public Double getDeclinePenalty() { return declinePenalty; }
    public void setDeclinePenalty(Double v) { this.declinePenalty = v; }

    public Double getInactivityPenalty() { return inactivityPenalty; }
    public void setInactivityPenalty(Double v) { this.inactivityPenalty = v; }

    public Double getEarlyDeliveryBonus() { return earlyDeliveryBonus; }
    public void setEarlyDeliveryBonus(Double v) { this.earlyDeliveryBonus = v; }

    public Double getQualityBonus() { return qualityBonus; }
    public void setQualityBonus(Double v) { this.qualityBonus = v; }

    public Double getCollaborationBonus() { return collaborationBonus; }
    public void setCollaborationBonus(Double v) { this.collaborationBonus = v; }

    public Double getTotalPenalty() { return totalPenalty; }
    public void setTotalPenalty(Double v) { this.totalPenalty = v; }

    public Double getTotalBonus() { return totalBonus; }
    public void setTotalBonus(Double v) { this.totalBonus = v; }

    public Double getOverallPerformanceScore() { return overallPerformanceScore; }
    public void setOverallPerformanceScore(Double v) { this.overallPerformanceScore = v; }

    public Double getAvailabilityScore() { return availabilityScore; }
    public void setAvailabilityScore(Double v) { this.availabilityScore = v; }

    public Double getNetScore() { return netScore; }
    public void setNetScore(Double v) { this.netScore = v; }

    public Integer getActiveTaskCount() { return activeTaskCount; }
    public void setActiveTaskCount(Integer v) { this.activeTaskCount = v; }

    public Integer getActiveStoryPoints() { return activeStoryPoints; }
    public void setActiveStoryPoints(Integer v) { this.activeStoryPoints = v; }

    public Integer getHistoricalVelocity() { return historicalVelocity; }
    public void setHistoricalVelocity(Integer v) { this.historicalVelocity = v; }

    public Double getCapacityUtilization() { return capacityUtilization; }
    public void setCapacityUtilization(Double v) { this.capacityUtilization = v; }

    public Integer getTotalCommits() { return totalCommits; }
    public void setTotalCommits(Integer v) { this.totalCommits = v; }

    public Double getApprovalRate() { return approvalRate; }
    public void setApprovalRate(Double v) { this.approvalRate = v; }

    public Double getOnTimeRate() { return onTimeRate; }
    public void setOnTimeRate(Double v) { this.onTimeRate = v; }

    public Integer getEarlyDeliveries() { return earlyDeliveries; }
    public void setEarlyDeliveries(Integer v) { this.earlyDeliveries = v; }

    public Integer getLateDeliveries() { return lateDeliveries; }
    public void setLateDeliveries(Integer v) { this.lateDeliveries = v; }

    public Integer getDeclinedTasks() { return declinedTasks; }
    public void setDeclinedTasks(Integer v) { this.declinedTasks = v; }

    public Integer getDaysSinceLastCommit() { return daysSinceLastCommit; }
    public void setDaysSinceLastCommit(Integer v) { this.daysSinceLastCommit = v; }

    public String getPrimaryExpertise() { return primaryExpertise; }
    public void setPrimaryExpertise(String v) { this.primaryExpertise = v; }

    public String getWeightsUsed() { return weightsUsed; }
    public void setWeightsUsed(String v) { this.weightsUsed = v; }

    public LocalDateTime getScoredAt() { return scoredAt; }
    public void setScoredAt(LocalDateTime v) { this.scoredAt = v; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String v) { this.reasoning = v; }
}
