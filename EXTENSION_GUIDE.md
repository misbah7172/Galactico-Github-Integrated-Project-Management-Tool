# Galactico VS Code Extension — Installation & Usage Guide

Complete guide for installing, configuring, and using the AutoTrack Galactico VS Code Extension.

---

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Installation](#installation)
3. [Configuration](#configuration)
4. [Authentication](#authentication)
5. [Features](#features)
6. [Troubleshooting](#troubleshooting)

---

## Prerequisites

- **VS Code** version `1.102.0` or higher
- **Node.js** version `16+` (for building from source)
- **Git** installed and configured
- A running **Galactico web application** (local or deployed)

---

## Installation

### Option A: Install from VSIX (Recommended)

1. **Build the extension package:**

   ```bash
   cd Extension
   npm install
   npm run compile
   npx vsce package --allow-star-activation
   ```

   This creates a `.vsix` file (e.g., `autotrack-galactico-extension-2.0.0.vsix`).

2. **Install in VS Code:**

   - Open VS Code
   - Press `Ctrl+Shift+P` → type **"Install from VSIX"**
   - Select the generated `.vsix` file
   - Reload VS Code when prompted

### Option B: Run in Development Mode

1. **Clone and setup:**

   ```bash
   cd Extension
   npm install
   npm run compile
   ```

2. **Launch:**

   - Open the `Extension` folder in VS Code
   - Press `F5` to launch the Extension Development Host
   - The extension activates in the new VS Code window

### Option C: Watch Mode (for Development)

```bash
cd Extension
npm install
npm run watch
```

Then press `F5` in VS Code to launch with auto-recompiling on changes.

---

## Configuration

### Setting the Galactico Server URL

The extension connects to your Galactico web application. Configure the base URL:

1. Open VS Code Settings (`Ctrl+,`)
2. Search for **"Galactico"**
3. Set **Base URL** to your Galactico server:

| Scenario | Base URL |
|---|---|
| Local development | `https://brings-broadcasting-tucson-fioricet.trycloudflare.com` |
| Docker (default) | `https://brings-broadcasting-tucson-fioricet.trycloudflare.com` |
| Azure deployment | `https://galactico-app.azurewebsites.net` |
| Custom server | Your server URL |

**Via `settings.json`:**

```json
{
    "galactico.baseUrl": "https://galactico-app.azurewebsites.net",
    "galactico.debug": false
}
```

### Running Galactico with Docker

If you don't have a running Galactico server, start one with Docker:

```bash
# From the project root directory
docker-compose up -d
```

Or build and run manually:

```bash
# Build the Docker image
docker build -t galactico:latest .

# Run the container
docker run -d -p 5000:5000 \
  -e SPRING_PROFILES_ACTIVE=postgresql \
  -e GITHUB_CLIENT_ID=your_github_client_id \
  -e GITHUB_CLIENT_SECRET=your_github_client_secret \
  -e DB_URL=jdbc:postgresql://host:5432/galactico \
  -e DB_USERNAME=your_db_user \
  -e DB_PASSWORD=your_db_password \
  galactico:latest
```

---

## Authentication

The extension uses **token-based authentication** with the Galactico backend.

### Step 1: Start Authentication

Choose one of these methods:

- **Command Palette:** `Ctrl+Shift+P` → **"Start Galactico Authentication"**
- **Quick Auth:** `Ctrl+Shift+P` → **"Quick Authenticate"**
- **Sidebar:** Click **"AutoTrack Galactico"** in the Activity Bar → **"Authenticate with Galactico"**

### Step 2: GitHub OAuth Login

A browser window opens with the Galactico login page. Sign in with your GitHub account.

### Step 3: Copy Token

After successful login, a token display page appears with your access token (format: `gal_xxxxxxxx`).

- The token is automatically copied to your clipboard
- You can also click **"Copy Token"** button

### Step 4: Paste Token in VS Code

Return to VS Code and paste the token when prompted in the input box.

### Step 5: Verification

The extension validates your token and displays your username. You're now authenticated!

### Token Details

| Property | Value |
|---|---|
| Format | `gal_` + 32-character hex string |
| Expiration | 24 hours |
| Storage | VS Code Secrets API (encrypted) |
| Auto-restore | Yes (on VS Code restart) |

### Logout

`Ctrl+Shift+P` → **"Logout from Galactico"**

---

## Features

### Activity Bar Panel

The extension adds an **"AutoTrack Galactico"** panel to the VS Code Activity Bar with four sections:

#### 1. AutoTrack Galactico (Main Section)
| Action | Description |
|---|---|
| Quick Authenticate | Fast login flow |
| Authenticate with Galactico | Full authentication flow |
| Open Galactico Dashboard | Opens web dashboard in browser |
| Open Task Dashboard | Opens VS Code task dashboard panel |
| Sync Tasks | Synchronizes tasks from backend |
| Check Login Status | Shows current auth status |

#### 2. Sprint Management
| Action | Description |
|---|---|
| Quick Sprint Assignment | Assign task to current sprint |
| Select Task for Sprint | Select and assign a task |
| View Commit Status | Check commit review status |
| Galactico Smart Commit | Create commit with task tracking |

#### 3. Task Management
| Action | Description |
|---|---|
| Create AutoTrack Commit | Formatted commit with feature code |
| Quick Backlog Addition | Add items to backlog |
| Open Contributions | View contribution analytics |
| Configure CI/CD | Setup CI/CD pipeline |

#### 4. Git Automation
| Action | Description |
|---|---|
| Git Add | Stage all changes |
| Git Commit | Commit with message |
| Commit to Local Branch | Add + commit locally (no push) |
| Git Push to Repository | Push to team repo with branch selection |
| Auto: Add + Commit + Push | Complete workflow automation |

### Task Dashboard (VS Code Panel)

The built-in task dashboard shows:

- **Statistics Overview:** Total tasks, completed, in-progress, overdue
- **Task Cards:** With project name, deadline, remaining time, branch name
- **Commit History:** Per-task commit list with approval status
- **Filters:** All / In Progress / Completed / Overdue
- **Auto-refresh:** Every 10 minutes

Open via: `Ctrl+Shift+P` → **"Open Task Dashboard"**

### CI/CD Pipeline Configuration

Generate CI/CD workflows for your projects directly from VS Code:

1. `Ctrl+Shift+P` → **"Configure CI/CD"**
2. Select your project
3. Choose project type (Node.js, Python, React, Docker, Java, Custom)
4. Choose deployment strategy
5. Pipeline files are generated automatically

### AutoTrack Commit System

Create structured commits with task tracking metadata:

```
Feature123: Implement user authentication -> assignee -> sprint1 -> sp:5
```

Commit format includes: Feature code, task title, assignee, sprint, backlog priority, story points, time estimate, and tags.

---

## All Commands Reference

| Command | Keyboard | Description |
|---|---|---|
| Start Galactico Authentication | — | Begin OAuth authentication |
| Quick Authenticate | — | Fast token-based login |
| Refresh Galactico | — | Refresh extension data |
| Git Add | — | `git add .` |
| Git Commit | — | Commit with message prompt |
| Git Push | — | Push to remote |
| Git Auto | — | Add + Commit + Push |
| Create GitHub Repository | — | Create new repo via GitHub API |
| Galactico Commit | — | Commit with task tracking |
| Select Task | — | Choose task for sprint |
| View Commit Status | — | Open dashboard in browser |
| Authenticate with Galactico | — | Full auth flow |
| Logout from Galactico | — | Revoke token and logout |
| Open Token Display | — | Open token page in browser |
| Open Task Dashboard | — | VS Code task dashboard panel |
| Sync Tasks | — | Sync tasks from backend |
| Open Contributions | — | View contribution stats |
| Open Web Dashboard | — | Open Galactico in browser |
| Configure CI/CD | — | Setup CI/CD pipeline |
| Check Login Status | — | Show auth status |
| Authenticate with GitHub API | — | GitHub API authentication |
| Commit to Local Branch | — | Local commit (no push) |
| Push to Repository | — | Push with branch selection |
| Auto Push to Repo | — | Full workflow to repo |
| AutoTrack Create Commit | — | Structured commit creator |
| Quick Sprint Assignment | — | Fast sprint task assignment |
| Quick Backlog Addition | — | Fast backlog item creation |

---

## Troubleshooting

### "Failed to sync tasks" or "HTTP error 401"

- Your token has expired (24h lifetime). Re-authenticate.
- Check that `galactico.baseUrl` points to your running server.

### "No tasks found"

- Ensure tasks are assigned to your account in the Galactico web app.
- Try syncing: `Ctrl+Shift+P` → **"Sync Tasks"**

### Extension not appearing in Activity Bar

- Ensure the extension is installed (check Extensions panel)
- Try reloading: `Ctrl+Shift+P` → **"Developer: Reload Window"**

### Cannot connect to server

- Verify the Galactico server is running
- Check `galactico.baseUrl` in settings
- For local Docker: ensure port 5000 is mapped and container is running

### Git operations failing

- Ensure Git is installed: `git --version`
- Ensure you're in a Git repository workspace
- For push: ensure remote origin is configured

### Token not being accepted

- Tokens expire after 24 hours — generate a new one
- Ensure you're copying the full token including the `gal_` prefix
- Check that the server hasn't been restarted (in-memory tokens are lost on restart)

### Debug Mode

Enable verbose logging:

```json
{
    "galactico.debug": true
}
```

Check the **Output** panel → **GitHub Auth Extension** for detailed logs.

---

## Architecture

```
Extension/
├── src/
│   ├── extension.ts              # Main entry point, command registration
│   ├── config.ts                 # Configuration management
│   ├── galacticoAuthService.ts   # Token-based auth with Galactico backend
│   ├── githubAuth.ts             # GitHub API authentication (Octokit)
│   ├── galacticoService.ts       # Task selection, smart commits
│   ├── dashboardService.ts       # VS Code webview task dashboard
│   ├── cicdService.ts            # CI/CD pipeline configuration
│   ├── autoTrackCommitService.ts # Sprint/backlog commit automation
│   └── githubTreeProvider.ts     # Activity Bar tree view
├── package.json                  # Extension manifest & commands
└── tsconfig.json                 # TypeScript configuration
```

### Backend API Endpoints Used

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/auth/login-url` | GET | Get OAuth login URL |
| `/api/auth/token-display` | GET | Display token after OAuth |
| `/api/auth/me` | GET | Validate token, get user info |
| `/api/auth/logout` | POST | Revoke extension token |
| `/api/user/tasks` | GET | Get user's assigned tasks |
| `/api/user/contributions` | GET | Get contribution statistics |
| `/api/user/projects` | GET | Get user's projects |
| `/webhook/commit` | POST | Send commit data to backend |
