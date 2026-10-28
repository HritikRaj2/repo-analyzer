
# RepoMind Backend

### GitHub Repository Intelligence & Health Analysis Platform

RepoMind is a backend platform designed to analyze GitHub repositories, inspect their file structures, measure source-code statistics, identify declared dependencies, and generate repository health assessments.

Built with Java and Spring Boot, RepoMind exposes REST APIs that can be consumed by a Next.js frontend to visualize repository metrics, dependency information, analysis progress, and repository health through interactive dashboards and charts.

> **Current Status:** Core repository ingestion, file inventory, source-content analysis, dependency extraction, and repository health assessment are implemented. Some metrics depend on how much repository content has been processed. Live vulnerability intelligence and dependency version checks are not yet complete.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Key Features](#key-features)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Prerequisites](#prerequisites)
- [Getting Started](#getting-started)
- [Environment Configuration](#environment-configuration)
- [API Documentation](#api-documentation)
- [Repository Analysis Workflow](#repository-analysis-workflow)
- [Dependency Analysis](#dependency-analysis)
- [Repository Health Assessment](#repository-health-assessment)
- [Content Analysis and Progress Tracking](#content-analysis-and-progress-tracking)
- [Database](#database)
- [Current Limitations](#current-limitations)
- [Roadmap](#roadmap)
- [Security](#security)
- [Contributing](#contributing)
- [License](#license)

---

## Project Overview

Understanding a large GitHub repository can be difficult because its source files, dependencies, tests, configuration files, and documentation are spread across many directories.

RepoMind aims to simplify this process by collecting repository metadata and structural information, analyzing source files, extracting dependencies, and generating a structured health assessment.

### Primary Objectives

- Retrieve and persist GitHub repository metadata.
- Build a file and directory inventory from a repository.
- Analyze source-code content and count nonblank lines.
- Extract declared dependencies from supported package manifests.
- Calculate repository health metrics and generate findings.
- Track analysis status, progress, and historical assessments.
- Provide structured REST APIs for dashboard visualizations.

---

## Key Features

### 1. GitHub Repository Ingestion

Accepts a GitHub repository URL and stores its metadata in the database.

**Endpoint**

`POST /api/repositories/analyze`

### 2. Repository File Inventory

Retrieves the repository's file tree and records information about files and directories.

The inventory supports analysis of:

- Repository structure
- File and directory counts
- Source and test file classifications
- File extensions and types
- Repository composition

**Endpoint**

`POST /api/repositories/{id}/files`

### 3. Source Content Analysis

Fetches eligible source files and calculates nonblank-line statistics.

The results contribute to source-code metrics and repository health assessments.

Content analysis supports configurable batch processing through query parameters.

**Endpoint**

`POST /api/repositories/{id}/content-analysis`

### 4. Content Analysis Progress

Exposes the current processing status, including progress information and whether more content remains to be analyzed.

**Endpoint**

`GET /api/repositories/{id}/content-analysis/status`

### 5. Dependency Extraction

Detects declared dependencies from supported manifest formats.

| Ecosystem | Manifest Files |
|---|---|
| Maven | `pom.xml` |
| Gradle | `build.gradle`, `build.gradle.kts` |
| npm | `package.json` |

Dependency records can be retrieved through a paginated API.

**Endpoints**

- `POST /api/repositories/{id}/dependencies/analyze`
- `GET /api/repositories/{id}/dependencies?page=0&size=20`

Dependency extraction is distinct from vulnerability scanning. The current implementation should not be interpreted as a complete security audit.

### 6. Repository Health Assessment

Generates a repository health assessment using the metrics and findings currently available to the backend.

Depending on the available data, the assessment includes:

- Health score
- Health grade
- Confidence level
- Assessment scope
- Analysis status
- Repository metrics
- Findings and warnings
- Assessment history

**Endpoints**

- `POST /api/repositories/{id}/analysis`
- `GET /api/repositories/{id}/analysis/latest`
- `GET /api/repositories/{id}/analysis/{analysisId}`

When important metrics have not been collected, the backend can label an assessment as preliminary instead of presenting incomplete information as definitive.

---

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 21 | Backend programming language |
| Spring Boot | REST API framework |
| Maven | Dependency management and build automation |
| Spring Data JPA | Database persistence |
| PostgreSQL | Relational data storage |
| Hibernate | Object-relational mapping |
| GitHub API integration | Repository metadata, file tree, and source retrieval |
| REST APIs | Communication with the frontend |
| Next.js and Tailwind CSS | Frontend integration |

Confirm the exact dependency versions against `pom.xml` before publishing a release.

---

## Architecture

The backend follows a layered architecture that separates API handling, business logic, external integrations, and persistence.

```text
                  GitHub Repository
                         |
                         v
                 GitHub Integration
                         |
                         v
                 Repository Analysis
                         |
           +-------------+-------------+
           |             |             |
           v             v             v
      File Inventory  Content       Dependency
                      Analysis      Extraction
           |             |             |
           +-------------+-------------+
                         |
                         v
                  Health Assessment
                         |
                         v
                   Spring Data JPA
                         |
                         v
                     PostgreSQL
                         |
                         v
                    REST APIs
                         |
                         v
                   Next.js Frontend
                         |
                         v
                Charts and Dashboards
```

### Main Layers

**Controller Layer**

Receives HTTP requests, validates inputs, and returns API responses.

**Service Layer**

Coordinates repository ingestion, content processing, dependency extraction, and health assessment.

**Repository Layer**

Provides database access through Spring Data JPA.

**Entity Layer**

Represents persisted data, including repositories, repository files, dependencies, and analysis records.

**Integration Layer**

Communicates with GitHub to retrieve repository metadata, trees, and source content.

The precise package and class names should be taken from the current source code.

---

## Prerequisites

Install the following before running the backend:

- Java Development Kit (JDK) 21
- Apache Maven 3.9 or a compatible Maven installation
- PostgreSQL
- Git
- A GitHub personal access token, if required by the GitHub integration

Verify your installations:

```bash
java -version
mvn -version
git --version
```

Ensure Maven uses Java 21.

---

## Getting Started

### 1. Clone the Repository

```bash
git clone <YOUR_BACKEND_REPOSITORY_URL>
cd <YOUR_BACKEND_DIRECTORY>
```

Replace the placeholders with your actual repository URL and directory name.

### 2. Create the PostgreSQL Database

Execute the following SQL command:

```sql
CREATE DATABASE repomind;
```

If the database already exists, reuse it.

### 3. Configure Application Properties

Check:

`src/main/resources/application.yml`

The configuration should contain the appropriate PostgreSQL connection settings, GitHub token configuration, and server port.

Example configuration shape:

```yaml
server:
  port: 8686

spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/repomind}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update

github:
  token: ${GITHUB_TOKEN:}
```

**Important:** This is an example configuration, not a replacement for the existing project configuration. Preserve any existing timeout, batch-size, and other application settings. Ensure the database credentials match your local PostgreSQL setup.

### 4. Configure Environment Variables

For PowerShell:

```powershell
$env:DB_URL="jdbc:postgresql://localhost:5432/repomind"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="YOUR_DATABASE_PASSWORD"
$env:GITHUB_TOKEN="YOUR_GITHUB_TOKEN"
```

These values apply to the current PowerShell session.

Alternatively, configure the environment variables in your IntelliJ IDEA run configuration.

If your existing `application.yml` uses different property names, follow the configuration already defined in the project.

Never commit credentials or GitHub tokens to version control.

### 5. Build the Application

```bash
mvn clean package
```

### 6. Run the Application

Using Maven:

```bash
mvn spring-boot:run
```

Alternatively, run the main Spring Boot application class from IntelliJ IDEA.

The expected local base URL is:

`http://localhost:8686`

Check the startup logs to confirm the application started successfully and connected to PostgreSQL.

---

## Environment Configuration

| Variable | Purpose | Example |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/repomind` |
| `DB_USERNAME` | PostgreSQL username | `postgres` |
| `DB_PASSWORD` | PostgreSQL password | Set privately |
| `GITHUB_TOKEN` | GitHub API authentication token | Set privately |

These are suggested environment variable names. Use the names expected by your current application configuration.

---

## API Documentation

The following endpoints represent the currently implemented API surface. Confirm request formats, response schemas, and validation rules against the actual controller definitions.

### Base URL

```text
http://localhost:8686
```

### 1. Analyze a GitHub Repository

**Endpoint:** `POST /api/repositories/analyze`

Registers a GitHub repository for analysis.

**Example request body:**

```json
"https://github.com/spring-projects/spring-boot"
```

The existing implementation accepts the repository URL as a raw JSON string rather than necessarily requiring an object such as `{"url": "..."}`.

**PowerShell example:**

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8686/api/repositories/analyze" `
  -ContentType "application/json" `
  -Body '"https://github.com/spring-projects/spring-boot"'
```

Record the repository ID returned by the response. Subsequent operations use that ID.

### 2. Ingest Repository Files

**Endpoint:** `POST /api/repositories/{id}/files`

Example:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8686/api/repositories/1/files"
```

Replace `1` with the actual repository ID.

This operation populates the file inventory used by later analysis stages.

### 3. Analyze Source Content

**Endpoint:** `POST /api/repositories/{id}/content-analysis`

Example:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8686/api/repositories/1/content-analysis"
```

The endpoint also supports query parameters for controlling batch processing, where supported by the current implementation.

Example:

```text
POST /api/repositories/1/content-analysis?batchSize=10&maxBatches=1
```

Use the project's configured limits and defaults. Small batches can be useful when testing large repositories.

### 4. Check Content Analysis Progress

**Endpoint:** `GET /api/repositories/{id}/content-analysis/status`

Example:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8686/api/repositories/1/content-analysis/status"
```

Use this endpoint to determine whether more source files remain to be processed.

### 5. Analyze Dependencies

**Endpoint:** `POST /api/repositories/{id}/dependencies/analyze`

Example:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8686/api/repositories/1/dependencies/analyze"
```

The backend inspects supported package manifests and persists extracted dependency information.

### 6. Retrieve Dependencies

**Endpoint:** `GET /api/repositories/{id}/dependencies?page=0&size=20`

Example:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8686/api/repositories/1/dependencies?page=0&size=20"
```

| Parameter | Description |
|---|---|
| `page` | Zero-based page index |
| `size` | Number of records requested per page |

Check the actual response DTO for pagination metadata and dependency fields.

### 7. Generate a Health Assessment

**Endpoint:** `POST /api/repositories/{id}/analysis`

Example:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8686/api/repositories/1/analysis"
```

This operation generates and stores a repository health assessment using the metrics available at the time of analysis.

### 8. Retrieve the Latest Health Assessment

**Endpoint:** `GET /api/repositories/{id}/analysis/latest`

Example:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8686/api/repositories/1/analysis/latest"
```

### 9. Retrieve a Specific Assessment

**Endpoint:** `GET /api/repositories/{id}/analysis/{analysisId}`

Example:

```powershell
Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8686/api/repositories/1/analysis/1"
```

Replace the final ID with a real analysis ID returned by the backend.

### Development Endpoint

The backend also includes a `GET /api/github/tree` endpoint used during development.

Confirm its request parameters and availability in the controller. Do not expose development-only endpoints publicly without reviewing their security implications.

---

## Repository Analysis Workflow

For a newly registered repository, the recommended initial workflow is:

1. Register the GitHub repository.
2. Ingest its file inventory.
3. Start content analysis.
4. Check content-analysis progress.
5. Analyze declared dependencies.
6. Generate a health assessment.
7. Retrieve the latest assessment and dependencies for the frontend.

```text
POST /api/repositories/analyze
              |
              v
POST /api/repositories/{id}/files
              |
              v
POST /api/repositories/{id}/content-analysis
              |
              v
GET /api/repositories/{id}/content-analysis/status
              |
              v
POST /api/repositories/{id}/dependencies/analyze
              |
              v
POST /api/repositories/{id}/analysis
              |
              v
GET /api/repositories/{id}/analysis/latest
```

For large repositories, content analysis may require multiple batches. Poll its status and continue processing according to the behavior implemented by the backend.

A health assessment created before content analysis is complete may have incomplete metrics. The frontend should display the assessment's confidence and preliminary status when those fields are available.

---

## Dependency Analysis

RepoMind extracts declared dependencies from supported build and package manifests.

### Supported Formats

**Maven**
- `pom.xml`

**Gradle**
- `build.gradle`
- `build.gradle.kts`

**npm**
- `package.json`

### Dependency Data

The backend stores extracted dependency records and supports paginated retrieval.

Fields describing a dependency's latest version, outdated status, or vulnerability status may be absent or null because live version and security intelligence are not yet fully implemented.

### Planned Improvements

- Latest stable version lookup
- Outdated dependency detection
- Known vulnerability matching
- CVE and advisory references
- Severity classification
- Dependency risk summaries

These capabilities should only be marked as implemented after the corresponding data sources, logic, and tests are available.

---

## Repository Health Assessment

The health assessment combines available repository metrics and findings into a structured result.

### Assessment Information

Depending on the data available, the API can return:

- Health score
- Health grade
- Confidence level
- Analysis status
- Assessment scope
- Repository metrics
- Findings
- Historical assessment information

### Preliminary Assessments

A repository's full source tree may contain thousands of files. Processing all source contents can take multiple batches.

For example, an assessment may report thousands of source files but have line counts for only a small subset. Such an assessment should be treated as preliminary.

The backend has been updated to represent incomplete assessments using information such as:

- `partialAssessment`
- `assessmentScope`
- Confidence level
- A preliminary health grade
- A finding indicating incomplete content metrics

The exact response structure depends on the current DTO.

### Important Interpretation

A repository health score is a heuristic assessment based on the metrics currently implemented. It is not a formal security certification, a guarantee of code quality, or a replacement for a complete testing and security audit.

Likewise, a ratio of test files to source files is not equivalent to measured test coverage.

---

## Content Analysis and Progress Tracking

Content analysis retrieves source content and calculates nonblank-line statistics for eligible files.

The progress endpoint can expose fields such as:

- Total eligible source files
- Number of processed files
- Number of remaining files
- Percentage completed
- Current processing status
- Whether more batches remain
- Whether a rate-limit condition was encountered

Verify actual field names and semantics against the current status response DTO.

### Handling Large Repositories

Large repositories should be processed incrementally rather than requiring every source file to be fetched in one request.

Recommended procedure:

1. Ingest the file tree.
2. Start a content-analysis batch.
3. Check the progress endpoint.
4. Continue batches when required.
5. Verify completion before relying on content-dependent metrics.
6. Run a new health assessment after sufficient data has been collected.

GitHub API rate limits, inaccessible files, unsupported file formats, and repository size may affect completion.

---

## Database

RepoMind uses PostgreSQL to persist repository data and analysis results.

### Local Database Settings

| Setting | Development Value |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `repomind` |
| Backend port | `8686` |

The database username and password depend on your local configuration.

### Main Data Concepts

The current backend includes persistence models for:

- Repository metadata
- Repository files and file inventory
- Declared dependencies
- Repository analysis results
- Analysis status and associated metrics or findings

The exact table names, foreign keys, column definitions, and indexes should be taken from the current entity classes and database schema.

### Schema Management

The development configuration uses Hibernate schema update behavior. Review the actual `spring.jpa.hibernate.ddl-auto` setting before deployment.

For production, prefer controlled database migrations and explicit schema versioning instead of relying on automatic schema updates.

---

## Current Limitations

The following items are not yet confirmed as fully implemented:

1. Live dependency version checks and latest-version lookups.
2. Comprehensive vulnerability detection using security advisories.
3. Complete content analysis for every eligible source file.
4. Fully mature health scoring based on complete repository metrics.
5. Historical health-score comparison and trend visualization.
6. Automated orchestration of every analysis stage.
7. Production-grade authentication, authorization, and deployment hardening.

These limitations should be revisited as the backend evolves.

---

## Roadmap

### Phase 1 — Core Repository Analysis

- [x] Repository registration and metadata persistence
- [x] Repository file inventory
- [x] Source-content analysis
- [x] Dependency manifest extraction
- [x] Repository health assessment
- [x] Analysis history retrieval endpoints
- [x] Content-analysis progress endpoint

### Phase 2 — Complete and Improve Analysis

- [ ] Complete content processing for large repositories
- [ ] Improve reliability and coverage of repository metrics
- [ ] Add live dependency version information
- [ ] Integrate vulnerability intelligence
- [ ] Improve health scoring and confidence calculations
- [ ] Add regression tests for large and incomplete repositories

### Phase 3 — Frontend Integration

- [ ] Integrate the Next.js dashboard
- [ ] Visualize repository health and analysis findings
- [ ] Display file statistics and content-analysis progress
- [ ] Add dependency tables and package-manager charts
- [ ] Display loading, error, empty, and partial-analysis states

### Phase 4 — Advanced Features

- [ ] Compare health assessments over time
- [ ] Add repository-to-repository comparisons
- [ ] Automate the analysis pipeline
- [ ] Improve caching and GitHub API usage
- [ ] Add scheduled re-analysis where appropriate

### Phase 5 — Production Readiness

- [ ] Audit authentication and authorization
- [ ] Add or verify rate limiting and request validation
- [ ] Improve structured logging and observability
- [ ] Add API documentation
- [ ] Introduce database migrations
- [ ] Add deployment configuration and CI/CD
- [ ] Verify secrets management and production security

---

## Security

Follow these security practices when developing or deploying RepoMind:

- Never commit GitHub tokens or database credentials.
- Use environment variables or a secrets manager for sensitive values.
- Keep production database credentials separate from development credentials.
- Restrict access to development-only endpoints.
- Review CORS settings before connecting a public frontend.
- Validate repository URLs and request parameters.
- Handle GitHub rate limits and upstream errors safely.
- Review authentication and authorization before exposing analysis APIs publicly.
- Avoid logging access tokens or sensitive configuration values.

A GitHub personal access token should have only the permissions required by the features being used.

---

## Contributing

Contributions and improvements are welcome.

Suggested contribution workflow:

1. Fork the repository or create a feature branch.
2. Make a focused change.
3. Add or update tests.
4. Run the Maven build.
5. Review API compatibility and database changes.
6. Submit a pull request describing the change.

Example:

```bash
git checkout -b feature/dependency-security
mvn clean package
git add .
git commit -m "Add dependency security analysis"
git push origin feature/dependency-security
```

Do not include generated credentials, local configuration secrets, or private repository data in commits.

---

## License

Add the project's chosen license before distributing the code publicly. Until a license is explicitly included, do not assume that the repository grants general permission to reuse, modify, or redistribute its contents.

---

**RepoMind — Turning GitHub repository data into actionable engineering insights.**
