package com.autotrack.service;

import com.autotrack.model.*;
import com.autotrack.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes contribution scores with penalty/bonus breakdown for each team member.
 * 
 * PENALTY FACTORS:
 * - Overdue tasks: -5 points per overdue day
 * - Declined tasks: -10 points per decline
 * - Inactivity: -3 points per day since last commit (max -30)
 * 
 * BONUS FACTORS:
 * - Early delivery: +2 points per day early
 * - High code quality: +10 points if approval rate > 90%
 * - Collaboration: +5 points if files changed overlap with other members
 * 
 * All scores are normalized to 0-100 scale before penalty/bonus application.
 */
@Service
public class ContributionScoringService {

    private static final Logger logger = LoggerFactory.getLogger(ContributionScoringService.class);

    private final ContributorStatsRepository contributorStatsRepository;
    private final TaskRepository taskRepository;
    private final CommitRepository commitRepository;
    private final ContributionScoreRepository contributionScoreRepository;

    public ContributionScoringService(ContributorStatsRepository contributorStatsRepository,
                                      TaskRepository taskRepository,
                                      CommitRepository commitRepository,
                                      ContributionScoreRepository contributionScoreRepository) {
        this.contributorStatsRepository = contributorStatsRepository;
        this.taskRepository = taskRepository;
        this.commitRepository = commitRepository;
        this.contributionScoreRepository = contributionScoreRepository;
    }

    /**
     * Compute and persist contribution scores for all team members on a project.
     */
    @Transactional
    public List<ContributionScore> scoreTeamMembers(Project project) {
        logger.info("Scoring team members for project: {}", project.getName());

        Set<User> teamMembers = project.getTeam() != null ? project.getTeam().getMembers() : Collections.emptySet();
        List<ContributionScore> scores = new ArrayList<>();

        for (User member : teamMembers) {
            ContributionScore score = computeScoreForMember(project, member);
            scores.add(score);
            contributionScoreRepository.save(score);
        }

        logger.info("Scored {} team members for project: {}", scores.size(), project.getName());
        return scores;
    }

    /**
     * Compute score for a single team member.
     */
    private ContributionScore computeScoreForMember(Project project, User user) {
        ContributionScore score = new ContributionScore(project, user);

        // 1. Fetch contributor stats
        Optional<ContributorStats> statsOpt = contributorStatsRepository.findByProjectAndContributorEmail(
                project, user.getEmail());
        
        if (statsOpt.isPresent()) {
            ContributorStats stats = statsOpt.get();
            score.setTotalCommits(stats.getTotalCommits());
            score.setApprovalRate(stats.getApprovalRate());
            score.setProductivityScore(stats.getProductivityScore());
            score.setCodeQualityScore(stats.getCodeQualityScore());
        }

        // 2. Compute workload metrics
        List<Task> userTasks = taskRepository.findByAssigneeOrderByUpdatedAtDesc(user);
        List<Task> projectTasks = userTasks.stream()
                .filter(t -> t.getProject().getId().equals(project.getId()))
                .collect(Collectors.toList());

        long activeTasks = projectTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS || t.getStatus() == TaskStatus.TODO)
                .count();
        
        int activeStoryPoints = projectTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                .mapToInt(t -> t.getStoryPoints() != null ? t.getStoryPoints() : 0)
                .sum();

        long completedTasks = projectTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE)
                .count();

        score.setActiveTaskCount((int) activeTasks);
        score.setActiveStoryPoints(activeStoryPoints);
        score.setHistoricalVelocity((int) completedTasks);

        // 3. Compute availability (inverse of capacity utilization)
        double capacityUtilization = activeStoryPoints > 0 ? 
                Math.min(1.0, activeStoryPoints / 20.0) : 0.0;  // Assume 20 SP is full capacity
        score.setCapacityUtilization(capacityUtilization);
        score.setAvailabilityScore(Math.max(0, 100 - (capacityUtilization * 100)));

        // 4. Compute on-time delivery
        List<Task> completedProjectTasks = projectTasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.DONE)
                .collect(Collectors.toList());

        int earlyDeliveries = 0;
        int lateDeliveries = 0;
        double totalDelayDays = 0;

        for (Task task : completedProjectTasks) {
            if (task.getSprint() != null && task.getUpdatedAt() != null) {
                LocalDateTime sprintEnd = task.getSprint().getEndDate();
                long daysDiff = Duration.between(sprintEnd, task.getUpdatedAt()).toDays();
                
                if (daysDiff < 0) {
                    earlyDeliveries++;
                    totalDelayDays += daysDiff;  // Negative = early
                } else if (daysDiff > 0) {
                    lateDeliveries++;
                    totalDelayDays += daysDiff;  // Positive = late
                }
            }
        }

        score.setEarlyDeliveries(earlyDeliveries);
        score.setLateDeliveries(lateDeliveries);

        double onTimeRate = completedProjectTasks.isEmpty() ? 100.0 : 
                ((earlyDeliveries + (completedProjectTasks.size() - earlyDeliveries - lateDeliveries)) 
                 * 100.0 / completedProjectTasks.size());
        score.setOnTimeRate(onTimeRate);
        score.setOnTimeDeliveryScore(Math.min(100, onTimeRate));

        // 5. Compute commit velocity (recent activity)
        List<Commit> recentCommits = commitRepository.findByProjectAndAuthorEmail(project, user.getEmail());
        int commitsLast30Days = (int) recentCommits.stream()
                .filter(c -> c.getCommittedAt() != null && 
                        c.getCommittedAt().isAfter(LocalDateTime.now().minusDays(30)))
                .count();
        
        double velocityScore = Math.min(100, commitsLast30Days * 5.0);  // 20 commits = 100 score
        score.setCommitVelocityScore(velocityScore);

        // 6. Check days since last commit (inactivity)
        Optional<LocalDateTime> lastCommitDate = recentCommits.stream()
                .map(Commit::getCommittedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo);

        int daysSinceLastCommit = lastCommitDate.map(date -> (int) Duration.between(date, LocalDateTime.now()).toDays())
                .orElse(999);
        score.setDaysSinceLastCommit(daysSinceLastCommit);

        // 7. Count declined tasks
        long declinedTasks = projectTasks.stream()
                .filter(t -> t.getDeclinedBy() != null)
                .count();
        score.setDeclinedTasks((int) declinedTasks);

        // 8. Compute penalties
        double overduePenalty = lateDeliveries * 5.0;  // -5 per late delivery
        double declinePenalty = declinedTasks * 10.0;  // -10 per decline
        double inactivityPenalty = Math.min(30, daysSinceLastCommit * 3.0);  // -3 per day, max -30

        score.setOverduePenalty(overduePenalty);
        score.setDeclinePenalty(declinePenalty);
        score.setInactivityPenalty(inactivityPenalty);
        score.setTotalPenalty(overduePenalty + declinePenalty + inactivityPenalty);

        // 9. Compute bonuses
        double earlyDeliveryBonus = earlyDeliveries * 2.0;  // +2 per early delivery
        double qualityBonus = score.getApprovalRate() > 90 ? 10.0 : 0.0;  // +10 for >90% approval
        double collaborationBonus = 0.0;  // TODO: Compute based on file overlap with other members

        score.setEarlyDeliveryBonus(earlyDeliveryBonus);
        score.setQualityBonus(qualityBonus);
        score.setCollaborationBonus(collaborationBonus);
        score.setTotalBonus(earlyDeliveryBonus + qualityBonus + collaborationBonus);

        // 10. Compute overall performance score (weighted average)
        double overallPerformance = (
                score.getProductivityScore() * 0.30 +
                score.getCodeQualityScore() * 0.25 +
                score.getOnTimeDeliveryScore() * 0.25 +
                score.getCommitVelocityScore() * 0.20
        );
        score.setOverallPerformanceScore(overallPerformance);

        // 11. Compute net score (performance + bonus - penalty)
        double netScore = overallPerformance + score.getTotalBonus() - score.getTotalPenalty();
        score.setNetScore(Math.max(0, Math.min(100, netScore)));

        // 12. Infer primary expertise from file types
        String primaryExpertise = inferExpertise(project, user);
        score.setPrimaryExpertise(primaryExpertise);
        score.setExpertiseMatchScore(50.0);  // Default, will be refined during assignment

        // 13. Build reasoning string
        String reasoning = buildReasoning(score, earlyDeliveries, lateDeliveries, declinedTasks, daysSinceLastCommit);
        score.setReasoning(reasoning);

        // 14. Store weights used
        String weightsJson = "{\"productivity\":0.30,\"quality\":0.25,\"onTime\":0.25,\"velocity\":0.20}";
        score.setWeightsUsed(weightsJson);

        logger.debug("Computed score for user {}: netScore={}, penalties={}, bonuses={}", 
                user.getNickname(), score.getNetScore(), score.getTotalPenalty(), score.getTotalBonus());

        return score;
    }

    /**
     * Infer primary expertise from file types the user most frequently modifies.
     */
    private String inferExpertise(Project project, User user) {
        List<Commit> commits = commitRepository.findByProjectAndAuthorEmail(project, user.getEmail());
        
        Map<String, Integer> fileTypeCounts = new HashMap<>();
        for (Commit commit : commits) {
            // TODO: Join with FileChangeMetrics to get actual file extensions
            // For now, use a simple heuristic based on commit message keywords
            String message = commit.getMessage().toLowerCase();
            if (message.contains("frontend") || message.contains("ui") || message.contains("css")) {
                fileTypeCounts.merge("frontend", 1, Integer::sum);
            } else if (message.contains("backend") || message.contains("api") || message.contains("service")) {
                fileTypeCounts.merge("backend", 1, Integer::sum);
            } else if (message.contains("test") || message.contains("spec")) {
                fileTypeCounts.merge("testing", 1, Integer::sum);
            } else if (message.contains("doc") || message.contains("readme")) {
                fileTypeCounts.merge("documentation", 1, Integer::sum);
            } else {
                fileTypeCounts.merge("general", 1, Integer::sum);
            }
        }

        return fileTypeCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("general");
    }

    /**
     * Build human-readable reasoning string.
     */
    private String buildReasoning(ContributionScore score, int early, int late, long declined, int inactiveDays) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(String.format("Overall performance: %.1f/100. ", score.getOverallPerformanceScore()));
        sb.append(String.format("Productivity: %.1f, Quality: %.1f, On-time: %.1f%%, Velocity: %.1f. ",
                score.getProductivityScore(), score.getCodeQualityScore(), 
                score.getOnTimeRate(), score.getCommitVelocityScore()));

        if (early > 0) {
            sb.append(String.format("Delivered %d tasks early (+%.1f bonus). ", early, score.getEarlyDeliveryBonus()));
        }
        if (late > 0) {
            sb.append(String.format("Delivered %d tasks late (-%.1f penalty). ", late, score.getOverduePenalty()));
        }
        if (declined > 0) {
            sb.append(String.format("Had %d tasks declined (-%.1f penalty). ", declined, score.getDeclinePenalty()));
        }
        if (inactiveDays > 7) {
            sb.append(String.format("Inactive for %d days (-%.1f penalty). ", inactiveDays, score.getInactivityPenalty()));
        }
        if (score.getApprovalRate() > 90) {
            sb.append(String.format("High code quality (%.1f%% approval, +%.1f bonus). ", 
                    score.getApprovalRate(), score.getQualityBonus()));
        }

        sb.append(String.format("Current workload: %d active tasks, %d story points (%.1f%% capacity). ",
                score.getActiveTaskCount(), score.getActiveStoryPoints(), score.getCapacityUtilization() * 100));

        sb.append(String.format("Net score: %.1f/100 (bonuses: +%.1f, penalties: -%.1f).",
                score.getNetScore(), score.getTotalBonus(), score.getTotalPenalty()));

        return sb.toString();
    }

    /**
     * Get latest scores for a project (for ranking display).
     */
    public List<ContributionScore> getLatestScores(Project project) {
        return contributionScoreRepository.findLatestScoresByProject(project);
    }

    /**
     * Get score history for a specific user on a project.
     */
    public List<ContributionScore> getScoreHistory(Project project, User user) {
        return contributionScoreRepository.findByProjectAndUserOrderByScoredAtDesc(project, user);
    }
}
