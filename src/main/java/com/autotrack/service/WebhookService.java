package com.autotrack.service;

import com.autotrack.model.*;
import com.autotrack.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

/**
 * Service for handling GitHub webhooks.
 */
@Service
public class WebhookService {

    private final ProjectRepository projectRepository;
    private final CommitRepository commitRepository;
    private final CommitParserService commitParserService;
    private final TaskService taskService;
    private final GitHubCommitAnalysisService commitAnalysisService;
    private final FileChangeMetricsRepository fileChangeMetricsRepository;
    private final ContributorStatsRepository contributorStatsRepository;
    private final BackgroundDataProcessingService backgroundDataProcessingService;
    private final AnalyticsCacheService analyticsCacheService;
    private final ObjectMapper objectMapper;

    public WebhookService(ProjectRepository projectRepository,
                         CommitRepository commitRepository,
                         CommitParserService commitParserService,
                         TaskService taskService,
                         GitHubCommitAnalysisService commitAnalysisService,
                         FileChangeMetricsRepository fileChangeMetricsRepository,
                         ContributorStatsRepository contributorStatsRepository,
                         BackgroundDataProcessingService backgroundDataProcessingService,
                         AnalyticsCacheService analyticsCacheService) {
        this.projectRepository = projectRepository;
        this.commitRepository = commitRepository;
        this.commitParserService = commitParserService;
        this.taskService = taskService;
        this.commitAnalysisService = commitAnalysisService;
        this.fileChangeMetricsRepository = fileChangeMetricsRepository;
        this.contributorStatsRepository = contributorStatsRepository;
        this.backgroundDataProcessingService = backgroundDataProcessingService;
        this.analyticsCacheService = analyticsCacheService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Process a GitHub push webhook payload and create contribution analytics from commit JSON.
     */
    @Transactional
    public WebhookProcessingResult processWebhook(Map<String, Object> payload, String signature) {
        try {
            JsonNode payloadNode = objectMapper.valueToTree(payload);

            JsonNode repository = payloadNode.get("repository");
            if (repository == null) {
                throw new RuntimeException("Invalid webhook payload: repository not found");
            }

            String repoId = getText(repository, "id", null);
            Optional<Project> projectOpt = repoId != null
                    ? projectRepository.findByGitHubRepoId(repoId)
                    : Optional.empty();

            if (projectOpt.isEmpty()) {
                String repoUrl = getText(repository, "html_url", null);
                projectOpt = projectRepository.findAll().stream()
                        .filter(p -> p.getGitHubRepoUrl() != null && repoUrl != null &&
                                (p.getGitHubRepoUrl().equals(repoUrl) ||
                                 p.getGitHubRepoUrl().equals(repoUrl + ".git")))
                        .findFirst();

                if (projectOpt.isEmpty()) {
                    throw new RuntimeException("No project found for repository: " + repoUrl);
                }
            }

            Project project = projectOpt.get();

            if (project.getWebhookSecret() != null && !project.getWebhookSecret().isEmpty() && signature != null) {
                verifySignature(objectMapper.writeValueAsString(payload), signature, project.getWebhookSecret());
            }

            WebhookProcessingResult result = new WebhookProcessingResult();
            JsonNode commits = payloadNode.get("commits");
            if (commits != null && commits.isArray()) {
                for (JsonNode commitNode : commits) {
                    result.incrementReceivedCommits();
                    CommitProcessResult commitResult = processCommit(commitNode, project);
                    switch (commitResult) {
                        case SAVED:
                            result.incrementSavedCommits();
                            result.incrementContributionsUpdated();
                            break;
                        case DUPLICATE:
                            result.incrementDuplicateCommits();
                            break;
                        case SKIPPED:
                            result.incrementSkippedCommits();
                            break;
                    }
                }
            }

            return result;
        } catch (Exception e) {
            throw new RuntimeException("Error processing webhook: " + e.getMessage(), e);
        }
    }

    /**
     * Process a single commit from the GitHub push payload.
     */
    private CommitProcessResult processCommit(JsonNode commitNode, Project project) {
        String sha = getText(commitNode, "id", null);
        if (sha == null || sha.isBlank()) {
            return CommitProcessResult.SKIPPED;
        }

        if (commitRepository.findByShaAndProject(sha, project).isPresent()) {
            return CommitProcessResult.DUPLICATE;
        }

        String message = getText(commitNode, "message", "");
        String url = getText(commitNode, "url", "");

        JsonNode author = commitNode.get("author");
        String authorName = getText(author, "name", "Unknown Contributor");
        String authorEmail = normalizeEmail(getText(author, "email", null));

        if (authorEmail == null || authorEmail.isBlank()) {
            JsonNode committer = commitNode.get("committer");
            authorName = getText(committer, "name", authorName);
            authorEmail = normalizeEmail(getText(committer, "email", null));
        }

        if (authorEmail == null || authorEmail.isBlank()) {
            authorEmail = sha + "@unknown.github";
        }

        LocalDateTime committedAt = parseCommittedAt(getText(commitNode, "timestamp", null));
        GitHubCommitAnalysisService.CommitStats stats = commitAnalysisService.extractStatsFromWebhookPayload(commitNode);

        Task task = null;
        Optional<CommitParserService.CommitInfo> commitInfoOpt = commitParserService.parseCommitMessage(message);
        if (commitInfoOpt.isPresent()) {
            task = taskService.findOrCreateTaskFromCommit(project, commitInfoOpt.get());
        }

        Commit commit = Commit.builder()
                .sha(sha)
                .message(message)
                .authorName(authorName)
                .authorEmail(authorEmail)
                .committedAt(committedAt)
                .gitHubUrl(url)
                .linesAdded(stats.getLinesAdded())
                .linesModified(stats.getLinesModified())
                .linesDeleted(stats.getLinesDeleted())
                .filesChanged(stats.getFilesChanged())
                .task(task)
                .project(project)
                .build();

        commit = commitRepository.save(commit);
        processFileChanges(commitNode, commit, project);
        updateContributorStats(commit, stats, project);
        backgroundDataProcessingService.processCommitInsightsAsync(commit);
        analyticsCacheService.invalidateProjectCache(project);

        return CommitProcessResult.SAVED;
    }

    /**
     * Process individual file changes from the commit payload.
     */
    private void processFileChanges(JsonNode commitNode, Commit commit, Project project) {
        try {
            JsonNode added = commitNode.get("added");
            if (added != null && added.isArray()) {
                for (JsonNode fileNode : added) {
                    createFileChangeMetric(commit, project, fileNode.asText(), FileChangeMetrics.ChangeType.ADDED, 0, 0, 0);
                }
            }

            JsonNode removed = commitNode.get("removed");
            if (removed != null && removed.isArray()) {
                for (JsonNode fileNode : removed) {
                    createFileChangeMetric(commit, project, fileNode.asText(), FileChangeMetrics.ChangeType.DELETED, 0, 0, 0);
                }
            }

            JsonNode modified = commitNode.get("modified");
            if (modified != null && modified.isArray()) {
                for (JsonNode fileNode : modified) {
                    int estimatedLinesAdded = commit.getFilesChanged() > 0 ? commit.getLinesAdded() / commit.getFilesChanged() : 0;
                    int estimatedLinesModified = commit.getFilesChanged() > 0 ? commit.getLinesModified() / commit.getFilesChanged() : 0;
                    int estimatedLinesDeleted = commit.getFilesChanged() > 0 ? commit.getLinesDeleted() / commit.getFilesChanged() : 0;

                    createFileChangeMetric(commit, project, fileNode.asText(), FileChangeMetrics.ChangeType.MODIFIED,
                            estimatedLinesAdded, estimatedLinesModified, estimatedLinesDeleted);
                }
            }
        } catch (Exception e) {
            System.err.println("Error processing file changes for commit " + commit.getSha() + ": " + e.getMessage());
        }
    }

    /**
     * Create a FileChangeMetrics record.
     */
    private void createFileChangeMetric(Commit commit, Project project, String fileName,
                                      FileChangeMetrics.ChangeType changeType, int linesAdded, int linesModified, int linesDeleted) {
        FileChangeMetrics fileMetrics = new FileChangeMetrics();
        fileMetrics.setCommit(commit);
        fileMetrics.setProject(project);
        fileMetrics.setFileName(fileName);
        fileMetrics.setFilePath(fileName);
        fileMetrics.setChangeType(changeType);
        fileMetrics.setLinesAdded(linesAdded);
        fileMetrics.setLinesModified(linesModified);
        fileMetrics.setLinesDeleted(linesDeleted);

        fileChangeMetricsRepository.save(fileMetrics);
    }

    /**
     * Update contributor statistics from the JSON commit author.
     */
    private void updateContributorStats(Commit commit, GitHubCommitAnalysisService.CommitStats stats, Project project) {
        try {
            String email = commit.getAuthorEmail();
            Optional<ContributorStats> existingStatsOpt = contributorStatsRepository.findByProjectAndContributorEmail(project, email);

            ContributorStats contributorStats;
            if (existingStatsOpt.isPresent()) {
                contributorStats = existingStatsOpt.get();
                contributorStats.setContributorName(commit.getAuthorName());
                contributorStats.setTotalCommits(contributorStats.getTotalCommits() + 1);
                contributorStats.setTotalLinesAdded(contributorStats.getTotalLinesAdded() + stats.getLinesAdded());
                contributorStats.setTotalLinesModified(contributorStats.getTotalLinesModified() + stats.getLinesModified());
                contributorStats.setTotalLinesDeleted(contributorStats.getTotalLinesDeleted() + stats.getLinesDeleted());
                contributorStats.setTotalFilesChanged(contributorStats.getTotalFilesChanged() + stats.getFilesChanged());

                if (contributorStats.getFirstCommitDate() == null || commit.getCommittedAt().isBefore(contributorStats.getFirstCommitDate())) {
                    contributorStats.setFirstCommitDate(commit.getCommittedAt());
                }
                if (contributorStats.getLastCommitDate() == null || commit.getCommittedAt().isAfter(contributorStats.getLastCommitDate())) {
                    contributorStats.setLastCommitDate(commit.getCommittedAt());
                }
            } else {
                contributorStats = new ContributorStats(project, commit.getAuthorName(), email);
                contributorStats.setTotalCommits(1);
                contributorStats.setTotalLinesAdded(stats.getLinesAdded());
                contributorStats.setTotalLinesModified(stats.getLinesModified());
                contributorStats.setTotalLinesDeleted(stats.getLinesDeleted());
                contributorStats.setTotalFilesChanged(stats.getFilesChanged());
                contributorStats.setFirstCommitDate(commit.getCommittedAt());
                contributorStats.setLastCommitDate(commit.getCommittedAt());
            }

            contributorStats.calculateMetrics();
            contributorStatsRepository.save(contributorStats);
        } catch (Exception e) {
            System.err.println("Error updating contributor stats for commit " + commit.getSha() + ": " + e.getMessage());
        }
    }

    private String getText(JsonNode node, String fieldName, String defaultValue) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return defaultValue;
        }
        return node.get(fieldName).asText(defaultValue);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase();
    }

    private LocalDateTime parseCommittedAt(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return ZonedDateTime.parse(timestamp).toLocalDateTime();
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    private enum CommitProcessResult {
        SAVED,
        DUPLICATE,
        SKIPPED
    }

    public static class WebhookProcessingResult {
        private int receivedCommits;
        private int savedCommits;
        private int duplicateCommits;
        private int skippedCommits;
        private int contributionsUpdated;

        public int getReceivedCommits() { return receivedCommits; }
        public int getSavedCommits() { return savedCommits; }
        public int getDuplicateCommits() { return duplicateCommits; }
        public int getSkippedCommits() { return skippedCommits; }
        public int getContributionsUpdated() { return contributionsUpdated; }

        private void incrementReceivedCommits() { receivedCommits++; }
        private void incrementSavedCommits() { savedCommits++; }
        private void incrementDuplicateCommits() { duplicateCommits++; }
        private void incrementSkippedCommits() { skippedCommits++; }
        private void incrementContributionsUpdated() { contributionsUpdated++; }
    }

    /**
     * Verify the GitHub webhook signature.
     */
    private void verifySignature(String payload, String signature, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(signingKey);
        byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

        String expectedSignature = "sha256=" + HexFormat.of().formatHex(rawHmac);

        if (!signature.equals(expectedSignature)) {
            throw new RuntimeException("Invalid webhook signature");
        }
    }
}
