package com.autotrack.service;

import com.autotrack.model.Commit;
import com.autotrack.model.ContributorStats;
import com.autotrack.model.Project;
import com.autotrack.repository.CommitRepository;
import com.autotrack.repository.ContributorStatsRepository;
import com.autotrack.repository.FileChangeMetricsRepository;
import com.autotrack.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private CommitRepository commitRepository;

    @Mock
    private CommitParserService commitParserService;

    @Mock
    private TaskService taskService;

    @Mock
    private GitHubCommitAnalysisService commitAnalysisService;

    @Mock
    private FileChangeMetricsRepository fileChangeMetricsRepository;

    @Mock
    private ContributorStatsRepository contributorStatsRepository;

    @Mock
    private BackgroundDataProcessingService backgroundDataProcessingService;

    @Mock
    private AnalyticsCacheService analyticsCacheService;

    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(
                projectRepository,
                commitRepository,
                commitParserService,
                taskService,
                commitAnalysisService,
                fileChangeMetricsRepository,
                contributorStatsRepository,
                backgroundDataProcessingService,
                analyticsCacheService);
    }

    @Test
    void pushWebhookCreatesContributionFromCommitJsonWithoutStructuredMessage() {
        Project project = new Project();
        project.setId(1L);
        project.setName("Webhook Project");
        project.setGitHubRepoId("12345");

        Map<String, Object> payload = Map.of(
                "repository", Map.of(
                        "id", 12345,
                        "html_url", "https://github.com/example/webhook-project"),
                "commits", List.of(Map.of(
                        "id", "abc123",
                        "message", "Add webhook contribution tracking",
                        "url", "https://api.github.com/repos/example/webhook-project/commits/abc123",
                        "timestamp", "2026-06-22T10:15:30Z",
                        "author", Map.of(
                                "name", "Jane Developer",
                                "email", "Jane@Example.com"),
                        "added", List.of("src/NewFeature.java"),
                        "modified", List.of("README.md"),
                        "removed", List.of())));

        when(projectRepository.findByGitHubRepoId("12345")).thenReturn(Optional.of(project));
        when(commitRepository.findByShaAndProject("abc123", project)).thenReturn(Optional.empty());
        when(commitParserService.parseCommitMessage("Add webhook contribution tracking")).thenReturn(Optional.empty());
        when(commitAnalysisService.extractStatsFromWebhookPayload(any()))
                .thenReturn(new GitHubCommitAnalysisService.CommitStats(12, 3, 2, 2));
        when(commitRepository.save(any(Commit.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(contributorStatsRepository.findByProjectAndContributorEmail(project, "jane@example.com"))
                .thenReturn(Optional.empty());
        when(backgroundDataProcessingService.processCommitInsightsAsync(any(Commit.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        WebhookService.WebhookProcessingResult result = webhookService.processWebhook(payload, null);

        assertEquals(1, result.getReceivedCommits());
        assertEquals(1, result.getSavedCommits());
        assertEquals(1, result.getContributionsUpdated());
        assertEquals(0, result.getDuplicateCommits());
        assertEquals(0, result.getSkippedCommits());

        ArgumentCaptor<Commit> commitCaptor = ArgumentCaptor.forClass(Commit.class);
        verify(commitRepository).save(commitCaptor.capture());
        Commit savedCommit = commitCaptor.getValue();
        assertEquals("abc123", savedCommit.getSha());
        assertEquals("Jane Developer", savedCommit.getAuthorName());
        assertEquals("jane@example.com", savedCommit.getAuthorEmail());
        assertEquals(12, savedCommit.getLinesAdded());
        assertEquals(3, savedCommit.getLinesModified());
        assertEquals(2, savedCommit.getLinesDeleted());
        assertEquals(2, savedCommit.getFilesChanged());
        assertNotNull(savedCommit.getCommittedAt());

        ArgumentCaptor<ContributorStats> statsCaptor = ArgumentCaptor.forClass(ContributorStats.class);
        verify(contributorStatsRepository).save(statsCaptor.capture());
        ContributorStats stats = statsCaptor.getValue();
        assertEquals("Jane Developer", stats.getContributorName());
        assertEquals("jane@example.com", stats.getContributorEmail());
        assertEquals(1, stats.getTotalCommits());
        assertEquals(12, stats.getTotalLinesAdded());
        assertEquals(3, stats.getTotalLinesModified());
        assertEquals(2, stats.getTotalLinesDeleted());
        assertEquals(2, stats.getTotalFilesChanged());

        verify(taskService, never()).findOrCreateTaskFromCommit(eq(project), any());
        verify(backgroundDataProcessingService).processCommitInsightsAsync(savedCommit);
        verify(analyticsCacheService).invalidateProjectCache(project);
    }
}
