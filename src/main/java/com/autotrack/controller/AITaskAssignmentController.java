package com.autotrack.controller;

import com.autotrack.dto.AITaskAssignmentRequest;
import com.autotrack.dto.AITaskAssignmentResponse;
import com.autotrack.model.ContributionScore;
import com.autotrack.model.Project;
import com.autotrack.model.User;
import com.autotrack.repository.ProjectRepository;
import com.autotrack.repository.UserRepository;
import com.autotrack.service.AITaskAssignmentService;
import com.autotrack.service.ContributionScoringService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API for AI-powered task assignment.
 * 
 * Endpoints:
 * - POST /api/ai/assign/recommend: Get AI recommendation for task assignee
 * - GET /api/ai/scores/{projectId}: Get contribution scores for all team members
 * - GET /api/ai/scores/{projectId}/history/{userId}: Get score history for a user
 * - GET /api/ai/team-health/{projectId}: Get team health summary
 */
@RestController
@RequestMapping("/api/ai")
public class AITaskAssignmentController {

    private static final Logger logger = LoggerFactory.getLogger(AITaskAssignmentController.class);

    private final AITaskAssignmentService assignmentService;
    private final ContributionScoringService scoringService;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public AITaskAssignmentController(AITaskAssignmentService assignmentService,
                                      ContributionScoringService scoringService,
                                      ProjectRepository projectRepository,
                                      UserRepository userRepository) {
        this.assignmentService = assignmentService;
        this.scoringService = scoringService;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    /**
     * Get AI recommendation for task assignee.
     * 
     * Analyzes all team members and returns a ranked list with full explainability.
     * Can operate in three modes:
     * - RECOMMEND: Return recommendations without assigning
     * - ASSIGN: Automatically assign the task to the top candidate
     * - SUGGEST: Return recommendations with additional context
     */
    @PostMapping("/assign/recommend")
    public ResponseEntity<?> recommendAssignee(@Valid @RequestBody AITaskAssignmentRequest request) {
        try {
            logger.info("AI assignment recommendation requested for task {} on project {}", 
                    request.getTaskId(), request.getProjectId());

            AITaskAssignmentResponse response = assignmentService.recommendAssignee(request);
            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            logger.error("Error generating AI recommendation: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            logger.error("Unexpected error in AI assignment: {}", e.getMessage(), e);
            Map<String, String> error = new HashMap<>();
            error.put("error", "Internal server error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(error);
        }
    }

    /**
     * Get contribution scores for all team members on a project.
     * Returns the latest snapshot for each member with full breakdown.
     */
    @GetMapping("/scores/{projectId}")
    public ResponseEntity<?> getProjectScores(@PathVariable Long projectId) {
        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            List<ContributionScore> scores = scoringService.getLatestScores(project);

            List<Map<String, Object>> response = scores.stream()
                    .map(this::formatScoreResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            logger.error("Error fetching project scores: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get score history for a specific user on a project.
     * Useful for tracking performance trends over time.
     */
    @GetMapping("/scores/{projectId}/history/{userId}")
    public ResponseEntity<?> getScoreHistory(@PathVariable Long projectId, @PathVariable Long userId) {
        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));

            List<ContributionScore> history = scoringService.getScoreHistory(project, user);

            List<Map<String, Object>> response = history.stream()
                    .map(this::formatScoreResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            logger.error("Error fetching score history: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Get team health summary for a project.
     * Includes aggregate metrics and health assessment.
     */
    @GetMapping("/team-health/{projectId}")
    public ResponseEntity<?> getTeamHealth(@PathVariable Long projectId) {
        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            List<ContributionScore> scores = scoringService.getLatestScores(project);

            Map<String, Object> health = new HashMap<>();
            health.put("projectId", project.getId());
            health.put("projectName", project.getName());
            health.put("teamSize", scores.size());

            // Aggregate metrics
            double avgNetScore = scores.stream()
                    .mapToDouble(ContributionScore::getNetScore)
                    .average()
                    .orElse(0.0);
            health.put("averageNetScore", avgNetScore);

            double avgProductivity = scores.stream()
                    .mapToDouble(ContributionScore::getProductivityScore)
                    .average()
                    .orElse(0.0);
            health.put("averageProductivity", avgProductivity);

            double avgQuality = scores.stream()
                    .mapToDouble(ContributionScore::getCodeQualityScore)
                    .average()
                    .orElse(0.0);
            health.put("averageCodeQuality", avgQuality);

            double avgCapacity = scores.stream()
                    .mapToDouble(ContributionScore::getCapacityUtilization)
                    .average()
                    .orElse(0.0);
            health.put("averageCapacityUtilization", avgCapacity);

            long availableMembers = scores.stream()
                    .filter(s -> s.getAvailabilityScore() >= 50)
                    .count();
            health.put("availableMembers", availableMembers);

            long atRiskMembers = scores.stream()
                    .filter(s -> s.getDaysSinceLastCommit() > 7 || s.getTotalPenalty() > 20)
                    .count();
            health.put("atRiskMembers", atRiskMembers);

            // Health assessment
            String healthStatus;
            if (avgNetScore >= 70 && avgCapacity < 0.7 && atRiskMembers == 0) {
                healthStatus = "HEALTHY";
            } else if (avgNetScore >= 50 && avgCapacity < 0.85 && atRiskMembers <= 1) {
                healthStatus = "WARNING";
            } else {
                healthStatus = "CRITICAL";
            }
            health.put("healthStatus", healthStatus);

            // Top performers
            List<Map<String, Object>> topPerformers = scores.stream()
                    .sorted((a, b) -> Double.compare(b.getNetScore(), a.getNetScore()))
                    .limit(3)
                    .map(s -> {
                        Map<String, Object> performer = new HashMap<>();
                        performer.put("userId", s.getUser().getId());
                        performer.put("username", s.getUser().getNickname());
                        performer.put("netScore", s.getNetScore());
                        performer.put("primaryExpertise", s.getPrimaryExpertise());
                        return performer;
                    })
                    .collect(Collectors.toList());
            health.put("topPerformers", topPerformers);

            return ResponseEntity.ok(health);

        } catch (RuntimeException e) {
            logger.error("Error fetching team health: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Force recompute scores for all team members on a project.
     * Useful after significant changes to task/commit data.
     */
    @PostMapping("/scores/{projectId}/recompute")
    public ResponseEntity<?> recomputeScores(@PathVariable Long projectId) {
        try {
            Project project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new RuntimeException("Project not found: " + projectId));

            logger.info("Recomputing scores for project: {}", project.getName());
            List<ContributionScore> scores = scoringService.scoreTeamMembers(project);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Scores recomputed successfully");
            response.put("membersScored", scores.size());
            response.put("projectId", project.getId());
            response.put("projectName", project.getName());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            logger.error("Error recomputing scores: {}", e.getMessage());
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Format a ContributionScore entity into a response map.
     */
    private Map<String, Object> formatScoreResponse(ContributionScore score) {
        Map<String, Object> response = new HashMap<>();
        
        response.put("userId", score.getUser().getId());
        response.put("username", score.getUser().getNickname());
        response.put("email", score.getUser().getEmail());
        response.put("scoredAt", score.getScoredAt());

        // Core scores
        response.put("productivityScore", score.getProductivityScore());
        response.put("codeQualityScore", score.getCodeQualityScore());
        response.put("onTimeDeliveryScore", score.getOnTimeDeliveryScore());
        response.put("commitVelocityScore", score.getCommitVelocityScore());
        response.put("expertiseMatchScore", score.getExpertiseMatchScore());

        // Penalty/Bonus breakdown
        response.put("overduePenalty", score.getOverduePenalty());
        response.put("declinePenalty", score.getDeclinePenalty());
        response.put("inactivityPenalty", score.getInactivityPenalty());
        response.put("totalPenalty", score.getTotalPenalty());
        response.put("earlyDeliveryBonus", score.getEarlyDeliveryBonus());
        response.put("qualityBonus", score.getQualityBonus());
        response.put("collaborationBonus", score.getCollaborationBonus());
        response.put("totalBonus", score.getTotalBonus());

        // Aggregated scores
        response.put("overallPerformanceScore", score.getOverallPerformanceScore());
        response.put("availabilityScore", score.getAvailabilityScore());
        response.put("netScore", score.getNetScore());

        // Workload snapshot
        response.put("activeTaskCount", score.getActiveTaskCount());
        response.put("activeStoryPoints", score.getActiveStoryPoints());
        response.put("historicalVelocity", score.getHistoricalVelocity());
        response.put("capacityUtilization", score.getCapacityUtilization());

        // Raw metrics
        response.put("totalCommits", score.getTotalCommits());
        response.put("approvalRate", score.getApprovalRate());
        response.put("onTimeRate", score.getOnTimeRate());
        response.put("earlyDeliveries", score.getEarlyDeliveries());
        response.put("lateDeliveries", score.getLateDeliveries());
        response.put("declinedTasks", score.getDeclinedTasks());
        response.put("daysSinceLastCommit", score.getDaysSinceLastCommit());
        response.put("primaryExpertise", score.getPrimaryExpertise());

        // Reasoning
        response.put("reasoning", score.getReasoning());

        return response;
    }
}
