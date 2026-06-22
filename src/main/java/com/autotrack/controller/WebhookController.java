package com.autotrack.controller;

import com.autotrack.dto.CommitWebhookDTO;
import com.autotrack.model.PendingCommit;
import com.autotrack.service.CommitReviewService;
import com.autotrack.service.WebhookService;
import com.autotrack.service.CICDStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for GitHub webhook integration.
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {

    private final WebhookService webhookService;
    
    @Autowired
    private CommitReviewService commitReviewService;
    
    @Autowired
    private CICDStatusService cicdStatusService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * Endpoint to receive GitHub webhook events.
     * 
     * @param payload The GitHub webhook payload
     * @param signature The GitHub webhook signature header (X-Hub-Signature-256)
     * @param event The GitHub event type header (X-GitHub-Event)
     * @return Response with appropriate status
     */
    @PostMapping("/github")
    public ResponseEntity<String> handleGitHubWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event) {
        
        try {
            if (event == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Missing X-GitHub-Event header");
            }
            
            switch (event) {
                case "push":
                    // Handle push events for commit tracking
                    WebhookService.WebhookProcessingResult result = webhookService.processWebhook(payload, signature);
                    return ResponseEntity.status(HttpStatus.OK)
                            .body("Push webhook processed successfully: received=" + result.getReceivedCommits()
                                    + ", saved=" + result.getSavedCommits()
                                    + ", duplicates=" + result.getDuplicateCommits()
                                    + ", skipped=" + result.getSkippedCommits()
                                    + ", contributionsUpdated=" + result.getContributionsUpdated());
                            
                case "workflow_run":
                    // Handle GitHub Actions workflow run events for CI/CD status
                    cicdStatusService.processWorkflowRunWebhook(payload);
                    return ResponseEntity.status(HttpStatus.OK)
                            .body("Workflow run webhook processed successfully");
                            
                default:
                    return ResponseEntity.status(HttpStatus.OK)
                            .body("Event ignored: " + event);
            }
            
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing webhook: " + e.getMessage());
        }
    }
    
    /**
     * Endpoint to receive commits from VS Code extension.
     * 
     * @param commitData The commit data from VS Code extension
     * @return Response with appropriate status
     */
    @PostMapping("/commit")
    public ResponseEntity<Map<String, Object>> handleCommitWebhook(@RequestBody CommitWebhookDTO commitData) {
        try {
            PendingCommit pendingCommit = commitReviewService.processIncomingCommit(commitData);
            
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                        "success", true,
                        "message", "Commit received and queued for review",
                        "commitId", pendingCommit.getId(),
                        "status", pendingCommit.getStatus()
                    ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "success", false,
                        "message", "Error processing commit: " + e.getMessage()
                    ));
        }
    }
}
