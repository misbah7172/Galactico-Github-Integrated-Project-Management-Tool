package com.autotrack.service;

import com.autotrack.dto.*;
import com.autotrack.model.*;
import com.autotrack.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI-powered task assignment service.
 * 
 * Analyzes all team members' contributions, workload, expertise, and timeline adherence
 * to intelligently recommend or assign tasks. Uses a weighted scoring algorithm that
 * considers:
 * 
 * 1. Performance (40%): Historical productivity, code quality, on-time delivery
 * 2. Availability (30%): Current workload, capacity utilization
 * 3. Expertise (20%): Match between task requirements and member's file-type history
 * 4. Penalty/Bonus (10%): Recent behavior (early/late deliveries, declines, inactivity)
 * 
 * The service produces a ranked list of candidates with full explainability, allowing
 * team leads to understand why each member was recommended and make informed decisions.
 */
@Service
public class AITaskAssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(AITaskAssignmentService.class);

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ContributionScoringService scoringService;
    private final ContributionScoreRepository contributionScoreRepository;
    private final AIAssignmentDecisionRepository decisionRepository;
    private final TaskService taskService;
    private final NotificationService notificationService;
    private final SlackService slackService;

    public AITaskAssignmentService(TaskRepository taskRepository,
                                   ProjectRepository projectRepository,
                                   UserRepository userRepository,
                                   ContributionScoringService scoringService,
                                   ContributionScoreRepository contributionScoreRepository,
                                   AIAssignmentDecisionRepository decisionRepository,
                                   TaskService taskService,
                                   NotificationService notificationService,
                                   SlackService slackService) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.scoringService = scoringService;
        this.contributionScoreRepository = contributionScoreRepository;
        this.decisionRepository = decisionRepository;
        this.taskService = taskService;
        this.notificationService = notificationService;
        this.slackService = slackService;
    }

    /**
     * Analyze the project team and recommend the best assignee for a task.
     */
    @Transactional
    public AITaskAssignmentResponse recommendAssignee(AITaskAssignmentRequest request) {
        logger.info("AI assignment recommendation requested for task {} on project {}", 
                request.getTaskId(), request.getProjectId());

        // 1. Fetch task and project
        Task task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new RuntimeException("Task not found: " + request.getTaskId()));
        
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new RuntimeException("Project not found: " + request.getProjectId()));

        if (!task.getProject().getId().equals(project.getId())) {
            throw new RuntimeException("Task does not belong to the specified project");
        }

        // 2. Compute fresh scores for all team members
        List<ContributionScore> scores = scoringService.scoreTeamMembers(project);

        if (scores.isEmpty()) {
            throw new RuntimeException("No team members found for project: " + project.getName());
        }

        // 3. Generate request ID for audit trail
        String requestId = UUID.randomUUID().toString();

        // 4. Analyze task requirements and match against member expertise
        List<CandidateAnalysis> candidates = analyzeCandidates(task, project, scores, request);

        // 5. Rank candidates by recommendation score
        candidates.sort((a, b) -> Double.compare(b.recommendationScore, a.recommendationScore));

        // 6. Filter by capacity threshold
        double minCapacity = request.getMinCapacityThreshold() != null ? request.getMinCapacityThreshold() : 0.2;
        candidates = candidates.stream()
                .filter(c -> c.availabilityComponent >= minCapacity * 100)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            logger.warn("No candidates meet the minimum capacity threshold of {}%", minCapacity * 100);
            throw new RuntimeException("No team members have sufficient capacity for this task");
        }

        // 7. Build response with full ranking
        AITaskAssignmentResponse response = new AITaskAssignmentResponse();
        response.setRequestId(requestId);
        response.setTaskId(task.getId());
        response.setTaskTitle(task.getTitle());
        response.setDecisionMode(request.getDecisionMode());

        List<AITaskAssignmentResponse.Recommendation> recommendations = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            CandidateAnalysis candidate = candidates.get(i);
            AITaskAssignmentResponse.Recommendation rec = buildRecommendation(candidate, i + 1, i == 0);
            recommendations.add(rec);

            // 8. Persist decision for audit trail
            AIAssignmentDecision decision = buildDecision(requestId, task, candidate, i + 1, i == 0, request.getDecisionMode());
            decisionRepository.save(decision);
        }

        response.setAllRecommendations(recommendations);
        response.setSelectedRecommendation(recommendations.get(0));

        // 9. Build team summary
        response.setTeamSummary(buildTeamSummary(scores, candidates));

        // 10. If mode is ASSIGN, actually assign the task
        if ("ASSIGN".equalsIgnoreCase(request.getDecisionMode())) {
            assignTask(task, candidates.get(0).user);
        }

        logger.info("AI recommendation completed. Top candidate: {} (score: {})", 
                candidates.get(0).user.getNickname(), candidates.get(0).recommendationScore);

        return response;
    }

    /**
     * Analyze all candidates for a task.
     */
    private List<CandidateAnalysis> analyzeCandidates(Task task, Project project, 
                                                       List<ContributionScore> scores,
                                                       AITaskAssignmentRequest request) {
        List<CandidateAnalysis> candidates = new ArrayList<>();

        for (ContributionScore score : scores) {
            CandidateAnalysis analysis = new CandidateAnalysis();
            analysis.user = score.getUser();
            analysis.score = score;

            // Component 1: Performance (40% weight)
            double performanceScore = request.getConsiderPerformance() != false ? score.getOverallPerformanceScore() : 50.0;
            analysis.performanceComponent = performanceScore;

            // Component 2: Availability (30% weight)
            double availabilityScore = request.getConsiderWorkload() != false ? score.getAvailabilityScore() : 50.0;
            analysis.availabilityComponent = availabilityScore;

            // Component 3: Expertise (20% weight)
            double expertiseScore = request.getConsiderExpertise() != false ? 
                    computeExpertiseMatch(task, score) : 50.0;
            analysis.expertiseComponent = expertiseScore;
            score.setExpertiseMatchScore(expertiseScore);

            // Component 4: Penalty/Bonus (10% weight, normalized)
            double penaltyBonusScore = computePenaltyBonusScore(score);
            analysis.penaltyBonusComponent = penaltyBonusScore;

            // Weighted recommendation score
            analysis.recommendationScore = (
                    performanceScore * 0.40 +
                    availabilityScore * 0.30 +
                    expertiseScore * 0.20 +
                    penaltyBonusScore * 0.10
            );

            // Build strengths and concerns
            analysis.strengths = identifyStrengths(score);
            analysis.concerns = identifyConcerns(score);

            // Build reasoning
            analysis.reasoning = buildCandidateReasoning(score, analysis, task);

            candidates.add(analysis);
        }

        return candidates;
    }

    /**
     * Compute expertise match score based on task requirements and member's history.
     */
    private double computeExpertiseMatch(Task task, ContributionScore score) {
        if (task.getTags() == null || task.getTags().isEmpty()) {
            return 50.0;  // Neutral score if no tags
        }

        List<String> taskTags = task.getTags();
        String expertise = score.getPrimaryExpertise();

        // Simple keyword matching
        for (String tag : taskTags) {
            String tagLower = tag.toLowerCase();
            if (expertise != null && expertise.toLowerCase().contains(tagLower)) {
                return 80.0;  // Strong match
            }
            if (tagLower.contains(expertise != null ? expertise.toLowerCase() : "")) {
                return 75.0;  // Good match
            }
        }

        return 40.0;  // Weak match
    }

    /**
     * Compute penalty/bonus component score (normalized to 0-100).
     */
    private double computePenaltyBonusScore(ContributionScore score) {
        double base = 50.0;  // Neutral starting point
        double adjusted = base + score.getTotalBonus() - score.getTotalPenalty();
        return Math.max(0, Math.min(100, adjusted));
    }

    /**
     * Identify strengths based on score metrics.
     */
    private List<String> identifyStrengths(ContributionScore score) {
        List<String> strengths = new ArrayList<>();

        if (score.getOnTimeRate() > 85) {
            strengths.add("Excellent on-time delivery (" + String.format("%.1f%%", score.getOnTimeRate()) + ")");
        }
        if (score.getApprovalRate() > 85) {
            strengths.add("High code quality (" + String.format("%.1f%%", score.getApprovalRate()) + " approval)");
        }
        if (score.getProductivityScore() > 75) {
            strengths.add("High productivity score (" + String.format("%.1f", score.getProductivityScore()) + ")");
        }
        if (score.getEarlyDeliveries() > 2) {
            strengths.add("Frequently delivers early (" + score.getEarlyDeliveries() + " times)");
        }
        if (score.getAvailabilityScore() > 70) {
            strengths.add("Good availability (" + String.format("%.1f%%", score.getAvailabilityScore()) + " capacity)");
        }
        if (score.getCommitVelocityScore() > 60) {
            strengths.add("Active contributor (" + String.format("%.0f", score.getCommitVelocityScore()) + " velocity score)");
        }

        return strengths;
    }

    /**
     * Identify concerns based on score metrics.
     */
    private List<String> identifyConcerns(ContributionScore score) {
        List<String> concerns = new ArrayList<>();

        if (score.getLateDeliveries() > 2) {
            concerns.add("History of late deliveries (" + score.getLateDeliveries() + " times)");
        }
        if (score.getDeclinedTasks() > 0) {
            concerns.add("Had tasks declined (" + score.getDeclinedTasks() + " times)");
        }
        if (score.getDaysSinceLastCommit() > 7) {
            concerns.add("Inactive for " + score.getDaysSinceLastCommit() + " days");
        }
        if (score.getCapacityUtilization() > 0.8) {
            concerns.add("High workload (" + String.format("%.1f%%", score.getCapacityUtilization() * 100) + " capacity)");
        }
        if (score.getOnTimeRate() < 60) {
            concerns.add("Low on-time delivery rate (" + String.format("%.1f%%", score.getOnTimeRate()) + ")");
        }
        if (score.getTotalPenalty() > 20) {
            concerns.add("High penalty score (-" + String.format("%.1f", score.getTotalPenalty()) + ")");
        }

        return concerns;
    }

    /**
     * Build human-readable reasoning for a candidate.
     */
    private String buildCandidateReasoning(ContributionScore score, CandidateAnalysis analysis, Task task) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("Recommendation score: %.1f/100. ", analysis.recommendationScore));
        sb.append(String.format("Performance: %.1f (%.0f%%), Availability: %.1f (%.0f%%), ",
                analysis.performanceComponent, 40.0,
                analysis.availabilityComponent, 30.0));
        sb.append(String.format("Expertise: %.1f (%.0f%%), Penalty/Bonus: %.1f (%.0f%%). ",
                analysis.expertiseComponent, 20.0,
                analysis.penaltyBonusComponent, 10.0));

        if (!analysis.strengths.isEmpty()) {
            sb.append("Strengths: ").append(String.join("; ", analysis.strengths)).append(". ");
        }
        if (!analysis.concerns.isEmpty()) {
            sb.append("Concerns: ").append(String.join("; ", analysis.concerns)).append(". ");
        }

        sb.append(score.getReasoning());

        return sb.toString();
    }

    /**
     * Build recommendation DTO from candidate analysis.
     */
    private AITaskAssignmentResponse.Recommendation buildRecommendation(CandidateAnalysis candidate, int rank, boolean isSelected) {
        AITaskAssignmentResponse.Recommendation rec = new AITaskAssignmentResponse.Recommendation();
        rec.setUserId(candidate.user.getId());
        rec.setUsername(candidate.user.getNickname());
        rec.setEmail(candidate.user.getEmail());
        rec.setRecommendationScore(candidate.recommendationScore);
        rec.setPerformanceComponent(candidate.performanceComponent);
        rec.setAvailabilityComponent(candidate.availabilityComponent);
        rec.setExpertiseComponent(candidate.expertiseComponent);
        rec.setPenaltyBonusComponent(candidate.penaltyBonusComponent);
        rec.setReasoning(candidate.reasoning);
        rec.setStrengths(candidate.strengths);
        rec.setConcerns(candidate.concerns);
        rec.setRankPosition(rank);
        rec.setIsSelected(isSelected);

        // Build member snapshot
        AITaskAssignmentResponse.MemberSnapshot snapshot = new AITaskAssignmentResponse.MemberSnapshot();
        snapshot.setActiveTasks(candidate.score.getActiveTaskCount());
        snapshot.setActiveStoryPoints(candidate.score.getActiveStoryPoints());
        snapshot.setCapacityUtilization(candidate.score.getCapacityUtilization());
        snapshot.setHistoricalVelocity(candidate.score.getHistoricalVelocity());
        snapshot.setProductivityScore(candidate.score.getProductivityScore());
        snapshot.setCodeQualityScore(candidate.score.getCodeQualityScore());
        snapshot.setOnTimeDeliveryRate(candidate.score.getOnTimeRate());
        snapshot.setTotalCommits(candidate.score.getTotalCommits());
        snapshot.setPrimaryExpertise(candidate.score.getPrimaryExpertise());
        snapshot.setPenaltyPoints(candidate.score.getTotalPenalty());
        snapshot.setBonusPoints(candidate.score.getTotalBonus());
        rec.setMemberSnapshot(snapshot);

        return rec;
    }

    /**
     * Build audit decision record.
     */
    private AIAssignmentDecision buildDecision(String requestId, Task task, CandidateAnalysis candidate, 
                                                int rank, boolean isSelected, String mode) {
        AIAssignmentDecision decision = new AIAssignmentDecision();
        decision.setRequestId(requestId);
        decision.setTask(task);
        decision.setCandidateUser(candidate.user);
        decision.setRankPosition(rank);
        decision.setWasSelected(isSelected);
        decision.setRecommendationScore(candidate.recommendationScore);
        decision.setPerformanceComponent(candidate.performanceComponent);
        decision.setAvailabilityComponent(candidate.availabilityComponent);
        decision.setExpertiseComponent(candidate.expertiseComponent);
        decision.setPenaltyBonusComponent(candidate.penaltyBonusComponent);
        decision.setReasoning(candidate.reasoning);
        decision.setStrengths(toJsonArray(candidate.strengths));
        decision.setConcerns(toJsonArray(candidate.concerns));
        decision.setDecisionMode(mode);
        return decision;
    }

    /**
     * Build team summary statistics.
     */
    private AITaskAssignmentResponse.TeamSummary buildTeamSummary(List<ContributionScore> scores, 
                                                                    List<CandidateAnalysis> candidates) {
        AITaskAssignmentResponse.TeamSummary summary = new AITaskAssignmentResponse.TeamSummary();
        summary.setTotalMembers(scores.size());

        long availableMembers = candidates.stream()
                .filter(c -> c.availabilityComponent >= 50)
                .count();
        summary.setAvailableMembers((int) availableMembers);

        double avgCapacity = scores.stream()
                .mapToDouble(ContributionScore::getCapacityUtilization)
                .average()
                .orElse(0.0);
        summary.setAverageCapacityUtilization(avgCapacity);

        double avgProductivity = scores.stream()
                .mapToDouble(ContributionScore::getProductivityScore)
                .average()
                .orElse(0.0);
        summary.setAverageProductivityScore(avgProductivity);

        double teamNetScore = scores.stream()
                .mapToDouble(ContributionScore::getNetScore)
                .average()
                .orElse(0.0);
        summary.setTeamNetScore(teamNetScore);

        // Determine team health
        String health;
        if (teamNetScore >= 70 && avgCapacity < 0.7) {
            health = "HEALTHY";
        } else if (teamNetScore >= 50 && avgCapacity < 0.85) {
            health = "WARNING";
        } else {
            health = "CRITICAL";
        }
        summary.setTeamHealth(health);

        return summary;
    }

    /**
     * Actually assign the task to the selected user.
     */
    private void assignTask(Task task, User assignee) {
        logger.info("Assigning task {} to user {}", task.getId(), assignee.getNickname());

        User oldAssignee = task.getAssignee();
        task.setAssignee(assignee);
        taskRepository.save(task);

        // Send notifications
        notificationService.notifyTaskAssigneeChanged(task, oldAssignee);

        if (assignee != null) {
            slackService.sendTaskAssigneeChangedNotification(
                    task.getTitle(),
                    task.getFeatureCode(),
                    oldAssignee != null ? oldAssignee.getNickname() : "Unassigned",
                    assignee.getNickname()
            );
        }
    }

    /**
     * Convert list to JSON array string.
     */
    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }
        return "[" + list.stream()
                .map(s -> "\"" + s.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",")) + "]";
    }

    /**
     * Internal holder for candidate analysis during processing.
     */
    private static class CandidateAnalysis {
        User user;
        ContributionScore score;
        double recommendationScore;
        double performanceComponent;
        double availabilityComponent;
        double expertiseComponent;
        double penaltyBonusComponent;
        List<String> strengths = new ArrayList<>();
        List<String> concerns = new ArrayList<>();
        String reasoning;
    }
}
