package com.autotrack.service;

import com.autotrack.dto.AITaskAssignmentRequest;
import com.autotrack.dto.AITaskAssignmentResponse;
import com.autotrack.model.*;
import com.autotrack.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AI task assignment service.
 * 
 * Tests verify:
 * - Scoring algorithm correctly computes penalty/bonus
 * - Ranking algorithm produces correct order
 * - Expertise matching works
 * - Capacity filtering works
 * - Assignment actually updates the task
 */
@ExtendWith(MockitoExtension.class)
class AITaskAssignmentServiceTest {

    @Mock
    private TaskRepository taskRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ContributionScoringService scoringService;
    @Mock
    private ContributionScoreRepository contributionScoreRepository;
    @Mock
    private AIAssignmentDecisionRepository decisionRepository;
    @Mock
    private TaskService taskService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private SlackService slackService;

    private AITaskAssignmentService assignmentService;

    private Project testProject;
    private Task testTask;
    private User user1;
    private User user2;
    private User user3;

    @BeforeEach
    void setUp() {
        assignmentService = new AITaskAssignmentService(
                taskRepository, projectRepository, userRepository,
                scoringService, contributionScoreRepository, decisionRepository,
                taskService, notificationService, slackService
        );

        // Create test project
        testProject = new Project();
        testProject.setId(1L);
        testProject.setName("Test Project");
        
        Team team = new Team();
        Set<User> members = new HashSet<>();
        user1 = createUser(1L, "alice", "alice@test.com");
        user2 = createUser(2L, "bob", "bob@test.com");
        user3 = createUser(3L, "charlie", "charlie@test.com");
        members.add(user1);
        members.add(user2);
        members.add(user3);
        team.setMembers(members);
        testProject.setTeam(team);

        // Create test task
        testTask = new Task();
        testTask.setId(100L);
        testTask.setTitle("Implement feature X");
        testTask.setProject(testProject);
        testTask.setTagsString("backend,api");
    }

    @Test
    void recommendAssignee_shouldReturnRankedCandidates() {
        // Given
        AITaskAssignmentRequest request = new AITaskAssignmentRequest();
        request.setTaskId(100L);
        request.setProjectId(1L);
        request.setDecisionMode("RECOMMEND");

        when(taskRepository.findById(100L)).thenReturn(Optional.of(testTask));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        // Create mock scores with different performance levels
        List<ContributionScore> mockScores = Arrays.asList(
                createMockScore(user1, 85.0, 70.0, 0.3, 5.0),  // High performer, low workload
                createMockScore(user2, 75.0, 60.0, 0.6, 3.0),  // Good performer, medium workload
                createMockScore(user3, 65.0, 50.0, 0.8, 2.0)   // Average performer, high workload
        );
        when(scoringService.scoreTeamMembers(any(Project.class))).thenReturn(mockScores);

        // When
        AITaskAssignmentResponse response = assignmentService.recommendAssignee(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getRequestId()).isNotEmpty();
        assertThat(response.getTaskId()).isEqualTo(100L);
        assertThat(response.getAllRecommendations()).hasSize(3);
        
        // Verify ranking order (highest score first)
        assertThat(response.getAllRecommendations().get(0).getUsername()).isEqualTo("alice");
        assertThat(response.getAllRecommendations().get(1).getUsername()).isEqualTo("bob");
        assertThat(response.getAllRecommendations().get(2).getUsername()).isEqualTo("charlie");

        // Verify scores are in descending order
        double prevScore = Double.MAX_VALUE;
        for (AITaskAssignmentResponse.Recommendation rec : response.getAllRecommendations()) {
            assertThat(rec.getRecommendationScore()).isLessThanOrEqualTo(prevScore);
            prevScore = rec.getRecommendationScore();
        }

        // Verify selected recommendation is the top one
        assertThat(response.getSelectedRecommendation().getUsername()).isEqualTo("alice");
        assertThat(response.getSelectedRecommendation().getIsSelected()).isTrue();

        // Verify audit trail was created
        verify(decisionRepository, times(3)).save(any(AIAssignmentDecision.class));
    }

    @Test
    void recommendAssignee_shouldFilterByCapacityThreshold() {
        // Given
        AITaskAssignmentRequest request = new AITaskAssignmentRequest();
        request.setTaskId(100L);
        request.setProjectId(1L);
        request.setMinCapacityThreshold(0.5);  // Only 50%+ capacity

        when(taskRepository.findById(100L)).thenReturn(Optional.of(testTask));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        // Create scores with varying capacity
        List<ContributionScore> mockScores = Arrays.asList(
                createMockScore(user1, 85.0, 70.0, 0.3, 5.0),  // 30% utilized = 70% available ✓
                createMockScore(user2, 75.0, 60.0, 0.6, 3.0),  // 60% utilized = 40% available ✗
                createMockScore(user3, 65.0, 50.0, 0.8, 2.0)   // 80% utilized = 20% available ✗
        );
        when(scoringService.scoreTeamMembers(any(Project.class))).thenReturn(mockScores);

        // When
        AITaskAssignmentResponse response = assignmentService.recommendAssignee(request);

        // Then
        assertThat(response.getAllRecommendations()).hasSize(1);
        assertThat(response.getAllRecommendations().get(0).getUsername()).isEqualTo("alice");
    }

    @Test
    void recommendAssignee_withAssignMode_shouldActuallyAssignTask() {
        // Given
        AITaskAssignmentRequest request = new AITaskAssignmentRequest();
        request.setTaskId(100L);
        request.setProjectId(1L);
        request.setDecisionMode("ASSIGN");

        when(taskRepository.findById(100L)).thenReturn(Optional.of(testTask));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        List<ContributionScore> mockScores = Collections.singletonList(
                createMockScore(user1, 85.0, 70.0, 0.3, 5.0)
        );
        when(scoringService.scoreTeamMembers(any(Project.class))).thenReturn(mockScores);

        // When
        AITaskAssignmentResponse response = assignmentService.recommendAssignee(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getDecisionMode()).isEqualTo("ASSIGN");
        
        // Verify task was actually assigned
        verify(taskRepository).save(argThat(task -> {
            assertThat(task.getAssignee()).isEqualTo(user1);
            return true;
        }));

        // Verify notifications were sent
        verify(notificationService).notifyTaskAssigneeChanged(any(Task.class), any());
    }

    @Test
    void recommendAssignee_shouldHandleNoTeamMembers() {
        // Given
        AITaskAssignmentRequest request = new AITaskAssignmentRequest();
        request.setTaskId(100L);
        request.setProjectId(1L);

        when(taskRepository.findById(100L)).thenReturn(Optional.of(testTask));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));
        when(scoringService.scoreTeamMembers(any(Project.class))).thenReturn(Collections.emptyList());

        // When/Then
        assertThatThrownBy(() -> assignmentService.recommendAssignee(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("No team members found");
    }

    @Test
    void recommendAssignee_shouldIdentifyStrengthsAndConcerns() {
        // Given
        AITaskAssignmentRequest request = new AITaskAssignmentRequest();
        request.setTaskId(100L);
        request.setProjectId(1L);

        when(taskRepository.findById(100L)).thenReturn(Optional.of(testTask));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        // Create a score with clear strengths and concerns
        ContributionScore score = createMockScore(user1, 90.0, 85.0, 0.3, 5.0);
        score.setOnTimeRate(95.0);  // Strength: high on-time rate
        score.setApprovalRate(92.0);  // Strength: high quality
        score.setLateDeliveries(3);  // Concern: late deliveries
        score.setDaysSinceLastCommit(10);  // Concern: inactive
        
        when(scoringService.scoreTeamMembers(any(Project.class))).thenReturn(Collections.singletonList(score));

        // When
        AITaskAssignmentResponse response = assignmentService.recommendAssignee(request);

        // Then
        AITaskAssignmentResponse.Recommendation rec = response.getSelectedRecommendation();
        assertThat(rec.getStrengths()).isNotEmpty();
        assertThat(rec.getConcerns()).isNotEmpty();
        
        assertThat(rec.getStrengths().stream().anyMatch(s -> s.contains("on-time"))).isTrue();
        assertThat(rec.getStrengths().stream().anyMatch(s -> s.contains("quality"))).isTrue();
        assertThat(rec.getConcerns().stream().anyMatch(s -> s.contains("late"))).isTrue();
        assertThat(rec.getConcerns().stream().anyMatch(s -> s.contains("Inactive"))).isTrue();
    }

    @Test
    void recommendAssignee_shouldMatchExpertise() {
        // Given
        AITaskAssignmentRequest request = new AITaskAssignmentRequest();
        request.setTaskId(100L);
        request.setProjectId(1L);
        request.setConsiderExpertise(true);

        when(taskRepository.findById(100L)).thenReturn(Optional.of(testTask));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        // Create scores with different expertise
        ContributionScore score1 = createMockScore(user1, 70.0, 60.0, 0.3, 5.0);
        score1.setPrimaryExpertise("backend");  // Matches task tag
        
        ContributionScore score2 = createMockScore(user2, 70.0, 60.0, 0.3, 5.0);
        score2.setPrimaryExpertise("frontend");  // Doesn't match
        
        when(scoringService.scoreTeamMembers(any(Project.class))).thenReturn(Arrays.asList(score1, score2));

        // When
        AITaskAssignmentResponse response = assignmentService.recommendAssignee(request);

        // Then
        // Backend expert should rank higher due to expertise match
        assertThat(response.getAllRecommendations().get(0).getUsername()).isEqualTo("alice");
        assertThat(response.getAllRecommendations().get(0).getExpertiseComponent())
                .isGreaterThan(response.getAllRecommendations().get(1).getExpertiseComponent());
    }

    @Test
    void recommendAssignee_shouldApplyPenaltiesAndBonuses() {
        // Given
        AITaskAssignmentRequest request = new AITaskAssignmentRequest();
        request.setTaskId(100L);
        request.setProjectId(1L);

        when(taskRepository.findById(100L)).thenReturn(Optional.of(testTask));
        when(projectRepository.findById(1L)).thenReturn(Optional.of(testProject));

        // Create two users with same base performance but different penalties/bonuses
        ContributionScore score1 = createMockScore(user1, 70.0, 60.0, 0.3, 5.0);
        score1.setEarlyDeliveries(5);  // Bonus: +10 points
        score1.setLateDeliveries(0);
        score1.setTotalBonus(10.0);
        score1.setTotalPenalty(0.0);
        
        ContributionScore score2 = createMockScore(user2, 70.0, 60.0, 0.3, 5.0);
        score2.setEarlyDeliveries(0);
        score2.setLateDeliveries(3);  // Penalty: -15 points
        score2.setTotalBonus(0.0);
        score2.setTotalPenalty(15.0);
        
        when(scoringService.scoreTeamMembers(any(Project.class))).thenReturn(Arrays.asList(score1, score2));

        // When
        AITaskAssignmentResponse response = assignmentService.recommendAssignee(request);

        // Then
        // User with bonuses should rank higher than user with penalties
        assertThat(response.getAllRecommendations().get(0).getUsername()).isEqualTo("alice");
        assertThat(response.getAllRecommendations().get(0).getPenaltyBonusComponent())
                .isGreaterThan(response.getAllRecommendations().get(1).getPenaltyBonusComponent());
    }

    // Helper methods

    private User createUser(Long id, String nickname, String email) {
        User user = new User();
        user.setId(id);
        user.setNickname(nickname);
        user.setEmail(email);
        return user;
    }

    private ContributionScore createMockScore(User user, double performance, double quality, 
                                               double capacity, double velocity) {
        ContributionScore score = new ContributionScore(testProject, user);
        score.setOverallPerformanceScore(performance);
        score.setCodeQualityScore(quality);
        score.setCapacityUtilization(capacity);
        score.setAvailabilityScore(100 - (capacity * 100));
        score.setCommitVelocityScore(velocity * 10);
        score.setProductivityScore(performance * 0.9);
        score.setOnTimeRate(85.0);
        score.setApprovalRate(quality);
        score.setTotalCommits(50);
        score.setActiveTaskCount(3);
        score.setActiveStoryPoints((int)(capacity * 20));
        score.setHistoricalVelocity(25);
        score.setPrimaryExpertise("general");
        score.setEarlyDeliveries(0);
        score.setLateDeliveries(0);
        score.setDeclinedTasks(0);
        score.setDaysSinceLastCommit(2);
        score.setTotalPenalty(0.0);
        score.setTotalBonus(0.0);
        score.setNetScore(performance);
        score.setReasoning("Test reasoning");
        return score;
    }
}
