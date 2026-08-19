package com.autotrack.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for sending Slack notifications via webhook.
 */
@Service
public class SlackService {
    
    private static final Logger logger = LoggerFactory.getLogger(SlackService.class);
    
    private final RestTemplate restTemplate;
    
    @Value("${slack.webhook.url:}")
    private String slackWebhookUrl;
    
    public SlackService() {
        this.restTemplate = new RestTemplate();
    }
    
    /**
     * Send a plain text message to Slack.
     * 
     * @param message The message text to send
     */
    @Async
    public void sendMessage(String message) {
        if (slackWebhookUrl == null || slackWebhookUrl.trim().isEmpty()) {
            logger.warn("Slack webhook URL not configured. Skipping message: {}", message);
            return;
        }
        
        try {
            // Create Slack message payload
            Map<String, Object> payload = new HashMap<>();
            payload.put("text", message);
            
            // Set headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Create HTTP entity
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            
            // Send POST request to Slack webhook
            ResponseEntity<String> response = restTemplate.postForEntity(slackWebhookUrl, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                logger.info("Slack message sent successfully: {}", message);
            } else {
                logger.error("Failed to send Slack message. Status: {}, Response: {}", 
                    response.getStatusCode(), response.getBody());
            }
            
        } catch (Exception e) {
            logger.error("Error sending Slack message: {}", message, e);
        }
    }
    
    /**
     * Send a task status change notification to Slack.
     * 
     * @param taskTitle The task title
     * @param featureCode The feature code (e.g., Feature01)
     * @param oldStatus The previous status
     * @param newStatus The new status
     * @param assigneeName The name of the assignee
     */
    @Async
    public void sendTaskStatusChangeNotification(String taskTitle, String featureCode, String oldStatus, String newStatus, String assigneeName) {
        String emoji = getStatusEmoji(newStatus);
        String message = String.format("%s Task '%s: %s' moved from %s to %s by %s.", 
            emoji, featureCode, taskTitle, oldStatus, newStatus, assigneeName);
        sendMessage(message);
    }
    
    /**
     * Send a new commit notification to Slack.
     * 
     * @param featureCode The feature code from the commit
     * @param authorName The commit author name
     * @param commitMessage The commit message
     */
    @Async
    public void sendNewCommitNotification(String featureCode, String authorName, String commitMessage) {
        String message = String.format("📝 New commit on %s by %s: '%s'", 
            featureCode, authorName, commitMessage);
        sendMessage(message);
    }
    
    /**
     * Send a task reminder notification to Slack.
     * 
     * @param taskTitle The task title
     * @param featureCode The feature code
     * @param assigneeName The assignee name
     * @param reminderType The type of reminder (e.g., "due tomorrow", "overdue")
     */
    @Async
    public void sendTaskReminderNotification(String taskTitle, String featureCode, String assigneeName, String reminderType) {
        String message = String.format("⏰ Reminder: '%s: %s' is %s (assigned to %s).", 
            featureCode, taskTitle, reminderType, assigneeName);
        sendMessage(message);
    }
    
    /**
     * Send a task creation notification to Slack.
     * 
     * @param taskTitle The task title
     * @param featureCode The feature code
     * @param assigneeName The assignee name
     */
    @Async
    public void sendTaskCreatedNotification(String taskTitle, String featureCode, String assigneeName) {
        String message = String.format("✨ New task created: '%s: %s' assigned to %s.", 
            featureCode, taskTitle, assigneeName);
        sendMessage(message);
    }
    
    /**
     * Send a task decline notification to Slack.
     * 
     * @param taskTitle The task title
     * @param featureCode The feature code
     * @param assigneeName The assignee name
     * @param teamLeaderName The team leader who declined
     */
    @Async
    public void sendTaskDeclinedNotification(String taskTitle, String featureCode, String assigneeName, String teamLeaderName) {
        String message = String.format("❌ Task '%s: %s' declined by %s. Reassigned to %s for improvements.", 
            featureCode, taskTitle, teamLeaderName, assigneeName);
        sendMessage(message);
    }
    
    /**
     * Send a task assignee change notification to Slack.
     * 
     * @param taskTitle The task title
     * @param featureCode The feature code
     * @param oldAssigneeName The old assignee name
     * @param newAssigneeName The new assignee name
     */
    @Async
    public void sendTaskAssigneeChangedNotification(String taskTitle, String featureCode, String oldAssigneeName, String newAssigneeName) {
        String message = String.format("🔄 Task '%s: %s' assignee changed from %s to %s.", 
            featureCode, taskTitle, oldAssigneeName, newAssigneeName);
        sendMessage(message);
    }
    
    /**
     * Get emoji for task status.
     * 
     * @param status The task status
     * @return Appropriate emoji
     */
    private String getStatusEmoji(String status) {
        switch (status.toUpperCase()) {
            case "TODO":
                return "📋";
            case "IN_PROGRESS":
                return "🔄";
            case "DONE":
                return "✅";
            default:
                return "📝";
        }
    }
    
    /**
     * Check if Slack is configured.
     * 
     * @return true if webhook URL is configured
     */
    public boolean isConfigured() {
        return slackWebhookUrl != null && !slackWebhookUrl.trim().isEmpty();
    }
}
