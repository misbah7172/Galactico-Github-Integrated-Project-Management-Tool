# Galactico - GitHub-Integrated Project Management Tool

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.1.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.oracle.com/java/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![GitHub](https://img.shields.io/badge/GitHub-Integration-black.svg)](https://github.com)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg)](https://docs.docker.com/compose/)

## Overview

Galactico is a full-stack project management platform built for software development teams that want tight GitHub integration. Tasks update automatically based on commit messages, and the included VS Code extension lets developers commit and manage tasks without leaving their editor.

## Key Features

- **Kanban Board**: Drag-and-drop task management with sprint integration
- **Sprint Management**: Scrum-style sprint planning with backlog and progress tracking  
- **Commit Tracking**: Automatic task status updates parsed from Git commit messages
- **GitHub OAuth2**: Secure login, no separate password required
- **Team Management**: Role-based access (Team Lead / Member), invitations, team chat
- **Product Backlog**: Prioritized backlog with story points, issue types, and acceptance criteria
- **Activity Timeline**: Per-project audit log of all task and sprint changes
- **CI/CD Generator**: Generate GitHub Actions pipelines for multiple languages
- **VS Code Extension**: Commit with task references, sync status, manage sprints from IDE
- **Analytics**: Contributor stats, commit frequency, language breakdown per project

## Technology Stack

| Layer         | Technology                                  |
|---------------|---------------------------------------------|
| Backend       | Spring Boot 3.1.5, Java 17                  |
| Database      | PostgreSQL 16                               |
| Auth          | GitHub OAuth2 via Spring Security           |
| Templates     | Thymeleaf                                   |
| Frontend      | Vanilla JS, CSS3 (custom dark design system)|
| Build         | Maven 3.9.4                                 |
| Container     | Docker + Docker Compose                     |
| VS Code Ext   | TypeScript, VS Code Extension API           |
| Azure Hosting | Azure App Service (Korea Central)           |

## Quick Start with Docker

### Prerequisites

- Docker Desktop
- A GitHub OAuth App (Settings > Developer Settings > OAuth Apps)
  - Homepage URL: `https://brings-broadcasting-tucson-fioricet.trycloudflare.com`  
  - Callback URL: `https://brings-broadcasting-tucson-fioricet.trycloudflare.com/login/oauth2/code/github`

### 1. Configure environment

Copy `.env.example` to `.env` and fill in your values:

```bash
cp .env.example .env
```

Required values in `.env`:
```env
GITHUB_CLIENT_ID=your_github_oauth_client_id
GITHUB_CLIENT_SECRET=your_github_oauth_client_secret
JWT_SECRET=at_least_32_character_random_secret
ENCRYPTION_KEY=exactly_32_character_key_here!!!
DB_NAME=galactico
DB_USERNAME=galactico
DB_PASSWORD=galactico_secret
BASE_URL=https://brings-broadcasting-tucson-fioricet.trycloudflare.com
OAUTH_REDIRECT_URI=https://brings-broadcasting-tucson-fioricet.trycloudflare.com/login/oauth2/code/github
```

### 2. Start the stack

```bash
docker-compose up --build -d
```

### 3. Open the application

Navigate to `https://brings-broadcasting-tucson-fioricet.trycloudflare.com` and log in with GitHub.

### Services

| Container          | Port (host:container) | Description           |
|--------------------|-----------------------|-----------------------|
| galactico-postgres | 5433:5432             | PostgreSQL 16 Alpine  |
| autotrack-app      | 5000:5000             | Spring Boot app       |

## Commit Message Format

Galactico parses commit messages to automatically update task status:

```
FeatureCode : description -> YourGitHubNickname -> status
```

Status keywords: `todo`, `in-progress`, `done`

Examples:
```
Feature01 : implement login page -> johndoe -> in-progress
Feature01 : login page complete  -> johndoe -> done
Feature02 : fix null pointer bug -> janedoe -> done
```

## VS Code Extension

The extension is located in the `Extension/` directory.

### Setup

```bash
cd Extension
npm install
npm run compile
```

Press `F5` in VS Code to launch the Extension Development Host.

### Configuration

Set `galactico.baseUrl` in VS Code settings to point at your running Galactico instance:

```json
{
  "galactico.baseUrl": "https://brings-broadcasting-tucson-fioricet.trycloudflare.com"
}
```

### Available Commands

- **Start Galactico Authentication** - Authenticate with your Galactico instance
- **Galactico Commit** - Commit with automatic task reference format
- **Select Task** - Pick a task before committing
- **Open Task Dashboard** - View your tasks inside VS Code
- **Sync Tasks** - Pull latest task data from backend
- **Git Auto (Add + Commit + Push)** - One-click full workflow
- **Configure CI/CD** - Generate pipeline config for your project

## Building from Source

```bash
# Backend
mvn clean package -DskipTests

# Run locally (requires PostgreSQL running)
java -jar target/autotrack-*.jar

# VS Code Extension
cd Extension
npm install && npm run compile
```

## Azure Deployment (Korea Central)

The CI/CD pipeline automatically deploys to Azure App Service on push to `main`.

Required GitHub secrets for Azure deployment:

| Secret                          | Description                                  |
|---------------------------------|----------------------------------------------|
| `AZURE_CREDENTIALS`             | Service principal JSON from `az ad sp create-for-rbac` |
| `AZURE_REGISTRY_LOGIN_SERVER`   | ACR login server (e.g., `galactico.azurecr.io`) |
| `AZURE_REGISTRY_USERNAME`       | ACR username                                 |
| `AZURE_REGISTRY_PASSWORD`       | ACR password                                 |
| `AZURE_RESOURCE_GROUP`          | Resource group name                          |
| `AZURE_APP_SERVICE_NAME`        | App Service name                             |
| `GITHUB_CLIENT_ID`              | GitHub OAuth client ID                       |
| `GITHUB_CLIENT_SECRET`          | GitHub OAuth client secret                   |
| `JWT_SECRET`                    | JWT signing key (min 32 chars)               |
| `ENCRYPTION_KEY`                | Encryption key (exactly 32 chars)            |
| `DB_URL`                        | Production JDBC connection string            |
| `DB_USERNAME`                   | Database username                            |
| `DB_PASSWORD`                   | Database password                            |

See `.github/workflows/azure-deploy.yml` for deployment details.

## Docker Image (GitHub Container Registry)

Production Docker images are published to `ghcr.io/misbah7172/galactico--github-integrated-project-management-tool` on every push to `main`.

## GitHub Actions Required Secrets

| Secret          | Required For           |
|-----------------|------------------------|
| `SONAR_TOKEN`   | SonarCloud analysis (optional) |
| `GITHUB_TOKEN`  | Auto-provided by GitHub Actions |

All Azure secrets listed above are required for Azure deployment.

## Running Tests

```bash
mvn clean test
```

Test configuration uses H2 in-memory database (see `src/test/resources/application.properties`).

## Branch Management

Pull all remote branches locally:
```bash
git fetch --all && for br in $(git branch -r | grep -v '\->'); do git branch --track "${br#origin/}" "$br" 2>/dev/null; done
```

## License

MIT License. See [LICENSE](LICENSE) for details.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit using the Galactico commit format
4. Push and open a Pull Request

Commit convention: `type: message` (e.g., `feat: add sprint velocity chart`, `fix: null pointer in webhook handler`)
