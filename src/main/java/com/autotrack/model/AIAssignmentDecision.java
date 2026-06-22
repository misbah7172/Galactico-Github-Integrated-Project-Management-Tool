package com.autotrack.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Audit record for an AI task assignment decision.
 *
 * Every time the AI is asked to recommend / assign a task, one row is written per
 * considered candidate so the team lead can see the full ranking and the reasoning
 * behind the chosen assignee.
 */
@Entity
@Table(name = "ai_assignment_decisions",
       indexes = {
           @Index(name = "idx_ai_decision_task", columnList = "task_id"),
           @Index(name = "idx_ai_decision_req", columnList = "request_id")
       })
public class AIAssignmentDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Correlates all decisions from a single assignment request. */
    @Column(name = "request_id", nullable = false)
    private String requestId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_user_id", nullable = false)
    private User candidateUser;

    @Column(name = "rank_position", nullable = false)
    private Integer rankPosition;

    @Column(name = "was_selected", nullable = false)
    private Boolean wasSelected;

    @Column(name = "recommendation_score")
    private Double recommendationScore = 0.0;

    @Column(name = "performance_component")
    private Double performanceComponent = 0.0;

    @Column(name = "availability_component")
    private Double availabilityComponent = 0.0;

    @Column(name = "expertise_component")
    private Double expertiseComponent = 0.0;

    @Column(name = "penalty_bonus_component")
    private Double penaltyBonusComponent = 0.0;

    @Column(name = "reasoning", columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "strengths", columnDefinition = "TEXT")
    private String strengths;  // JSON array

    @Column(name = "concerns", columnDefinition = "TEXT")
    private String concerns;   // JSON array

    @Column(name = "requested_by_id")
    private Long requestedById;

    @Column(name = "decision_mode", length = 32)
    private String decisionMode;  // RECOMMEND, ASSIGN, SUGGEST

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AIAssignmentDecision() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String v) { this.requestId = v; }

    public Task getTask() { return task; }
    public void setTask(Task v) { this.task = v; }

    public User getCandidateUser() { return candidateUser; }
    public void setCandidateUser(User v) { this.candidateUser = v; }

    public Integer getRankPosition() { return rankPosition; }
    public void setRankPosition(Integer v) { this.rankPosition = v; }

    public Boolean getWasSelected() { return wasSelected; }
    public void setWasSelected(Boolean v) { this.wasSelected = v; }

    public Double getRecommendationScore() { return recommendationScore; }
    public void setRecommendationScore(Double v) { this.recommendationScore = v; }

    public Double getPerformanceComponent() { return performanceComponent; }
    public void setPerformanceComponent(Double v) { this.performanceComponent = v; }

    public Double getAvailabilityComponent() { return availabilityComponent; }
    public void setAvailabilityComponent(Double v) { this.availabilityComponent = v; }

    public Double getExpertiseComponent() { return expertiseComponent; }
    public void setExpertiseComponent(Double v) { this.expertiseComponent = v; }

    public Double getPenaltyBonusComponent() { return penaltyBonusComponent; }
    public void setPenaltyBonusComponent(Double v) { this.penaltyBonusComponent = v; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String v) { this.reasoning = v; }

    public String getStrengths() { return strengths; }
    public void setStrengths(String v) { this.strengths = v; }

    public String getConcerns() { return concerns; }
    public void setConcerns(String v) { this.concerns = v; }

    public Long getRequestedById() { return requestedById; }
    public void setRequestedById(Long v) { this.requestedById = v; }

    public String getDecisionMode() { return decisionMode; }
    public void setDecisionMode(String v) { this.decisionMode = v; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime v) { this.createdAt = v; }
}
