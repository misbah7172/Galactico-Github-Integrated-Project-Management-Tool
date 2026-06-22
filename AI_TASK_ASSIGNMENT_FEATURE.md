# AI-Powered Task Assignment Feature

## Overview

This feature implements an intelligent task assignment system that analyzes team members' contributions, workload, expertise, and timeline adherence to recommend or automatically assign tasks to the most suitable team member.

## Architecture

### Core Components

1. **ContributionScore Entity** (`model/ContributionScore.java`)
   - Persistent snapshot of each team member's performance metrics
   - Stores detailed penalty/bonus breakdown
   - Tracks workload, expertise, and historical performance
   - Enables trend analysis over time

2. **AIAssignmentDecision Entity** (`model/AIAssignmentDecision.java`)
   - Audit trail for every AI decision
   - Records full reasoning for each candidate
   - Enables explainability and transparency

3. **ContributionScoringService** (`service/ContributionScoringService.java`)
   - Computes comprehensive scores for each team member
   - Applies penalty/bonus system
   - Infers expertise from commit history
   - Calculates availability and capacity metrics

4. **AITaskAssignmentService** (`service/AITaskAssignmentService.java`)
   - Main orchestrator for task assignment
   - Implements weighted scoring algorithm
   - Ranks candidates with full explainability
   - Can recommend or automatically assign tasks

5. **AITaskAssignmentController** (`controller/AITaskAssignmentController.java`)
   - REST API endpoints
   - Provides recommendations, scores, and team health metrics

## Scoring Algorithm

### Weighted Components

The AI uses a weighted scoring algorithm with four main components:

1. **Performance (40% weight)**
   - Productivity Score (30%): Commit frequency, lines of code changed
   - Code Quality Score (25%): Approval rate, review feedback
   - On-Time Delivery Score (25%): Tasks completed within sprint deadlines
   - Commit Velocity Score (20%): Recent activity level (last 30 days)

2. **Availability (30% weight)**
   - Current workload (active tasks, story points)
   - Capacity utilization (inverse of availability)
   - Historical velocity (completed tasks)

3. **Expertise Match (20% weight)**
   - Match between task tags and member's primary expertise
   - Inferred from commit message keywords and file types
   - Categories: frontend, backend, testing, documentation, general

4. **Penalty/Bonus (10% weight)**
   - Adjusts score based on recent behavior
   - Normalized to 0-100 scale

### Penalty System

Penalties are subtracted from the base score:

- **Overdue Tasks**: -5 points per late delivery
- **Declined Tasks**: -10 points per task declined by team lead
- **Inactivity**: -3 points per day since last commit (max -30 points)

### Bonus System

Bonuses are added to the base score:

- **Early Delivery**: +2 points per task delivered before sprint end
- **High Code Quality**: +10 points if approval rate > 90%
- **Collaboration**: +5 points for file overlap with other team members (future enhancement)

### Example Calculation

For a team member with:
- Performance score: 85/100
- Availability score: 70/100 (30% capacity utilized)
- Expertise match: 80/100 (strong match)
- Penalties: -15 points (3 late deliveries)
- Bonuses: +10 points (5 early deliveries)

**Calculation:**
```
Base Score = (85 × 0.40) + (70 × 0.30) + (80 × 0.20) + (50 × 0.10)
           = 34 + 21 + 16 + 5
           = 76

Final Score = 76 + 10 (bonus) - 15 (penalty)
            = 71/100
```

## API Endpoints

### 1. Get AI Recommendation

**Endpoint:** `POST /api/ai/assign/recommend`

**Request:**
```json
{
  "taskId": 123,
  "projectId": 1,
  "decisionMode": "RECOMMEND",
  "considerExpertise": true,
  "considerWorkload": true,
  "considerPerformance": true,
  "minCapacityThreshold": 0.2
}
```

**Decision Modes:**
- `RECOMMEND`: Return recommendations without assigning
- `ASSIGN`: Automatically assign to top candidate
- `SUGGEST`: Return recommendations with additional context

**Response:**
```json
{
  "requestId": "uuid-here",
  "taskId": 123,
  "taskTitle": "Implement feature X",
  "decisionMode": "RECOMMEND",
  "selectedRecommendation": {
    "userId": 1,
    "username": "alice",
    "email": "alice@example.com",
    "recommendationScore": 85.5,
    "performanceComponent": 88.0,
    "availabilityComponent": 75.0,
    "expertiseComponent": 80.0,
    "penaltyBonusComponent": 65.0,
    "reasoning": "Alice has high productivity (88.0) and excellent code quality (92% approval). Currently at 25% capacity with 5 active story points. Strong backend expertise matching task requirements. Recently delivered 5 tasks early (+10 bonus). No late deliveries. Net score: 85.5/100.",
    "strengths": [
      "Excellent on-time delivery (95.0%)",
      "High code quality (92.0% approval)",
      "Frequently delivers early (5 times)"
    ],
    "concerns": [],
    "rankPosition": 1,
    "isSelected": true,
    "memberSnapshot": {
      "activeTasks": 3,
      "activeStoryPoints": 5,
      "capacityUtilization": 0.25,
      "historicalVelocity": 25,
      "productivityScore": 88.0,
      "codeQualityScore": 92.0,
      "onTimeDeliveryRate": 95.0,
      "totalCommits": 150,
      "primaryExpertise": "backend",
      "penaltyPoints": 0.0,
      "bonusPoints": 10.0
    }
  },
  "allRecommendations": [
    // ... ranked list of all candidates
  ],
  "teamSummary": {
    "totalMembers": 5,
    "availableMembers": 3,
    "averageCapacityUtilization": 0.65,
    "averageProductivityScore": 72.5,
    "teamNetScore": 68.0,
    "teamHealth": "HEALTHY"
  },
  "timestamp": "2026-06-22T18:30:00"
}
```

### 2. Get Contribution Scores

**Endpoint:** `GET /api/ai/scores/{projectId}`

**Response:** List of all team members with their latest scores and full breakdown.

### 3. Get Score History

**Endpoint:** `GET /api/ai/scores/{projectId}/history/{userId}`

**Response:** Historical score snapshots for trend analysis.

### 4. Get Team Health

**Endpoint:** `GET /api/ai/team-health/{projectId}`

**Response:**
```json
{
  "projectId": 1,
  "projectName": "Galactico",
  "teamSize": 5,
  "averageNetScore": 68.0,
  "averageProductivity": 72.5,
  "averageCodeQuality": 78.0,
  "averageCapacityUtilization": 0.65,
  "availableMembers": 3,
  "atRiskMembers": 1,
  "healthStatus": "WARNING",
  "topPerformers": [
    {
      "userId": 1,
      "username": "alice",
      "netScore": 85.5,
      "primaryExpertise": "backend"
    }
  ]
}
```

**Health Status:**
- `HEALTHY`: Net score ≥70, capacity <70%, no at-risk members
- `WARNING`: Net score ≥50, capacity <85%, ≤1 at-risk member
- `CRITICAL`: Net score <50 or capacity ≥85% or >1 at-risk member

### 5. Recompute Scores

**Endpoint:** `POST /api/ai/scores/{projectId}/recompute`

**Response:** Confirmation that scores were recomputed.

## Key Features

### 1. Explainable AI
Every recommendation includes:
- Detailed reasoning string
- List of strengths and concerns
- Full score breakdown
- Audit trail in database

### 2. Penalty/Bonus System
Encourages good behavior:
- Rewards early delivery and high quality
- Penalizes late delivery, declines, and inactivity
- Transparent point system

### 3. Expertise Matching
Infers member expertise from:
- Commit message keywords (frontend, backend, testing, etc.)
- File type history (future enhancement with FileChangeMetrics)
- Task tag matching

### 4. Capacity Management
Prevents overloading:
- Tracks active tasks and story points
- Calculates capacity utilization
- Filters candidates by minimum capacity threshold

### 5. Audit Trail
Complete decision history:
- Every recommendation is logged
- Full ranking preserved
- Enables post-hoc analysis

## Usage Examples

### Example 1: Recommend Assignee

```bash
curl -X POST http://localhost:8080/api/ai/assign/recommend \
  -H "Content-Type: application/json" \
  -d '{
    "taskId": 123,
    "projectId": 1,
    "decisionMode": "RECOMMEND"
  }'
```

### Example 2: Auto-Assign Task

```bash
curl -X POST http://localhost:8080/api/ai/assign/recommend \
  -H "Content-Type: application/json" \
  -d '{
    "taskId": 123,
    "projectId": 1,
    "decisionMode": "ASSIGN"
  }'
```

### Example 3: View Team Health

```bash
curl http://localhost:8080/api/ai/team-health/1
```

## Testing

The implementation includes comprehensive unit tests in `AITaskAssignmentServiceTest.java`:

1. **testRecommendAssignee** - Verifies correct ranking
2. **testCapacityFiltering** - Tests minimum capacity threshold
3. **testAutoAssignment** - Verifies ASSIGN mode actually assigns
4. **testExpertiseMatching** - Tests expertise-based ranking
5. **testPenaltyBonus** - Verifies penalty/bonus application
6. **testStrengthsConcerns** - Tests identification logic

Run tests:
```bash
mvn test -Dtest=AITaskAssignmentServiceTest
```

## Database Schema

### contribution_scores Table
Stores historical score snapshots with indexes for fast lookup.

### ai_assignment_decisions Table
Audit trail for all AI decisions with request correlation.

## Integration Points

1. **Webhook Integration**: Scores are computed from commit data ingested via webhooks
2. **Notification Service**: Sends notifications when tasks are assigned
3. **Slack Integration**: Posts assignment updates to Slack
4. **Analytics**: Feeds into existing analytics dashboard

## Future Enhancements

1. **Machine Learning Model**: Replace heuristic scoring with trained ML model
2. **File Type Analysis**: Deep integration with FileChangeMetrics for better expertise inference
3. **Collaboration Bonus**: Detect file overlap between team members
4. **Skill Decay**: Reduce expertise score for unused skills
5. **Learning from Feedback**: Adjust weights based on successful assignments
6. **Predictive Analytics**: Predict task completion time based on assignee's history

## Configuration

Weights and thresholds are currently hardcoded but can be made configurable:

```java
// In AITaskAssignmentService.java
double performanceWeight = 0.40;
double availabilityWeight = 0.30;
double expertiseWeight = 0.20;
double penaltyBonusWeight = 0.10;
```

## Performance Considerations

- Scores are computed on-demand (can be cached)
- Database indexes on frequently queried columns
- Lazy loading for related entities
- Batch processing for team-wide scoring

## Security

- All endpoints require authentication
- Team lead role required for ASSIGN mode
- Audit trail prevents unauthorized assignments
- Sensitive data (emails) handled appropriately

## Monitoring

Key metrics to monitor:
- Average recommendation score
- Team health distribution
- Assignment acceptance rate
- Time to assignment
- Penalty/bonus distribution

## Troubleshooting

**Issue: No candidates returned**
- Check that project has team members
- Verify members have contributor stats
- Lower minCapacityThreshold

**Issue: Wrong candidate selected**
- Review penalty/bonus breakdown
- Check expertise matching logic
- Verify capacity calculations

**Issue: Scores not updating**
- Recompute scores via API
- Check webhook ingestion
- Verify commit data exists

## Support

For issues or questions:
1. Check audit trail in ai_assignment_decisions table
2. Review reasoning strings in responses
3. Examine contribution_scores for trends
4. Contact development team

## License

Part of Galactico - GitHub Integrated Project Management Tool
