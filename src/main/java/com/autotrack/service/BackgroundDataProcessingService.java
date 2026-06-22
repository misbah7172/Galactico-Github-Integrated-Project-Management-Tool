package com.autotrack.service;

import com.autotrack.model.Commit;
import com.autotrack.model.Project;
import com.autotrack.model.FileChangeMetrics;
import com.autotrack.repository.CommitRepository;
import com.autotrack.repository.ProjectRepository;
import com.autotrack.repository.FileChangeMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Background service for processing webhook data and maintaining analytics in real-time.
 * This service handles asynchronous data processing to avoid blocking webhook responses.
 */
@Service
public class BackgroundDataProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(BackgroundDataProcessingService.class);

    @Autowired
    private CommitRepository commitRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private FileChangeMetricsRepository fileChangeMetricsRepository;

    @Autowired
    private CommitAnalyticsService commitAnalyticsService;

    @Autowired
    private NotificationService notificationService;

    /**
     * Process commit data asynchronously to avoid blocking webhook responses.
     */
    @Async
    @Transactional
    public CompletableFuture<Void> processCommitAsync(Commit commit) {
        try {
            logger.info("Starting async processing for commit: {}", commit.getSha());

            // Update contributor statistics
            commitAnalyticsService.updateContributorStats(commit);

            // Process file change metrics if they exist
            List<FileChangeMetrics> fileChanges = fileChangeMetricsRepository.findByCommitId(commit.getId());
            for (FileChangeMetrics fileChange : fileChanges) {
                processFileChangeMetrics(fileChange);
            }

            // Trigger notifications for significant commits
            triggerCommitNotifications(commit);

            logger.info("Completed async processing for commit: {}", commit.getSha());
        } catch (Exception e) {
            logger.error("Error processing commit async: {}", commit.getSha(), e);
        }

        return CompletableFuture.completedFuture(null);
    }

    /**
     * Process non-aggregate commit insights asynchronously after webhook contribution totals are updated.
     */
    @Async
    @Transactional
    public CompletableFuture<Void> processCommitInsightsAsync(Commit commit) {
        try {
            logger.info("Starting async insight processing for commit: {}", commit.getSha());

            List<FileChangeMetrics> fileChanges = fileChangeMetricsRepository.findByCommitId(commit.getId());
            for (FileChangeMetrics fileChange : fileChanges) {
                processFileChangeMetrics(fileChange);
            }

            triggerCommitNotifications(commit);
            logger.info("Completed async insight processing for commit: {}", commit.getSha());
        } catch (Exception e) {
            logger.error("Error processing commit insights async: {}", commit.getSha(), e);
        }

        return CompletableFuture.completedFuture(null);
    }
    /**
     * Process file change metrics to extract additional insights.
     */
    private void processFileChangeMetrics(FileChangeMetrics fileChange) {
        try {
            // Extract file type and update project statistics
            String fileExtension = extractFileExtension(fileChange.getFilePath());
            updateFileTypeStatistics(fileChange.getCommit().getProject(), fileExtension, 
                                   fileChange.getLinesAdded(), fileChange.getLinesDeleted());

            // Detect large file changes that might need review
            if (fileChange.getLinesAdded() + fileChange.getLinesDeleted() > 500) {
                logger.info("Large file change detected: {} in commit {}", 
                           fileChange.getFilePath(), fileChange.getCommit().getSha());
                // Could trigger special review process here
            }

            // Detect critical file changes (config files, main modules, etc.)
            if (isCriticalFile(fileChange.getFilePath())) {
                logger.info("Critical file change detected: {} in commit {}", 
                           fileChange.getFilePath(), fileChange.getCommit().getSha());
                // Could trigger additional notifications or reviews
            }

        } catch (Exception e) {
            logger.error("Error processing file change metrics: {}", fileChange.getFilePath(), e);
        }
    }

    /**
     * Update file type statistics for the project.
     */
    private void updateFileTypeStatistics(Project project, String fileExtension, 
                                        Integer linesAdded, Integer linesDeleted) {
        // This could update a separate FileTypeStats entity if you want to track this
        // For now, we'll just log it
        logger.debug("File type {} changed in project {}: +{} -{} lines", 
                    fileExtension, project.getName(), linesAdded, linesDeleted);
    }

    /**
     * Extract file extension from file path.
     */
    private String extractFileExtension(String filePath) {
        if (filePath == null || !filePath.contains(".")) {
            return "unknown";
        }
        
        int lastDot = filePath.lastIndexOf('.');
        if (lastDot == -1 || lastDot == filePath.length() - 1) {
            return "unknown";
        }
        
        return filePath.substring(lastDot + 1).toLowerCase();
    }

    /**
     * Check if a file is considered critical (might need special handling).
     */
    private boolean isCriticalFile(String filePath) {
        if (filePath == null) return false;
        
        String lowerPath = filePath.toLowerCase();
        
        // Configuration files
        if (lowerPath.contains("config") || lowerPath.contains("application.properties") || 
            lowerPath.contains("pom.xml") || lowerPath.contains("package.json") ||
            lowerPath.contains("dockerfile") || lowerPath.contains("docker-compose")) {
            return true;
        }
        
        // Database migration files
        if (lowerPath.contains("migration") || lowerPath.contains("schema")) {
            return true;
        }
        
        // Security files
        if (lowerPath.contains("security") || lowerPath.contains("auth") || 
            lowerPath.contains("oauth") || lowerPath.contains("jwt")) {
            return true;
        }
        
        return false;
    }

    /**
     * Trigger notifications for significant commits.
     */
    private void triggerCommitNotifications(Commit commit) {
        try {
            // Large commits might need attention
            int totalChanges = (commit.getLinesAdded() != null ? commit.getLinesAdded() : 0) + 
                             (commit.getLinesDeleted() != null ? commit.getLinesDeleted() : 0);
            
            if (totalChanges > 1000) {
                logger.info("Large commit detected: {} with {} total line changes", 
                           commit.getSha(), totalChanges);
                // Could send notifications to project maintainers
            }

            // Commits that affect many files might need review
            if (commit.getFilesChanged() != null && commit.getFilesChanged() > 20) {
                logger.info("Multi-file commit detected: {} affecting {} files", 
                           commit.getSha(), commit.getFilesChanged());
                // Could trigger review notifications
            }

        } catch (Exception e) {
            logger.error("Error triggering commit notifications: {}", commit.getSha(), e);
        }
    }

    /**
     * Scheduled task to clean up old data and optimize analytics.
     * Runs every day at 2 AM.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void performDailyMaintenance() {
        logger.info("Starting daily maintenance tasks");

        try {
            // Clean up old temporary data (if any)
            cleanupOldData();

            // Recalculate aggregated statistics
            recalculateProjectStatistics();

            // Optimize database indexes (if needed)
            optimizeAnalyticsData();

            logger.info("Daily maintenance tasks completed successfully");
        } catch (Exception e) {
            logger.error("Error during daily maintenance", e);
        }
    }

    /**
     * Clean up old temporary data.
     */
    private void cleanupOldData() {
        // Remove old analytics cache entries if implemented
        // Clean up old notification records
        // This is where you'd add cleanup logic
        logger.debug("Data cleanup completed");
    }

    /**
     * Recalculate project statistics for better accuracy.
     */
    private void recalculateProjectStatistics() {
        List<Project> projects = projectRepository.findAll();
        
        for (Project project : projects) {
            try {
                // Recalculate contributor statistics
                List<Commit> projectCommits = commitRepository.findByProject(project);
                
                // Update project-level aggregated data
                int totalCommits = projectCommits.size();
                int totalLines = projectCommits.stream()
                    .mapToInt(c -> (c.getLinesAdded() != null ? c.getLinesAdded() : 0) + 
                                 (c.getLinesDeleted() != null ? c.getLinesDeleted() : 0))
                    .sum();
                
                logger.debug("Recalculated stats for project {}: {} commits, {} total lines", 
                            project.getName(), totalCommits, totalLines);
                
            } catch (Exception e) {
                logger.error("Error recalculating statistics for project: {}", project.getName(), e);
            }
        }
    }

    /**
     * Optimize analytics data for better performance.
     */
    private void optimizeAnalyticsData() {
        // This could include:
        // - Updating materialized views
        // - Rebuilding indexes
        // - Compacting data
        // - Caching frequently accessed metrics
        
        logger.debug("Analytics data optimization completed");
    }

    /**
     * Process multiple commits in batch for better performance.
     */
    @Async
    @Transactional
    public CompletableFuture<Void> processBatchCommits(List<Commit> commits) {
        logger.info("Starting batch processing for {} commits", commits.size());

        try {
            for (Commit commit : commits) {
                commitAnalyticsService.updateContributorStats(commit);
            }

            // Batch process file changes
            for (Commit commit : commits) {
                List<FileChangeMetrics> fileChanges = fileChangeMetricsRepository.findByCommitId(commit.getId());
                for (FileChangeMetrics fileChange : fileChanges) {
                    processFileChangeMetrics(fileChange);
                }
            }

            logger.info("Completed batch processing for {} commits", commits.size());
        } catch (Exception e) {
            logger.error("Error in batch commit processing", e);
        }

        return CompletableFuture.completedFuture(null);
    }
}
