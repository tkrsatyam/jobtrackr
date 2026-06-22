# 🔗 JIRA + GitHub Integration Guide

Reference for keeping JIRA work items and GitHub development activity in sync.
If commits, branches, or PRs are not showing up in the JIRA **Development** panel, start with the [Troubleshooting](#troubleshooting) section at the bottom.

---

## Finding Your JIRA Key

Every JIRA work item has a unique key in the format **`PROJECT-123`** (e.g. `JD-42`).

| Where to look | What you see |
|---|---|
| Board card | Key displayed at the bottom of the card |
| Work item detail page | Key shown in the breadcrumb/URL at the top |
| Backlog list | Key shown in the leftmost column |

> The key is **case-insensitive** for GitHub matching purposes, but the JIRA convention is uppercase. Use uppercase to be safe.

---

## Naming Conventions

### Branches

```
git checkout -b JD-123-short-description
```

- Put the key **at the start** of the branch name.
- Use lowercase with hyphens for the description part.
- Keep it short and descriptive — it shows up in PR titles and JIRA panels.

**Examples for this project:**

```
JD-10-user-service-jwt-refresh
JD-24-reminder-kafka-producer
JD-31-frontend-application-form
JD-45-fix-eureka-health-check
```

---

### Commit Messages

```
git commit -m "JD-123 brief description of what changed"
```

- The key must appear **somewhere in the commit message** — beginning is recommended.
- Every commit on the branch gets linked individually if it contains the key.
- Commits without the key will **not** appear in the JIRA development panel.

**Examples:**

```bash
git commit -m "JD-10 add refresh token rotation in UserService"
git commit -m "JD-24 configure Kafka producer for reminder events"
git commit -m "JD-10 fix token expiry edge case — closes #12"
```

**Smart Commits** (optional but powerful):

If your JIRA project has Smart Commits enabled, you can drive JIRA transitions and log work directly from commit messages:

```bash
# Transition a ticket to In Review
git commit -m "JD-123 #in-review add pagination to application list"

# Log time worked
git commit -m "JD-123 #time 2h 30m implement contact entity mapping"

# Add a comment to the ticket
git commit -m "JD-123 #comment endpoint tested locally, ready for PR"

# Transition and log time in one commit
git commit -m "JD-123 #done #time 4h complete analytics aggregation pipeline"
```

> Smart commit commands use the `#` prefix followed by your JIRA workflow status name (as configured by your admin). `#time`, `#comment`, and `#done` are the most common.

---

### Pull Requests

```
[JD-123] Short description of what the PR does
```

- Include the JIRA key in the **PR title** — wrap it in brackets for readability.
- The PR description should reference the ticket for traceability.
- When the PR is merged, the linked issue shows **"1 pull request"** in the JIRA Development panel.

**PR title examples:**

```
[JD-10] JWT refresh token implementation in user-service
[JD-24] Kafka producer setup for reminder-service
[JD-31] Angular application form with status pipeline
```

**Recommended PR description template:**

```markdown
## JIRA
[JD-123](https://your-jira-site.atlassian.net/browse/JD-123)

## What changed
- Short bullet list of changes

## How to test
- Steps to verify locally

## Services affected
- user-service / application-service / etc.
```

---

## Workflow Summary

```
1. Pick a JIRA ticket → note the key (e.g. JD-42)
2. git checkout -b JD-42-feature-name
3. git commit -m "JD-42 description of change"   ← repeat for every commit
4. Push branch → open PR with title "[JD-42] Feature name"
5. JIRA Development panel now shows: branch ✓ · commits ✓ · pull request ✓
```

---

## What Shows Up in the JIRA Development Panel

| GitHub action | Appears in JIRA if… |
|---|---|
| Branch | Branch name contains the key |
| Commit | Commit message contains the key |
| Pull Request | PR title contains the key |
| Build status | GitHub Actions workflow is connected via GitHub for JIRA |
| Deployment | Deployment environment is tracked via GitHub environments |

---

## Microservices-Specific Notes

This project has 8 backend services + 1 frontend. A few things to keep consistent:

- A single JIRA story may involve changes across multiple services (e.g. `application-service` + `notification-service` + Kafka topic). Use the **same key** in all commits across all services — they all roll up to the same ticket.
- If a commit touches infrastructure (Docker Compose, `scripts/`, `.github/workflows/`), still tag it with the relevant ticket key if it was done as part of that story.
- For purely chore/housekeeping commits unrelated to a ticket (dependency bumps, `.gitignore` fixes), it is fine to omit the key — they will not link to JIRA, which is intentional.

---

## Troubleshooting

If development information is not appearing in JIRA, work through this checklist in order.

### 1. Integration not set up
- Go to **JIRA → Settings → Apps → GitHub for Jira**.
- Confirm the app is installed and `tkrsatyam/jobtrackr` is listed under connected repositories.
- If the repo is missing, click **Add repository** and authorise access.

### 2. Key not in the right place
- Branch names must contain the key anywhere in the name — `JD-123-anything` works.
- Commit messages must contain the key anywhere in the message body.
- PR title must contain the key — the PR *description* alone is not enough.
- Double-check for typos: `DJ-123` instead of `JD-123` will silently fail.

### 3. Wrong project key
- JIRA project keys are set when the project is created and are shown in the project settings.
- If your project key is not `JD`, update all examples in this guide accordingly.
- Confirm by going to **JIRA → Project Settings → Details**.

### 4. Delay in sync
- JIRA does not update in real time. After a push, wait **up to 60 seconds** for commits and branches to appear.
- After a PR is opened or merged, allow **1–2 minutes**.
- If nothing appears after 5 minutes, move on to the steps below.

### 5. Force a re-sync
- Open the work item in JIRA.
- Scroll to the **Development** panel on the right.
- Click the **refresh icon** (⟳) if available, or re-save the issue with a minor field edit to trigger a re-fetch.

### 6. Re-authorise the GitHub App
- Go to **JIRA → Settings → Apps → GitHub for Jira → Configure**.
- Disconnect and reconnect the repository.
- This forces a full backfill of recent commits and branches.

### 7. Commits on a branch not showing up after squash/rebase
- If you rewrite Git history (interactive rebase, squash merge), the original commit SHAs are replaced.
- The new squashed commit must still contain the JIRA key in its message, otherwise the link is lost.
- Prefer **merge commits** or **squash with the key preserved** in the final commit message.

### 8. Private repo or org-level permission issues
- If the repo is ever made private, go back to the GitHub App settings in JIRA and re-grant repository access.
- Organisation-level GitHub Apps require an org admin to approve the JIRA app installation.

### 9. Build/deployment status not showing
- Build status comes from GitHub Actions. The workflow must report status back to GitHub's Checks API (most standard actions do this automatically).
- Deployment tracking requires using GitHub **Environments** in your workflow YAML (`environment: production`).
- See `.github/workflows/deploy.yml` in this repo for the current setup.

---

## Quick Reference Card

```
Branch    →  git checkout -b JD-123-description
Commit    →  git commit -m "JD-123 what you did"
PR title  →  [JD-123] What the PR does

Smart commits (optional):
  #in-progress  #in-review  #done
  #time 1h 30m
  #comment your note here
```

---

## Official Reference

If you run into an issue not covered here, the Atlassian documentation is the authoritative source:

- **GitHub for Jira — full setup & troubleshooting guide:**
  https://support.atlassian.com/jira-cloud-administration/docs/integrate-with-github/

- **Smart Commits reference:**
  https://support.atlassian.com/jira-software-cloud/docs/process-issues-with-smart-commits/

- **Development panel not showing data — Atlassian KB:**
  https://support.atlassian.com/jira-software-cloud/docs/view-development-information-for-an-issue/

---

*Last updated: June 2026*
