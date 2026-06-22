# Galactico - GitHub-Integrated Project Management Tool

## Complete Build & Feature Guide

A comprehensive guide for building a project management platform tightly integrated with GitHub. This document covers every feature, architecture decision, database schema, UI design system, and deployment setup so that the entire application can be reproduced.

---

## Table of Contents

1. [Technology Stack](#technology-stack)
2. [Project Structure](#project-structure)
3. [Quick Start (Docker)](#quick-start-docker)
4. [Database Schema](#database-schema)
5. [Authentication & Security](#authentication--security)
6. [Backend Architecture](#backend-architecture)
7. [Frontend & UI Design System](#frontend--ui-design-system)
8. [Feature Breakdown](#feature-breakdown)
9. [REST API Endpoints](#rest-api-endpoints)
10. [VS Code Extension](#vs-code-extension)
11. [Configuration Reference](#configuration-reference)
12. [Commit Message Format](#commit-message-format)
13. [Azure Deployment](#azure-deployment)
14. [Key Design Decisions](#key-design-decisions)

---

## Technology Stack

| Layer               | Technology                       |
|---------------------|----------------------------------|
| Backend Framework   | Spring Boot 3.1.5                |
| Language            | Java 17                          |
| Database            | PostgreSQL 16                    |
| Authentication      | GitHub OAuth2 (Spring Security)  |
| Template Engine     | Thymeleaf 3.1                    |
| Frontend            | Vanilla JS, CSS3 (custom design) |
| Build Tool          | Maven 3.9.4                      |
| Container           | Docker + Docker Compose          |
| JVM Runtime         | Amazon Corretto 17               |
| Caching             | Caffeine (in-memory, 18 caches)  |
| Extension           | VS Code Extension API (TypeScript) |
| Testing             | JUnit 5, Mockito, H2 (test DB)  |
| Code Coverage       | JaCoCo                           |

---

## Project Structure

```
Galactico/
├── pom.xml                          # Maven build configuration
├── Dockerfile                       # Multi-stage production build
├── Dockerfile.dev                   # Development build with hot reload
├── docker-compose.yml               # Docker services (app + PostgreSQL)
├── .env                             # Environment variables (not committed)
├── start.bat / start.sh             # Launch scripts
├── create_database.sql              # Manual DB setup
├── src/
│   ├── main/
│   │   ├── java/com/autotrack/
│   │   │   ├── AutoTrackApplication.java     # Spring Boot entry point (@EnableCaching, @EnableAsync, @EnableScheduling)
│   │   │   ├── config/                       # Security, OAuth2, Cache, Web config
│   │   │   ├── controller/                   # MVC + REST controllers
│   │   │   ├── dto/                          # Data Transfer Objects
│   │   │   ├── model/                        # JPA entities & enums
│   │   │   ├── repository/                   # Spring Data JPA repos
│   │   │   ├── service/                      # Business logic
│   │   │   └── util/                         # Utilities (ApiResponse)
│   │   └── resources/
│   │       ├── application.properties        # App configuration
│   │       ├── application-postgresql.properties
│   │       ├── schema.sql                    # Database schema
│   │       ├── data.sql                      # Seed data (roles, statuses)
│   │       ├── db/migration/                 # Flyway-style migration scripts
│   │       ├── static/
│   │       │   ├── css/                      # Stylesheets
│   │       │   │   ├── main.css              # Core design system (~3800 lines)
│   │       │   │   ├── kanban.css            # Kanban board styles
│   │       │   │   └── ...                   # Module-specific CSS
│   │       │   ├── js/                       # JavaScript
│   │       │   │   ├── kanban.js             # Drag-and-drop, task updates
│   │       │   │   ├── modern-ui.js          # UI interactions
│   │       │   │   └── ...
│   │       │   └── images/                   # Static images
│   │       └── templates/                    # Thymeleaf HTML templates
│   │           ├── fragments/                # Reusable header, footer
│   │           ├── project/                  # Project CRUD pages
│   │           ├── task/                     # Task management pages
│   │           ├── team/                     # Team management pages
│   │           ├── cicd/                     # CI/CD configuration pages
│   │           ├── email/                    # Email templates
│   │           ├── kanban.html               # Kanban board
│   │           ├── dashboard.html            # Main dashboard
│   │           ├── profile.html              # User profile
│   │           ├── notifications.html        # Notification center
│   │           └── ...
│   └── test/                                 # Unit & integration tests
└── Extension/                                # VS Code extension
    ├── package.json                          # Extension manifest
    ├── tsconfig.json
    └── src/
        ├── extension.ts                      # Main entry point
        ├── githubAuth.ts                     # OAuth flow
        ├── galacticoService.ts               # Backend API client
        ├── galacticoCommitService.ts          # Commit creation
        └── ...
```

---

## Quick Start (Docker)

### Prerequisites
- Docker Desktop installed
- GitHub OAuth App created (Settings → Developer Settings → OAuth Apps)
  - Homepage URL: `http://localhost:5000`
  - Callback URL: `http://localhost:5000/login/oauth2/code/github`

### 1. Create `.env` file in project root

```env
# Database
DB_NAME=galactico
DB_USERNAME=galactico
DB_PASSWORD=galactico_secret

# GitHub OAuth (from your GitHub OAuth App)
GITHUB_CLIENT_ID=your_github_client_id
GITHUB_CLIENT_SECRET=your_github_client_secret

# Security
JWT_SECRET=your_jwt_secret_key_minimum_32_characters_long
ENCRYPTION_KEY=your_encryption_key_32_chars_here

# Application URLs
APP_BASE_URL=http://localhost:5000
BASE_URL=http://localhost:5000
OAUTH_REDIRECT_URI=http://localhost:5000/login/oauth2/code/github

# Email (optional - Gmail SMTP)
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=your_email@gmail.com
SPRING_MAIL_PASSWORD=your_app_password
```

### 2. Build and run

```bash
docker-compose up --build -d
```

### 3. Access the application

- **Web App**: http://localhost:5000
- **PostgreSQL**: localhost:5433 (external port)

### Docker Services

| Service         | Container Name     | Port        | Image                |
|-----------------|--------------------|-------------|----------------------|
| PostgreSQL      | galactico-postgres | 5433:5432   | postgres:16-alpine   |
| Spring Boot App | galactico-app      | 5000:5000   | Custom (Dockerfile)  |

### Docker Architecture

- **Production Dockerfile**: Multi-stage build - Maven builds JAR, then Amazon Corretto 17 Alpine runs it with a non-root user, health checks, and curl/bash/tzdata installed.
- **Development Dockerfile**: Maven with hot reload (`spring-boot:run`), debug port 5005 open, development tools (git, vim, htop).
- **PostgreSQL**: Alpine image with volume persistence, health check via `pg_isready`, restart policy.
- **Server Binding**: `SERVER_ADDRESS=0.0.0.0` is required for Docker networking.

---

## Database Schema

### Entity Relationship Overview

```
users ──────────────┬──── user_roles
                    ├──── team_members (junction) ──── teams
                    ├──── team_invitations
                    ├──── tasks (assignee)
                    ├──── notifications
                    ├──── messages
                    └──── task_history

teams ──────────────┬──── projects
                    ├──── team_members
                    ├──── team_invitations
                    ├──── messages
                    └──── team_member_removals

projects ───────────┬──── tasks
                    ├──── sprints
                    ├──── backlog_items
                    ├──── commits
                    └──── task_history

tasks ──────────────┬──── commits
                    ├──── notifications
                    └──── task_history

sprints ────────────┬──── backlog_items
                    └──── task_history

messages ───────────── message_reactions
```

### Core Tables

#### `users`
```sql
CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    github_id VARCHAR(255) UNIQUE NOT NULL,
    nickname VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    avatar_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `user_roles`
```sql
CREATE TABLE user_roles (
    user_id BIGINT REFERENCES users(id),
    role VARCHAR(50) NOT NULL  -- TEAM_LEAD, MEMBER
);
```

#### `teams`
```sql
CREATE TABLE teams (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT REFERENCES users(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    github_organization_url VARCHAR(500),
    deleted_at TIMESTAMP,           -- Soft delete
    deleted_by_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `team_members` (Junction Table)
```sql
CREATE TABLE team_members (
    team_id BIGINT REFERENCES teams(id),
    user_id BIGINT REFERENCES users(id),
    PRIMARY KEY (team_id, user_id)
);
```

#### `team_invitations`
```sql
CREATE TABLE team_invitations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT REFERENCES teams(id),
    inviter_id BIGINT REFERENCES users(id),
    invitee_id BIGINT REFERENCES users(id),
    invitee_github_url VARCHAR(500),  -- Invite by GitHub URL
    status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, ACCEPTED, REJECTED
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    responded_at TIMESTAMP
);
```

#### `projects`
```sql
CREATE TABLE projects (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT REFERENCES teams(id),
    name VARCHAR(255) NOT NULL,
    github_repo_id VARCHAR(255),
    github_repo_url VARCHAR(500),
    github_access_token VARCHAR(500),
    webhook_secret VARCHAR(255),
    deleted_at TIMESTAMP,
    deleted_by_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `tasks`
```sql
CREATE TABLE tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT REFERENCES projects(id),
    feature_code VARCHAR(100),       -- e.g., "Feature01"
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(20) DEFAULT 'TODO',  -- TODO, IN_PROGRESS, DONE
    assignee_id BIGINT REFERENCES users(id),
    github_issue_url VARCHAR(500),
    milestone VARCHAR(255),
    tags VARCHAR(500),
    declined_by_id BIGINT REFERENCES users(id),
    declined_at TIMESTAMP,
    decline_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `sprints`
```sql
CREATE TABLE sprints (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT REFERENCES projects(id),
    name VARCHAR(255) NOT NULL,
    sprint_goal TEXT,
    status VARCHAR(20) DEFAULT 'PLANNED',  -- PLANNED, ACTIVE, COMPLETED
    start_date DATE,
    end_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `backlog_items`
```sql
CREATE TABLE backlog_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT REFERENCES projects(id),
    sprint_id BIGINT REFERENCES sprints(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    user_story TEXT,
    acceptance_criteria TEXT,
    issue_type VARCHAR(20) DEFAULT 'TASK',  -- STORY, TASK, BUG, EPIC
    priority_level VARCHAR(20) DEFAULT 'MEDIUM',  -- CRITICAL, HIGH, MEDIUM, LOW
    priority_rank INTEGER,
    story_points INTEGER DEFAULT 0,
    business_value INTEGER,
    backlog_status VARCHAR(30) DEFAULT 'PRODUCT_BACKLOG',
        -- PRODUCT_BACKLOG, SPRINT_BACKLOG, IN_PROGRESS, DONE, REMOVED
    assignee_id BIGINT REFERENCES users(id),
    created_by_id BIGINT REFERENCES users(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `commits`
```sql
CREATE TABLE commits (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT REFERENCES projects(id),
    task_id BIGINT REFERENCES tasks(id),
    sha VARCHAR(255) NOT NULL,
    message TEXT,
    author_name VARCHAR(255),
    author_email VARCHAR(255),
    github_url VARCHAR(500),
    committed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `notifications`
```sql
CREATE TABLE notifications (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT REFERENCES users(id),
    task_id BIGINT REFERENCES tasks(id),
    message TEXT,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `messages` & `message_reactions`
```sql
CREATE TABLE messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT REFERENCES teams(id),
    sender_id BIGINT REFERENCES users(id),
    content TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE message_reactions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    message_id BIGINT REFERENCES messages(id),
    user_id BIGINT REFERENCES users(id),
    emoji VARCHAR(10),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (message_id, user_id, emoji)
);
```

#### `task_history`
```sql
CREATE TABLE task_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT REFERENCES tasks(id),
    user_id BIGINT REFERENCES users(id),
    project_id BIGINT REFERENCES projects(id),
    sprint_id BIGINT REFERENCES sprints(id),
    action_type VARCHAR(30),
        -- TASK_CREATED, FIELD_UPDATED, STATUS_CHANGED, ASSIGNED, COMMENT_ADDED, etc.
    field_name VARCHAR(100),
    old_value TEXT,
    new_value TEXT,
    description TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### `team_member_removals`
```sql
CREATE TABLE team_member_removals (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT REFERENCES teams(id),
    user_id BIGINT REFERENCES users(id),
    removed_by_id BIGINT REFERENCES users(id),
    removal_type VARCHAR(20),  -- KICKED, LEFT
    removal_reason TEXT,
    contributions_removed BOOLEAN DEFAULT FALSE,
    removed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Seed Data (`data.sql`)

```sql
-- Roles
INSERT INTO roles (name, description) VALUES
('ADMIN', 'System Administrator'),
('TEAM_LEAD', 'Team Leader'),
('MEMBER', 'Regular team member'),
('VIEWER', 'Read-only access');

-- Task Statuses
INSERT INTO task_statuses (name, color) VALUES
('TODO', '#6c757d'),
('IN_PROGRESS', '#007bff'),
('IN_REVIEW', '#ffc107'),
('DONE', '#28a745'),
('BLOCKED', '#dc3545'),
('CANCELLED', '#6f42c1');
```

---

## Authentication & Security

### GitHub OAuth2 Flow

1. User clicks "Login with GitHub" on the login page
2. Spring Security redirects to GitHub's authorization URL
3. User authorizes the app on GitHub
4. GitHub redirects back with an authorization code
5. Spring Security exchanges the code for an access token
6. `UserService` creates/updates the user record from GitHub profile data
7. Session is established with Spring Security context

### Security Configuration (`SecurityConfig.java`)

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login", "/error", "/css/**", "/js/**", 
                    "/images/**", "/webjars/**").permitAll()
                .requestMatchers("/webhook/github", "/webhook/commit", 
                    "/h2-console/**", "/api/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo.userService(userService))
                .successHandler(extensionOAuthSuccessHandler)
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?logout=true")
                .deleteCookies("JSESSIONID")
                .clearAuthentication(true)
                .invalidateHttpSession(true)
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/webhook/github", "/webhook/commit",
                    "/h2-console/**", "/api/**")
            )
            .build();
    }
}
```

### Key Security Features
- **OAuth2 via GitHub**: No password storage; relies on GitHub for authentication
- **CSRF Protection**: Enabled on all endpoints except webhook receivers; uses `CookieCsrfTokenRepository` with meta tags for AJAX requests
- **Role-Based Access**: `TEAM_LEAD` vs `MEMBER` roles control team management permissions; authorities stored as `ROLE_TEAM_LEAD`, `ROLE_MEMBER` (use `hasRole()` not `hasAuthority()` in `@PreAuthorize`)
- **Webhook Signature Validation**: GitHub webhook payloads verified with HMAC-SHA256
- **Project Secrets Encryption**: API keys/tokens encrypted before database storage
- **Persistent Sessions**: 7-day session timeout with persistent cookies (`max-age=604800`) that survive browser restarts. In-memory session store (not JDBC — avoids OAuth2 serialization issues)
- **Secure Cookies**: `HttpOnly=true`, `Secure=true` (HTTPS only)
- **Non-Root Docker User**: Application runs as `autotrack:1001` in production Docker
- **Method-Level Security**: `@EnableMethodSecurity` with `@PreAuthorize` annotations on controllers
- **Custom Error Controller**: Handles 403, 404, 500 errors gracefully; redirects non-error status codes (200-399) to dashboard to prevent "Error 200" rendering artifacts

---

## Backend Architecture

### Layered Architecture

```
HTTP Request
    │
    ▼
┌──────────────────────────────────────┐
│  Controllers (MVC + REST)            │  Route handling, request validation
│  - Page Controllers → Thymeleaf      │
│  - API Controllers → JSON            │
└─────────────┬────────────────────────┘
              │
              ▼
┌──────────────────────────────────────┐
│  Services                            │  Business logic, orchestration
│  - UserService, ProjectService       │
│  - TaskService, SprintService        │
│  - CommitService, WebhookService     │
│  - NotificationService              │
│  - CICDGeneratorService             │
└─────────────┬────────────────────────┘
              │
              ▼
┌──────────────────────────────────────┐
│  Repositories (Spring Data JPA)      │  Database access
│  - TaskRepository, UserRepository    │
│  - Custom JPQL queries               │
└─────────────┬────────────────────────┘
              │
              ▼
┌──────────────────────────────────────┐
│  PostgreSQL Database                 │
└──────────────────────────────────────┘
```

### Controllers

**Page Controllers** (return Thymeleaf views):
| Controller                  | Path Pattern              | Purpose                      |
|-----------------------------|---------------------------|------------------------------|
| `HomeController`            | `/`                       | Landing page                 |
| `AuthController`            | `/login`                  | Login page                   |
| `DashboardController`       | `/dashboard`              | Main dashboard               |
| `ProjectController`         | `/projects/**`            | Project CRUD + kanban        |
| `TaskController`            | `/tasks/**`               | Task management + kanban     |
| `TeamController`            | `/teams/**`               | Team management              |
| `TeamInvitationController`  | `/teams/*/invitations/**` | Invitation workflows         |
| `SprintController`          | `/sprints/**`             | Sprint views                 |
| `BacklogViewController`     | `/backlog/**`             | Product backlog              |
| `ProfileController`         | `/profile`                | User profile                 |
| `NotificationController`    | `/notifications`          | Notification center          |
| `CommitReviewPageController`| `/commit-review`          | Commit review                |
| `AnalyticsController`       | `/analytics/**`           | Analytics dashboards         |
| `CICDController`            | `/cicd/**`                | CI/CD configuration          |
| `MessageController`         | `/messages/**`            | Team chat                    |
| `TimelineController`        | `/timeline/**`            | Project timeline             |

**REST API Controllers** (return JSON):
| Controller                    | Base Path           | Purpose                |
|-------------------------------|---------------------|------------------------|
| `AuthApiController`           | `/api/auth`         | Auth status            |
| `ProjectApiController`        | `/api/projects`     | Project data           |
| `SprintApiController`         | `/api/v1/sprints`   | Sprint CRUD            |
| `TaskHistoryApiController`    | `/api/timeline`     | Activity timeline      |
| `UserApiController`           | `/api/users`        | User data              |
| `UserDashboardApiController`  | `/api/dashboard`    | Dashboard stats        |
| `WebhookController`           | `/api/webhooks`     | GitHub webhooks        |
| `HealthApiController`         | `/api/health`       | Health checks          |

### Services Overview

| Service                        | Responsibility                                       |
|--------------------------------|------------------------------------------------------|
| `UserService`                  | User CRUD, GitHub profile sync, role management      |
| `ProjectService`               | Project CRUD, GitHub repo linking, webhook setup      |
| `TaskService`                  | Task CRUD, status changes, assignment, decline flow   |
| `TeamService`                  | Team CRUD, member management, soft delete            |
| `SprintService`                | Sprint planning, activation, completion tracking     |
| `BacklogService`               | Backlog items, priority ranking, sprint assignment   |
| `CommitService`                | Commit storage, retrieval, statistics                |
| `CommitParserService`          | Parse "Feature : desc -> user -> status" format      |
| `CommitReviewService`          | Pending/approved commit workflow                     |
| `GitHubService`                | GitHub API calls (repos, commits, issues)            |
| `GitHubCommitAnalysisService`  | Analyze commit patterns and metrics                  |
| `WebhookService`               | Process GitHub webhook events (push, PR, issues)     |
| `NotificationService`          | Create and deliver user notifications                |
| `MessageService`               | Team chat messages and emoji reactions                |
| `EmailService`                 | SMTP email delivery (welcome, invitations, tasks)    |
| `SlackService`                 | Slack webhook notifications                          |
| `CICDGeneratorService`         | Generate CI/CD pipeline configs                      |
| `MultiLanguagePipelineGenerator` | Templates for Java, Python, Node, Go, etc.         |
| `ProjectSecretService`         | Encrypted secrets management                         |
| `TaskHistoryService`           | Audit log for task changes                           |
| `CommitStatisticsService`      | Commit analytics and contributor metrics             |
| `LanguageAnalysisService`      | Code language detection from commits                 |
| `AnalyticsCacheService`        | Cache analytics computations                         |
| `TaskReminderService`          | Scheduled deadline reminders                         |
| `SprintScheduler`              | Scheduled sprint auto-completion                     |
| `BackgroundDataProcessingService` | Async data processing                             |
| `ExtensionAuthService`         | VS Code extension token management                  |

### Caching Architecture

The application uses **Caffeine** as an in-memory cache provider, configured via `CacheConfig.java`:

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        // 18 named caches, each: maxSize=500, expireAfterWrite=5min
    }
}
```

**Named Caches** (18 total):
| Cache Name | Used By | Purpose |
|---|---|---|
| `projects` | ProjectService | Project lookups |
| `userProjects` | ProjectService | User's project list |
| `tasks` | TaskService | Task lookups |
| `projectTasks` | TaskService | Tasks by project |
| `teams` | TeamService | Team lookups |
| `userTeams` | TeamService | Cache eviction targets (not used for reads — JOIN FETCH queries bypass cache to avoid lazy loading issues) |
| `sprints`, `projectSprints` | SprintService | Sprint data |
| `backlogItems` | BacklogService | Backlog data |
| `commitStats` | CommitStatisticsService | Commit analytics |
| `analytics` | AnalyticsCacheService | Dashboard analytics |
| Other caches | Various services | Notifications, messages, etc. |

**Important Pattern**: Team queries (`getTeamsByUser`, `getTeamById`) are **NOT cached** because they use `JOIN FETCH` to eagerly load `members` and `projects` collections. Caching detached JPA entities with lazy proxies causes `LazyInitializationException` on cache hits.

### Entity Design: Sets vs Lists

The `Team` entity uses `Set` (not `List`) for `@ManyToMany` and `@OneToMany` relationships:

```java
@ManyToMany
private Set<User> members = new HashSet<>();

@OneToMany(mappedBy = "team")
private Set<Project> projects = new HashSet<>();
```

**Why Sets**: Hibernate cannot `JOIN FETCH` two `List` collections simultaneously — it throws `MultipleBagFetchException` because `List` maps to a Hibernate "bag" type. Using `Set` avoids this limitation and allows fetching both collections in a single query.

### HikariCP Connection Pool

```properties
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.maximum-pool-size=15
spring.datasource.hikari.idle-timeout=30000
spring.datasource.hikari.max-lifetime=600000
spring.datasource.hikari.connection-timeout=20000
```

---

## Frontend & UI Design System

### Dark Theme Design

The entire UI uses a midnight-blue dark theme defined in `main.css` (~3800 lines).

### Color Palette (CSS Variables)

```css
:root {
    /* Backgrounds */
    --midnight-blue: #0f172a;    /* Page background */
    --slate-900: #0f172a;        /* Dark surfaces */
    --slate-800: #1e293b;        /* Cards, panels */
    --slate-700: #334155;        /* Input backgrounds */
    --slate-600: #475569;        /* Subtle text */

    /* Accent Colors */
    --neon-cyan: #06b6d4;        /* Primary accent */
    --indigo: #6366f1;           /* Secondary accent */
    --soft-violet: #8b5cf6;      /* Tertiary accent */

    /* Text */
    --text-primary: #f8fafc;     /* Main text (near white) */
    --text-secondary: #cbd5e1;   /* Secondary text */

    /* Borders */
    --border-color: #1e293b;     /* Default borders */

    /* Status Colors */
    --success: #10b981;          /* Green */
    --warning: #f59e0b;          /* Amber */
    --danger: #ef4444;           /* Red */
    --info: #3b82f6;             /* Blue */
}
```

### Typography

- **Font Family**: `'Inter', system-ui, -apple-system, sans-serif`
- **Headings**: 600-700 weight, `var(--text-primary)`
- **Body**: 400 weight, `var(--text-secondary)`

### Component Library (CSS Classes)

| Component         | Classes                        | Description                              |
|-------------------|--------------------------------|------------------------------------------|
| Buttons           | `.btn`, `.btn-primary`, `.btn-secondary`, `.btn-danger` | Gradient backgrounds, hover lift |
| Cards             | `.card`, `.stat-card`, `.section-card` | Dark surface with subtle borders  |
| Badges            | `.badge`, `.badge-primary`, `.badge-secondary` | Status indicators               |
| Forms             | `.form-group`, `.form-control` | Dark inputs with focus glow              |
| Tables            | `.table`                       | Dark rows with hover highlight           |
| Modal             | `.modal-overlay`, `.modal-content.jira-modal` | Backdrop blur, dark surface     |
| Side Panel        | `.side-panel`                  | Slide-in panel for details               |
| Empty States      | `.empty-state`, `.empty-icon`  | Centered placeholder content             |
| Navigation        | In header fragment             | Dark navbar with active indicators       |
| Footer            | In footer fragment             | Branded footer with SVG logo             |

### Icons

- **Font Awesome 6.0.0** via CDN for general icons
- **Inline SVGs** for custom icons (GitHub, profile, etc.)

### Page Templates

All pages use Thymeleaf fragments:
```html
<div th:replace="~{fragments/header :: header}"></div>
<!-- page content -->
<div th:replace="~{fragments/footer :: footer}"></div>
```

### Header Navigation

- Mobile-responsive hamburger menu
- Active page highlighting via current request URI comparison (Thymeleaf 3.1 compatible — does NOT use `#httpServletRequest`)
- Notification badge with unread count (AJAX poll every 30 seconds to `/team-invitations/pending/count`)
- User avatar dropdown

### Kanban Board Design

The kanban board (`kanban.html`) is the most complex page with:
- **3-column layout**: TODO, IN_PROGRESS, DONE with task counts
- **Drag-and-drop**: Handled by `kanban.js` with HTML5 Drag API
- **Side panels**: Sprint Management, Product Backlog, Activity Timeline (slide-in from right)
- **Modals**: Create Sprint, Create Backlog Item (Jira-style dark forms)
- **Real-time updates**: Auto-refresh every 60 seconds (skipped during drag or panel open)
- **Task cards**: Show title, assignee avatar, priority badge, feature code
- **Sprint progress**: Calculated from actual task counts on the board
- **Dark-themed notifications**: Inline toast notifications for success/error

---

## Feature Breakdown

### 1. GitHub OAuth2 Login
- Single-click login via GitHub with loading overlay animation (\"Connecting to GitHub...\" spinner)
- Auto-creates user profile from GitHub data (nickname, email, avatar)
- Secure token management
- Persistent session authentication (7-day timeout, survives browser restarts via persistent cookies)

### 2. Dashboard
- **Quick Access Cards** (8 cards):
  - My Teams, My Projects, Kanban Board, Create Team
  - Create Project, Notifications, Profile, Commit Review
- Each card links to its respective feature page
- Stats overview (teams, projects, tasks, commits)

### 3. Team Management
- **Create Team**: Name, description, GitHub organization URL
- **Invite Members**: By GitHub username URL or search
- **Member Roles**: Team Lead (full control) vs Member (limited)
- **Team Chat**: Real-time messaging with emoji reactions
- **Member Removal**: Kick members (with reason) or leave team
- **Soft Delete**: Teams can be soft-deleted, preserving history
- **Invitation Workflow**: Pending → Accepted/Rejected states

### 4. Project Management
- **Create Project**: Name, linked GitHub repository
- **GitHub Integration**: Auto-sync repo URL, issues, commits
- **Webhook Setup**: Automatic GitHub webhook configuration
- **Project Secrets**: Encrypted storage for API keys/tokens
- **Team Assignment**: Assign projects to teams
- **Analytics Dashboard**: Commit stats, contributor metrics, language breakdown
- **Soft Delete**: Projects can be soft-deleted

### 5. Kanban Board
- **Three Columns**: TODO → IN_PROGRESS → DONE
- **Drag-and-Drop**: Move tasks between columns (updates status via API)
- **Task Cards**: Title, description, assignee, priority, feature code
- **Task Decline**: Assigned users can decline tasks with a reason
- **Quick Actions**: Edit, delete, reassign from card context menu
- **Filtered View**: Tasks filtered by project
- **Task Count Badges**: Per-column task counters

### 6. Sprint Management
- **Create Sprints**: Name, goal, start/end dates
- **Sprint States**: PLANNED → ACTIVE → COMPLETED
- **Active Sprint**: One active sprint per project
- **Sprint Progress**: Visual progress bar based on task completion
- **Remaining Days**: Countdown to sprint end date
- **Sprint Statistics**: Velocity, points completed, burndown

### 7. Product Backlog
- **Backlog Items**: Title, description, user story, acceptance criteria
- **Issue Types**: Story, Task, Bug, Epic (with icons)
- **Priority Levels**: Critical, High, Medium, Low (color-coded badges)
- **Story Points**: Estimation for sprint planning
- **Business Value**: Prioritization metric
- **Sprint Assignment**: Move items from product backlog to sprint backlog
- **Status Flow**: PRODUCT_BACKLOG → SPRINT_BACKLOG → IN_PROGRESS → DONE

### 8. Commit Tracking
- **Auto-Parse Commits**: Extract task references from commit messages
- **Commit Format**: `"Feature01 : task description -> Username -> status"`
- **Status Keywords**: `todo`, `in-progress`, `done`
- **Commit Review Workflow**: Pending → Approved flow for team leads
- **Commit Statistics**: Per-user, per-project analytics
- **Language Detection**: Analyze file extensions in commits
- **GitHub Webhook**: Real-time commit ingestion via webhook events

### 9. Activity Timeline
- **Project Timeline**: Chronological feed of all project activity
- **Action Types**: Task created, status changed, assigned, priority updated, commented, sprint assigned
- **Grouped by Date**: Activities organized by calendar date
- **Color-Coded Icons**: Each action type has a distinct color
- **User Attribution**: Shows who performed each action

### 10. Notifications
- **Task Assignments**: Notified when assigned to a task
- **Status Changes**: Notified of task status updates
- **Team Invitations**: Notified of pending invitations
- **Read/Unread**: Mark notifications as read
- **Badge Counter**: Unread count in navigation header

### 11. CI/CD Pipeline Generation
- **Multi-Language Support**: Java, Python, Node.js, Go, Ruby, .NET, Rust, PHP
- **GitHub Actions**: Generates `.github/workflows/*.yml` files
- **Pipeline Templates**: Build, test, lint, deploy stages
- **Custom Configuration**: Adjust build commands, test frameworks, deployment targets
- **Secrets Integration**: Use project secrets in pipeline configs

### 12. User Profile
- **GitHub Sync**: Avatar, nickname, email from GitHub
- **Dark Theme**: Glassmorphism card design with `backdrop-filter: blur(20px)`, gradient backgrounds
- **Statistics**: Teams count, projects count, tasks assigned, commits tracked
- **Team List**: Quick links to user's teams
- **Project List**: Recent projects with "auto-tracking commits" indicator
- **Settings**: Commit message format guide, username reference, status keywords
- **CSRF Protection**: Meta tags + token headers in fetch() calls for profile updates

### 13. Team Chat / Messaging
- **Team Channels**: Each team has a message board
- **Message Posting**: Text content with sender attribution
- **Emoji Reactions**: React to messages (unique per user per emoji)
- **Message History**: Scrollable message feed

### 14. Email Notifications
- **Welcome Email**: When user first joins
- **Team Invitation**: When invited to a team
- **Task Assignment**: When assigned to a task
- **Customizable**: Thymeleaf HTML email templates

---

## REST API Endpoints

### Authentication (Web)
| Method | Path                | Description           |
|--------|---------------------|-----------------------|
| GET    | `/api/auth/status`  | Get auth status       |
| POST   | `/api/auth/github`  | GitHub authentication |

### Extension Authentication (Token-based)
| Method | Path                      | Description                        |
|--------|---------------------------|------------------------------------|
| GET    | `/api/auth/login-url`     | Get OAuth login URL for extension  |
| GET    | `/api/auth/token-display` | Token display page after OAuth     |
| GET    | `/api/auth/me`            | Validate token, get user info      |
| POST   | `/api/auth/logout`        | Revoke extension token             |
| GET    | `/api/auth/sessions`      | G--et active extension sessions      |

### User Dashboard API (Extension)
| Method | Path                      | Description                    |
|--------|---------------------------|--------------------------------|
| GET    | `/api/user/tasks`         | Get user's assigned tasks      |
| GET    | `/api/user/contributions` | Get contribution statistics    |
| GET    | `/api/user/projects`      | Get user's projects            |
| GET    | `/api/user/me`            | Get current user info          |

### Projects
| Method | Path                          | Description              |
|--------|-------------------------------|--------------------------|
| GET    | `/api/projects/user-projects` | Get user's projects      |

### Sprints
| Method | Path                                        | Description            |
|--------|---------------------------------------------|------------------------|
| GET    | `/api/v1/sprints/project/{projectId}`       | List sprints (paginated) |
| GET    | `/api/v1/sprints/project/{projectId}/active`| Get active sprint      |
| POST   | `/api/v1/sprints`                           | Create sprint          |
| PUT    | `/api/v1/sprints/{id}`                      | Update sprint          |
| DELETE | `/api/v1/sprints/{id}`                      | Delete sprint          |

### Backlog
| Method | Path                                   | Description              |
|--------|----------------------------------------|--------------------------|
| GET    | `/api/backlog/project/{id}?page=&size=`| Get backlog items        |
| POST   | `/api/backlog`                         | Create backlog item      |
| PUT    | `/api/backlog/{id}`                    | Update backlog item      |
| DELETE | `/api/backlog/{id}`                    | Delete backlog item      |

### Tasks
| Method | Path                       | Description            |
|--------|----------------------------|------------------------|
| POST   | `/tasks/{id}/status`       | Update task status     |
| POST   | `/api/tasks/{id}/decline`  | Decline assigned task  |
| DELETE | `/tasks/{id}`              | Delete task            |

### Timeline
| Method | Path                               | Description             |
|--------|------------------------------------|-------------------------|
| GET    | `/api/timeline/task/{taskId}`      | Get task history        |
| GET    | `/api/timeline/project/{projectId}`| Get project timeline    |

### Webhooks
| Method | Path                      | Description             |
|--------|---------------------------|-------------------------|
| POST   | `/api/webhooks/github`    | GitHub webhook receiver |

### Dashboard
| Method | Path                  | Description           |
|--------|-----------------------|-----------------------|
| GET    | `/api/dashboard/stats`| Dashboard statistics  |

---

## VS Code Extension

### Overview
The VS Code Extension (`Extension/`) provides IDE-integrated project management with token-based authentication, task dashboard, CI/CD configuration, and Git automation.

> **Full installation and usage guide:** See [`EXTENSION_GUIDE.md`](EXTENSION_GUIDE.md)

### Architecture

| File                         | Purpose                                    |
|------------------------------|--------------------------------------------|
| `extension.ts`               | Main activation, 25+ command registrations |
| `config.ts`                  | Backend URL configuration via VS Code settings |
| `galacticoAuthService.ts`    | Token-based auth (`gal_` prefix tokens)    |
| `githubAuth.ts`              | GitHub API auth via VS Code session + Octokit |
| `galacticoService.ts`        | Task selection, smart commit workflow      |
| `dashboardService.ts`        | VS Code webview task dashboard panel       |
| `cicdService.ts`             | CI/CD pipeline generation from IDE         |
| `autoTrackCommitService.ts`  | Sprint/backlog commit automation           |
| `githubTreeProvider.ts`      | Activity Bar tree view (4 sections)        |

### Authentication Flow
1. Extension opens browser for GitHub OAuth login
2. `ExtensionOAuthSuccessHandler` redirects to `/api/auth/token-display`
3. `ExtensionAuthService` generates `gal_` prefixed token (24h expiry)
4. User copies token and pastes into VS Code input
5. Token validated via `/api/auth/me` and stored in VS Code Secrets API
6. All subsequent API calls use `Authorization: Bearer <token>`

### Key Features
- **Task Dashboard:** Webview panel with stats, task cards, commit history, filtering, auto-refresh
- **Git Automation:** Add, commit, push, branch selection, collaborator verification
- **CI/CD Configuration:** Generate Node.js/Python/React/Docker/Java pipelines
- **AutoTrack Commits:** Structured commits with feature codes, sprint/backlog tracking
- **Smart Login:** Single command to check status, login, or open dashboard

### Configuration
```json
{
    "galactico.baseUrl": "https://galactico-app.azurewebsites.net",
    "galactico.debug": false
}
```

---

## Configuration Reference

### `application.properties`

```properties
# Server
server.port=${SERVER_PORT:5000}
server.address=${SERVER_ADDRESS:localhost}
server.forward-headers-strategy=NATIVE

# Static Resources & Performance
spring.web.resources.static-locations=classpath:/static/
spring.web.resources.cache.cachecontrol.max-age=31536000
spring.web.resources.cache.cachecontrol.cache-public=true
spring.web.resources.cache.cachecontrol.must-revalidate=true
spring.web.resources.chain.strategy.content.enabled=true
spring.web.resources.chain.strategy.content.paths=/**

# Compression
server.compression.enabled=true
server.compression.mime-types=text/html,text/xml,text/plain,text/css,text/javascript,application/javascript,application/json
server.compression.min-response-size=1024

# Database (PostgreSQL)
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/galactico}
spring.datasource.username=${DB_USERNAME:galactico}
spring.datasource.password=${DB_PASSWORD:galactico_secret}
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.open-in-view=true  # Required — disabling causes LazyInitializationException in Thymeleaf templates

# HikariCP Connection Pool
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.maximum-pool-size=15
spring.datasource.hikari.idle-timeout=30000
spring.datasource.hikari.max-lifetime=600000
spring.datasource.hikari.connection-timeout=20000

# GitHub OAuth2
spring.security.oauth2.client.registration.github.client-id=${GITHUB_CLIENT_ID}
spring.security.oauth2.client.registration.github.client-secret=${GITHUB_CLIENT_SECRET}
spring.security.oauth2.client.registration.github.scope=read:user,user:email
spring.security.oauth2.client.registration.github.redirect-uri=${OAUTH_REDIRECT_URI:http://localhost:5000/login/oauth2/code/github}

# Session — persistent cookie survives browser close, 7-day timeout
server.servlet.session.timeout=7d
server.servlet.session.cookie.max-age=604800
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.secure=true  # HTTPS only (Azure); set false for local dev

# Thymeleaf
spring.thymeleaf.cache=true  # Enable in production for performance
spring.thymeleaf.mode=HTML
spring.thymeleaf.encoding=UTF-8

# Email
spring.mail.host=${SPRING_MAIL_HOST:smtp.gmail.com}
spring.mail.port=${SPRING_MAIL_PORT:587}
spring.mail.username=${MAIL_USERNAME:}
spring.mail.password=${MAIL_PASSWORD:}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# File Upload
spring.servlet.multipart.max-file-size=10MB

# Logging
logging.level.org.springframework.web=INFO
logging.level.org.hibernate=ERROR
logging.level.com.autotrack=DEBUG
```

### Maven Dependencies (`pom.xml`)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.1.5</version>
</parent>

<properties>
    <java.version>17</java.version>
</properties>

<dependencies>
    <!-- Spring Boot Starters -->
    spring-boot-starter-web
    spring-boot-starter-security
    spring-boot-starter-oauth2-client
    spring-boot-starter-data-jpa
    spring-boot-starter-thymeleaf
    spring-boot-starter-mail
    spring-boot-starter-actuator
    spring-boot-starter-validation
    spring-boot-starter-cache

    <!-- Database -->
    postgresql (42.7.2)
    h2 (test scope)

    <!-- Caching -->
    caffeine (com.github.ben-manes.caffeine)

    <!-- Security -->
    thymeleaf-extras-springsecurity6

    <!-- Utilities -->
    lombok
    jackson-databind
    httpclient5 (5.2.1)
    spring-dotenv

    <!-- Development -->
    spring-boot-devtools
</dependencies>
```

---

## Commit Message Format

Galactico parses commit messages to automatically track tasks. The format is:

```
FeatureCode : task description -> Username -> status
```

### Components
| Part             | Example          | Description                        |
|------------------|------------------|------------------------------------|
| `FeatureCode`    | `Feature01`      | Maps to task's feature_code field  |
| `task description` | `login page`  | Description of the work done       |
| `Username`       | `john_doe`       | Must match the user's nickname     |
| `status`         | `in-progress`    | Task status keyword                |

### Status Keywords
| Keyword       | Maps To       |
|---------------|---------------|
| `todo`        | TODO          |
| `in-progress` | IN_PROGRESS   |
| `done`        | DONE          |

### Examples
```
Feature01 : login page design -> john_doe -> todo
Feature01 : login api integration -> john_doe -> in-progress
Feature01 : login page completed -> john_doe -> done
Feature02 : user dashboard -> jane_smith -> in-progress
```

### How It Works
1. Developer pushes commits to GitHub
2. GitHub webhook sends push event to `/api/webhooks/github`
3. `WebhookService` receives the payload
4. `CommitParserService` extracts feature code, description, username, and status
5. `TaskService` finds the matching task and updates its status
6. `CommitService` stores the commit record linked to the task
7. `NotificationService` creates notifications for relevant team members

---

## Building from Source (Without Docker)

### Prerequisites
- Java 17 (JDK)
- Maven 3.9+
- PostgreSQL 16

### Steps

1. **Create PostgreSQL database**:
   ```sql
   CREATE DATABASE galactico;
   CREATE USER galactico WITH PASSWORD 'galactico_secret';
   GRANT ALL PRIVILEGES ON DATABASE galactico TO galactico;
   ```

2. **Set environment variables** (or create `.env` file):
   ```bash
   export GITHUB_CLIENT_ID=your_client_id
   export GITHUB_CLIENT_SECRET=your_client_secret
   ```

3. **Build and run**:
   ```bash
   mvn clean package -DskipTests
   java -jar target/galactico-*.jar
   ```

4. **Access**: http://localhost:5000

---

## Testing

```bash
# Run all tests
mvn test

# Run with coverage
mvn test jacoco:report

# Coverage report at: target/site/jacoco/index.html
```

Test database uses H2 in-memory (configured in `application-test.properties`).

---

## Key Design Decisions

1. **Dark Theme Only**: Single cohesive dark theme using CSS variables and glassmorphism (`backdrop-filter: blur(20px)`) for consistency
2. **Server-Side Rendering**: Thymeleaf 3.1 templates for SEO and fast initial load; inline `<style>` blocks per page for self-contained dark theme
3. **Progressive Enhancement**: JS adds interactivity (drag-drop, async panels) on top of HTML
4. **GitHub-First**: Authentication, repos, commits, and webhooks all flow through GitHub
5. **Soft Deletes**: Teams and projects use `deletedAt`/`deletedBy` fields to preserve audit history
6. **Commit-Driven Workflow**: Task status updates happen automatically from commit messages
7. **Extension Integration**: VS Code extension enables commit tracking without leaving the IDE
8. **Docker-First Deployment**: Single `docker-compose up` launches the entire stack; multi-stage Dockerfile for production
9. **Sets over Lists for JPA Collections**: `@ManyToMany` and `@OneToMany` relationships use `Set` to avoid Hibernate `MultipleBagFetchException` when using `JOIN FETCH` queries
10. **JOIN FETCH over Lazy Loading**: Team queries eagerly fetch `members` and `projects` via JPQL `LEFT JOIN FETCH` to prevent `LazyInitializationException` during Thymeleaf template rendering
11. **No JDBC Session Store**: In-memory sessions with persistent cookies (`max-age=604800`). Spring Session JDBC was removed because it can't serialize OAuth2 security context containing JPA entity proxies
12. **Caffeine Caching with Selective Use**: `@Cacheable` only on services that return simple data. Team service queries are NOT cached because JOIN FETCH results contain Hibernate proxies that fail on deserialization
13. **Role Authority Pattern**: `UserService.loadUser()` creates authorities with `"ROLE_"` prefix (e.g., `"ROLE_TEAM_LEAD"`), so controllers must use `hasRole('TEAM_LEAD')` not `hasAuthority('TEAM_LEAD')`
14. **Custom Error Controller**: Prevents "Error 200" artifacts where Thymeleaf partially commits a 200 response then hits an exception mid-render — redirects status 200-399 to dashboard
15. **Direct JPQL for Team Member Projects**: `ProjectRepository.findProjectsByTeamMember()` uses `JOIN p.team t JOIN t.members m` instead of iterating `user.getTeams()` (lazy-loaded `@ManyToMany`), which would fail with `LazyInitializationException` or return empty results when the User entity is detached or cached
16. **Extension Token Auth**: VS Code extension uses in-memory token store (`ExtensionAuthService`) with `gal_` prefix tokens (24h expiry) rather than session cookies — enables stateless API calls from the IDE without browser session dependency

---

## Azure Deployment

### Architecture

| Resource | Type | Details |
|---|---|---|
| App Service | `galactico-app` | B1 plan, Linux, Docker container, Always On |
| PostgreSQL | `galactico-db.postgres.database.azure.com` | Flexible Server, Standard_B1ms, v16 |
| Container Registry | `galacticoacr.azurecr.io` | Basic SKU, admin enabled |
| Resource Group | `galactico-rg` | Korea Central region |

### Deployment Steps

```bash
# 1. Build production Docker image (MUST use --no-cache to pick up source changes)
docker build --no-cache -t galacticoacr.azurecr.io/galactico:latest .

# 2. Push to Azure Container Registry
az acr login --name galacticoacr
docker push galacticoacr.azurecr.io/galactico:latest

# 3. Restart the app to pull the new image
az webapp restart --name galactico-app --resource-group galactico-rg

# 4. Wait ~200 seconds for startup (Spring Boot + Hibernate initialization)
# 5. Verify
curl https://galactico-app.azurewebsites.net/login
```

### Key Environment Variables (Azure App Settings)

```
DB_URL=jdbc:postgresql://galactico-db.postgres.database.azure.com:5432/galactico?sslmode=require
DB_USERNAME=galacticoadmin
DB_PASSWORD=<secure-password>
GITHUB_CLIENT_ID=<from-github-oauth-app>
GITHUB_CLIENT_SECRET=<from-github-oauth-app>
OAUTH_REDIRECT_URI=https://galactico-app.azurewebsites.net/login/oauth2/code/github
APP_BASE_URL=https://galactico-app.azurewebsites.net
SERVER_ADDRESS=0.0.0.0
SERVER_PORT=5000
WEBSITES_PORT=5000
```

### Important Notes
- **`--no-cache` is required** when building Docker images because the multi-stage Dockerfile copies source inside Docker; layer caching may serve stale code
- **Startup takes ~180-200 seconds** on B1 plan — Azure's default 230-second container timeout is tight
- **`server.servlet.session.cookie.secure=true`** requires HTTPS — works on Azure, breaks local HTTP dev (set `false` for local)
- **`spring.jpa.open-in-view=true`** is required — setting it to `false` causes `LazyInitializationException` in Thymeleaf templates
