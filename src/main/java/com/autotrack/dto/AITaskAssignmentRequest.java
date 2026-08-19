package com.autotrack.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for AI task assignment.
 */
public class AITaskAssignmentRequest {

    @NotNull(message = "Task ID is required")
    private Long taskId;

    @NotNull(message = "Project ID is required")
    private Long projectId;

    private String decisionMode = "RECOMMEND";  // RECOMMEND, ASSIGN, SUGGEST
    
    private Boolean considerExpertise = true;
    private Boolean considerWorkload = true;
    private Boolean considerPerformance = true;
    
    private Double minCapacityThreshold = 0.2;  // Only consider members with at least 20% capacity
    
    private Boolean useAiModel = true;

    public AITaskAssignmentRequest() {}

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public String getDecisionMode() { return decisionMode; }
    public void setDecisionMode(String decisionMode) { this.decisionMode = decisionMode; }

    public Boolean getConsiderExpertise() { return considerExpertise; }
    public void setConsiderExpertise(Boolean considerExpertise) { this.considerExpertise = considerExpertise; }

    public Boolean getConsiderWorkload() { return considerWorkload; }
    public void setConsiderWorkload(Boolean considerWorkload) { this.considerWorkload = considerWorkload; }

    public Boolean getConsiderPerformance() { return considerPerformance; }
    public void setConsiderPerformance(Boolean considerPerformance) { this.considerPerformance = considerPerformance; }

    public Double getMinCapacityThreshold() { return minCapacityThreshold; }
    public void setMinCapacityThreshold(Double minCapacityThreshold) { this.minCapacityThreshold = minCapacityThreshold; }

    public Boolean getUseAiModel() { return useAiModel; }
    public void setUseAiModel(Boolean useAiModel) { this.useAiModel = useAiModel; }
}
