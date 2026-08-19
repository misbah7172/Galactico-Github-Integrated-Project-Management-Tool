package com.autotrack.service;

import com.autotrack.model.ContributionScore;
import com.autotrack.model.Project;
import com.autotrack.model.Task;
import com.autotrack.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QwenAIServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private ObjectMapper objectMapper;
    private QwenAIService qwenAIService;
    private Task testTask;
    private List<ContributionScore> testScores;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        
        Project project = new Project();
        project.setId(1L);
        project.setName("Test Project");

        testTask = new Task();
        testTask.setId(100L);
        testTask.setTitle("Implement REST Endpoint");
        testTask.setUserStory("Create a new controller for reports");
        testTask.setProject(project);

        User user = new User();
        user.setId(1L);
        user.setNickname("alice");
        user.setEmail("alice@test.com");

        ContributionScore cs = new ContributionScore(project, user);
        cs.setTotalCommits(20);
        cs.setProductivityScore(80.0);
        cs.setCodeQualityScore(85.0);
        cs.setOnTimeRate(90.0);
        cs.setCapacityUtilization(0.3);
        cs.setPrimaryExpertise("backend");

        testScores = Collections.singletonList(cs);
    }

    @Test
    void isConfigured_shouldReturnFalseWhenApiKeyEmpty() {
        qwenAIService = new QwenAIService(restTemplate, objectMapper, "", "http://mock-api", "qwen-plus");
        assertThat(qwenAIService.isConfigured()).isFalse();

        qwenAIService = new QwenAIService(restTemplate, objectMapper, null, "http://mock-api", "qwen-plus");
        assertThat(qwenAIService.isConfigured()).isFalse();
    }

    @Test
    void isConfigured_shouldReturnTrueWhenApiKeySet() {
        qwenAIService = new QwenAIService(restTemplate, objectMapper, "sk-test-key", "http://mock-api", "qwen-plus");
        assertThat(qwenAIService.isConfigured()).isTrue();
    }

    @Test
    void recommendAssignee_shouldParseResponseSuccessfully() throws Exception {
        qwenAIService = new QwenAIService(restTemplate, objectMapper, "sk-test-key", "http://mock-api", "qwen-plus");

        String mockResponseBody = "{\n" +
                "  \"choices\": [\n" +
                "    {\n" +
                "      \"message\": {\n" +
                "        \"content\": \"{\\n  \\\"selectedRecommendationUserId\\\": 1,\\n  \\\"recommendations\\\": [\\n    {\\n      \\\"userId\\\": 1,\\n      \\\"recommendationScore\\\": 88.5,\\n      \\\"performanceComponent\\\": 85.0,\\n      \\\"availabilityComponent\\\": 70.0,\\n      \\\"expertiseComponent\\\": 90.0,\\n      \\\"penaltyBonusComponent\\\": 80.0,\\n      \\\"reasoning\\\": \\\"Strong backend match\\\",\\n      \\\"strengths\\\": [\\\"Expertise\\\"],\\n      \\\"concerns\\\": []\\n    }\\n  ],\\n  \\\"teamSummary\\\": {\\n    \\\"teamNetScore\\\": 88.5,\\n    \\\"teamHealth\\\": \\\"HEALTHY\\\"\\n  }\\n}\"\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        when(restTemplate.postForEntity(eq("http://mock-api/chat/completions"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockResponseBody, HttpStatus.OK));

        Optional<QwenAIService.QwenAssignmentResult> resultOpt = qwenAIService.recommendAssignee(testTask, testScores);

        assertThat(resultOpt).isPresent();
        QwenAIService.QwenAssignmentResult result = resultOpt.get();
        assertThat(result.getSelectedRecommendationUserId()).isEqualTo(1L);
        assertThat(result.getRecommendations()).hasSize(1);
        
        QwenAIService.QwenRecommendation rec = result.getRecommendations().get(0);
        assertThat(rec.getUserId()).isEqualTo(1L);
        assertThat(rec.getRecommendationScore()).isEqualTo(88.5);
        assertThat(rec.getReasoning()).isEqualTo("Strong backend match");
        assertThat(rec.getStrengths()).containsExactly("Expertise");
        assertThat(result.getTeamSummary().getTeamHealth()).isEqualTo("HEALTHY");
    }

    @Test
    void recommendAssignee_shouldParseMarkdownCodeBlockResponse() throws Exception {
        qwenAIService = new QwenAIService(restTemplate, objectMapper, "sk-test-key", "http://mock-api", "qwen-plus");

        // The LLM wraps response in ```json ... ``` code blocks
        String mockResponseBody = "{\n" +
                "  \"choices\": [\n" +
                "    {\n" +
                "      \"message\": {\n" +
                "        \"content\": \"```json\\n{\\n  \\\"selectedRecommendationUserId\\\": 1,\\n  \\\"recommendations\\\": [\\n    {\\n      \\\"userId\\\": 1,\\n      \\\"recommendationScore\\\": 88.5,\\n      \\\"reasoning\\\": \\\"Strong backend match\\\"\\n    }\\n  ]\\n}\\n```\"\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        when(restTemplate.postForEntity(eq("http://mock-api/chat/completions"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockResponseBody, HttpStatus.OK));

        Optional<QwenAIService.QwenAssignmentResult> resultOpt = qwenAIService.recommendAssignee(testTask, testScores);

        assertThat(resultOpt).isPresent();
        QwenAIService.QwenAssignmentResult result = resultOpt.get();
        assertThat(result.getSelectedRecommendationUserId()).isEqualTo(1L);
        assertThat(result.getRecommendations()).hasSize(1);
        assertThat(result.getRecommendations().get(0).getRecommendationScore()).isEqualTo(88.5);
    }

    @Test
    void recommendAssignee_shouldHandleApiExceptionGracefully() {
        qwenAIService = new QwenAIService(restTemplate, objectMapper, "sk-test-key", "http://mock-api", "qwen-plus");

        when(restTemplate.postForEntity(eq("http://mock-api/chat/completions"), any(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection timeout"));

        Optional<QwenAIService.QwenAssignmentResult> resultOpt = qwenAIService.recommendAssignee(testTask, testScores);
        assertThat(resultOpt).isNotPresent();
    }
}
