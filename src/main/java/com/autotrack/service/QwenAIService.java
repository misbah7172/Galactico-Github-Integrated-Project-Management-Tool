package com.autotrack.service;

import com.autotrack.model.ContributionScore;
import com.autotrack.model.Task;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Service to interact with the Qwen AI model API for intelligent task assignment recommendations.
 * Uses OpenAI-compatible chat completions interface.
 */
@Service
public class QwenAIService {

    private static final Logger logger = LoggerFactory.getLogger(QwenAIService.class);

    @Value("${qwen.api.key}")
    private String apiKey;

    @Value("${qwen.api.url}")
    private String apiUrl;

    @Value("${qwen.model}")
    private String model;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public QwenAIService() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    // Constructor for testing injects
    public QwenAIService(RestTemplate restTemplate, ObjectMapper objectMapper, String apiKey, String apiUrl, String model) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
    }

    /**
     * Checks if Qwen AI integration is configured and enabled.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    /**
     * Recommends task assignment by calling Qwen AI API.
     */
    public Optional<QwenAssignmentResult> recommendAssignee(Task task, List<ContributionScore> scores) {
        if (!isConfigured()) {
            logger.warn("Qwen API key is not configured. Falling back to local heuristic engine.");
            return Optional.empty();
        }

        try {
            logger.info("Calling Qwen AI API using model: {} to recommend assignee for task: {}", model, task.getTitle());

            // 1. Build the system & user prompt messages
            String systemPrompt = "You are a professional project management AI assistant specializing in software engineering workload distribution.\n" +
                    "Your goal is to recommend the best team member for a new task by analyzing each member's past contributions, commits, timelines, and current workload.\n" +
                    "You must distribute the workload fairly, ensuring team members are not overloaded (balance workload capacity), and award scores depending on penalties (late deliveries, inactivity) and bonuses (early deliveries, high code quality).\n" +
                    "You must analyze the task tags/requirements against each member's primary expertise.\n" +
                    "You must return a RAW JSON response matching the following schema structure, with NO explanation text outside the JSON, and DO NOT wrap it in markdown code blocks like ```json:\n" +
                    "{\n" +
                    "  \"selectedRecommendationUserId\": 1,\n" +
                    "  \"recommendations\": [\n" +
                    "    {\n" +
                    "      \"userId\": 1,\n" +
                    "      \"recommendationScore\": 85.5,\n" +
                    "      \"performanceComponent\": 88.0,\n" +
                    "      \"availabilityComponent\": 75.0,\n" +
                    "      \"expertiseComponent\": 80.0,\n" +
                    "      \"penaltyBonusComponent\": 90.0,\n" +
                    "      \"reasoning\": \"Alice has strong expertise matching the task requirements and is currently at 25% capacity. She has a high delivery rate, though she has 1 overdue penalty.\",\n" +
                    "      \"strengths\": [\"Strong backend expertise matching task tags\", \"Good availability (25% capacity)\"],\n" +
                    "      \"concerns\": [\"1 late delivery (-5 points penalty)\"]\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"teamSummary\": {\n" +
                    "    \"teamNetScore\": 72.5,\n" +
                    "    \"teamHealth\": \"HEALTHY\"\n" +
                    "  }\n" +
                    "}";

            Map<String, Object> taskInfo = new HashMap<>();
            taskInfo.put("taskId", task.getId());
            taskInfo.put("title", task.getTitle());
            taskInfo.put("description", task.getUserStory() != null ? task.getUserStory() : (task.getAcceptanceCriteria() != null ? task.getAcceptanceCriteria() : ""));
            taskInfo.put("tags", task.getTags());
            taskInfo.put("priority", task.getPriorityLevel() != null ? task.getPriorityLevel().name() : "MEDIUM");
            taskInfo.put("storyPoints", task.getStoryPoints() != null ? task.getStoryPoints() : 3);

            List<Map<String, Object>> membersInfo = new ArrayList<>();
            for (ContributionScore cs : scores) {
                Map<String, Object> member = new HashMap<>();
                member.put("userId", cs.getUser().getId());
                member.put("username", cs.getUser().getNickname());
                member.put("email", cs.getUser().getEmail());
                member.put("totalCommits", cs.getTotalCommits());
                member.put("productivityScore", cs.getProductivityScore());
                member.put("codeQualityScore", cs.getCodeQualityScore());
                member.put("onTimeDeliveryRate", cs.getOnTimeRate());
                member.put("earlyDeliveries", cs.getEarlyDeliveries());
                member.put("lateDeliveries", cs.getLateDeliveries());
                member.put("declinedTasks", cs.getDeclinedTasks());
                member.put("daysSinceLastCommit", cs.getDaysSinceLastCommit());
                member.put("activeTaskCount", cs.getActiveTaskCount());
                member.put("activeStoryPoints", cs.getActiveStoryPoints());
                member.put("capacityUtilization", cs.getCapacityUtilization());
                member.put("primaryExpertise", cs.getPrimaryExpertise());
                membersInfo.add(member);
            }

            Map<String, Object> promptData = new HashMap<>();
            promptData.put("taskToAssign", taskInfo);
            promptData.put("teamMembers", membersInfo);

            String userPrompt = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(promptData);

            // 2. Prepare HTTP headers and payload
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey.trim());

            Map<String, Object> messageSystem = new HashMap<>();
            messageSystem.put("role", "system");
            messageSystem.put("content", systemPrompt);

            Map<String, Object> messageUser = new HashMap<>();
            messageUser.put("role", "user");
            messageUser.put("content", userPrompt);

            List<Map<String, Object>> messages = Arrays.asList(messageSystem, messageUser);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.2);

            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

            // 3. Make POST request
            String endpoint = apiUrl.endsWith("/") ? apiUrl + "chat/completions" : apiUrl + "/chat/completions";
            logger.debug("Requesting Qwen API endpoint: {}", endpoint);
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(endpoint, requestEntity, String.class);

            if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                String responseBody = responseEntity.getBody();
                logger.debug("Qwen API raw response: {}", responseBody);

                // Parse standard ChatCompletion response
                Map<String, Object> parsedResponse = objectMapper.readValue(responseBody, Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) parsedResponse.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> choice = choices.get(0);
                    Map<String, Object> message = (Map<String, Object>) choice.get("message");
                    if (message != null && message.get("content") != null) {
                        String content = (String) message.get("content");
                        String cleanedJson = cleanJsonString(content);
                        logger.debug("Cleaned Qwen JSON: {}", cleanedJson);

                        QwenAssignmentResult result = objectMapper.readValue(cleanedJson, QwenAssignmentResult.class);
                        return Optional.of(result);
                    }
                }
            } else {
                logger.error("Qwen API returned unsuccessful status: {}", responseEntity.getStatusCode());
            }

        } catch (Exception e) {
            logger.error("Error calling or parsing Qwen AI API response: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }

    /**
     * Clean markdown backticks or formatting from LLM response.
     */
    private String cleanJsonString(String raw) {
        if (raw == null) return "";
        raw = raw.trim();
        if (raw.startsWith("```")) {
            raw = raw.replaceAll("^```[a-zA-Z]*\\s*", "");
            raw = raw.replaceAll("\\s*```$", "");
        }
        return raw.trim();
    }

    // --- Inner classes for mapping Qwen API response ---

    public static class QwenAssignmentResult {
        private Long selectedRecommendationUserId;
        private List<QwenRecommendation> recommendations;
        private QwenTeamSummary teamSummary;

        public Long getSelectedRecommendationUserId() { return selectedRecommendationUserId; }
        public void setSelectedRecommendationUserId(Long id) { this.selectedRecommendationUserId = id; }

        public List<QwenRecommendation> getRecommendations() { return recommendations; }
        public void setRecommendations(List<QwenRecommendation> recommendations) { this.recommendations = recommendations; }

        public QwenTeamSummary getTeamSummary() { return teamSummary; }
        public void setTeamSummary(QwenTeamSummary teamSummary) { this.teamSummary = teamSummary; }
    }

    public static class QwenRecommendation {
        private Long userId;
        private Double recommendationScore;
        private Double performanceComponent;
        private Double availabilityComponent;
        private Double expertiseComponent;
        private Double penaltyBonusComponent;
        private String reasoning;
        private List<String> strengths;
        private List<String> concerns;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public Double getRecommendationScore() { return recommendationScore; }
        public void setRecommendationScore(Double score) { this.recommendationScore = score; }

        public Double getPerformanceComponent() { return performanceComponent; }
        public void setPerformanceComponent(Double performance) { this.performanceComponent = performance; }

        public Double getAvailabilityComponent() { return availabilityComponent; }
        public void setAvailabilityComponent(Double availability) { this.availabilityComponent = availability; }

        public Double getExpertiseComponent() { return expertiseComponent; }
        public void setExpertiseComponent(Double expertise) { this.expertiseComponent = expertise; }

        public Double getPenaltyBonusComponent() { return penaltyBonusComponent; }
        public void setPenaltyBonusComponent(Double penaltyBonus) { this.penaltyBonusComponent = penaltyBonus; }

        public String getReasoning() { return reasoning; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }

        public List<String> getStrengths() { return strengths; }
        public void setStrengths(List<String> strengths) { this.strengths = strengths; }

        public List<String> getConcerns() { return concerns; }
        public void setConcerns(List<String> concerns) { this.concerns = concerns; }
    }

    public static class QwenTeamSummary {
        private Double teamNetScore;
        private String teamHealth;

        public Double getTeamNetScore() { return teamNetScore; }
        public void setTeamNetScore(Double score) { this.teamNetScore = score; }

        public String getTeamHealth() { return teamHealth; }
        public void setTeamHealth(String health) { this.teamHealth = health; }
    }
}
