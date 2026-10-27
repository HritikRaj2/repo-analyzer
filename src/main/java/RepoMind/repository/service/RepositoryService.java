package RepoMind.repository.service;

import RepoMind.common.config.GitHubProperties;
import RepoMind.repository.client.GitHubClient;
import RepoMind.repository.client.GitHubClient.GitHubNotFoundException;
import RepoMind.repository.client.GitHubClient.GitHubRateLimitException;
import RepoMind.repository.dto.GitHubRepositoryResponse;
import RepoMind.repository.dto.GitHubTreeResponse;
import RepoMind.repository.dto.RepositoryFileAnalysisResponse;
import RepoMind.repository.dto.RepositoryContentAnalysisResponse;
import RepoMind.repository.model.Repository;
import RepoMind.repository.model.RepositoryFile;
import RepoMind.repository.repository.RepositoryFileRepository;
import RepoMind.repository.repository.RepositoryRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RepositoryService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryService.class);

    private final GitHubClient gitHubClient;
    private final RepositoryRepository repositoryRepository;
    private final RepositoryFileRepository repositoryFileRepository;
    private final GitHubProperties gitHubProperties;

    public RepositoryService(
            GitHubClient gitHubClient,
            RepositoryRepository repositoryRepository,
            RepositoryFileRepository repositoryFileRepository,
            GitHubProperties gitHubProperties
    ) {
        this.gitHubClient = gitHubClient;
        this.repositoryRepository = repositoryRepository;
        this.repositoryFileRepository = repositoryFileRepository;
        this.gitHubProperties = gitHubProperties;
    }

    // ==================================================
    // 1. ANALYZE REPOSITORY METADATA
    // ==================================================

    @Transactional
    public Repository analyzeRepository(String githubUrl) {

        String cleanUrl = githubUrl.trim();

        while (cleanUrl.endsWith("/")) {
            cleanUrl = cleanUrl.substring(0, cleanUrl.length() - 1);
        }

        if (!cleanUrl.matches(
                "https?://(www\\.)?github\\.com/[^/]+/[^/]+"
        )) {
            throw new IllegalArgumentException(
                    "Invalid GitHub repository URL"
            );
        }

        String[] parts = cleanUrl.split("/");

        String owner = parts[3];
        String repositoryName = parts[4];

        if (repositoryName.endsWith(".git")) {
            repositoryName = repositoryName.substring(
                    0,
                    repositoryName.length() - 4
            );
        }

        GitHubRepositoryResponse response =
                gitHubClient.getRepository(owner, repositoryName);

        String githubId = String.valueOf(response.id());

        Repository repository = repositoryRepository
                .findByGithubId(githubId)
                .orElseGet(Repository::new);

        repository.setGithubId(githubId);
        repository.setOriginalUrl(cleanUrl);
        repository.setName(response.name());
        repository.setFullName(response.fullName());
        repository.setOwner(response.owner().login());
        repository.setDescription(response.description());
        repository.setHtmlUrl(response.htmlUrl());
        repository.setDefaultBranch(response.defaultBranch());
        repository.setStars(response.stars());
        repository.setForks(response.forks());
        repository.setOpenIssues(response.openIssues());
        repository.setPrimaryLanguage(response.language());

        if (response.license() != null) {
            repository.setLicense(response.license().name());
        } else {
            repository.setLicense(null);
        }

        repository.setCreatedAt(
                toUtcLocalDateTime(response.createdAt())
        );

        repository.setUpdatedAt(
                toUtcLocalDateTime(response.updatedAt())
        );

        repository.setPushedAt(
                toUtcLocalDateTime(response.pushedAt())
        );

        repository.setAnalyzedAt(
                LocalDateTime.now(ZoneOffset.UTC)
        );

        return repositoryRepository.save(repository);
    }

    // ==================================================
    // 2. ANALYZE AND SAVE REPOSITORY FILE TREE
    // ==================================================

    @Transactional
    public RepositoryFileAnalysisResponse analyzeRepositoryFiles(
            Long repositoryId
    ) {

        Repository repository = repositoryRepository
                .findById(repositoryId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Repository not found: " + repositoryId
                        )
                );

        GitHubTreeResponse treeResponse =
                gitHubClient.getRepositoryTree(
                        repository.getOwner(),
                        repository.getName(),
                        repository.getDefaultBranch()
                );

        if (treeResponse == null || treeResponse.tree() == null) {
            throw new IllegalStateException(
                    "GitHub returned an empty repository tree"
            );
        }

        // Protect the existing inventory from incomplete tree data.
        if (treeResponse.truncated()) {
            throw new IllegalStateException(
                    "GitHub returned a truncated file tree. "
                            + "Existing file records were not replaced."
            );
        }

        List<RepositoryFile> files = new ArrayList<>();

        for (GitHubTreeResponse.TreeItem item : treeResponse.tree()) {

            // GitHub uses "blob" for files and "tree" for directories.
            if (!"blob".equals(item.type())) {
                continue;
            }

            String path = item.path();

            if (path == null || path.isBlank()) {
                continue;
            }

            String fileName =
                    path.substring(path.lastIndexOf('/') + 1);

            String extension = getExtension(fileName);

            RepositoryFile file = new RepositoryFile();

            file.setRepository(repository);
            file.setPath(path);
            file.setFileName(fileName);
            file.setExtension(extension);
            file.setLanguage(getLanguage(extension));
            file.setSize(item.size());

            // Content analysis has not been completed yet.
            file.setLinesOfCode(null);

            file.setTestFile(isTestFile(path, fileName));
            file.setSourceFile(isSourceFile(extension));

            files.add(file);
        }

        // Replace the previous file inventory.
        repositoryFileRepository.deleteByRepositoryId(repositoryId);

        repositoryFileRepository.saveAll(files);

        long sourceCount = files.stream()
                .filter(file ->
                        Boolean.TRUE.equals(file.getSourceFile())
                )
                .count();

        long testCount = files.stream()
                .filter(file ->
                        Boolean.TRUE.equals(file.getTestFile())
                )
                .count();

        return new RepositoryFileAnalysisResponse(
                repository.getId(),
                repository.getFullName(),
                files.size(),
                sourceCount,
                testCount,
                treeResponse.truncated()
        );
    }

    // ==================================================
    // 3. ANALYZE FILE CONTENTS IN BATCHES
    // ==================================================

    /**
     * Fetches raw content for a paginated batch of un-analyzed source files,
     * counts their non-blank lines, and persists the result.
     *
     * <p>Call this endpoint repeatedly until {@code filesRemaining} reaches zero.
     * Each call processes at most {@code github.content-analysis-batch-size} files
     * (default 50) to stay within GitHub rate limits.
     *
     * <p>If the GitHub rate limit is exhausted mid-batch, processing stops
     * immediately and the response reports how many files were completed before
     * the limit was hit. Files that failed due to rate-limiting retain a null
     * {@code linesOfCode} and will be included in the next batch.
     *
     * <p>Files that return HTTP 404 (deleted or renamed since the tree was fetched)
     * are marked with {@code linesOfCode = -1} and permanently excluded from
     * future batches.
     */
    public RepositoryContentAnalysisResponse analyzeRepositoryContent(
            Long repositoryId
    ) {
        return analyzeRepositoryContent(repositoryId, null, 1);
    }

    /**
     * Executes progressive, bounded batches of source file line count analysis.
     *
     * @param repositoryId    the repository ID
     * @param customBatchSize optional batch size override (bounded between 1 and 200)
     * @param maxBatches      optional number of consecutive batches to execute in this call (bounded 1 to 20)
     */
    public RepositoryContentAnalysisResponse analyzeRepositoryContent(
            Long repositoryId,
            Integer customBatchSize,
            Integer maxBatches
    ) {
        final int batchSize = (customBatchSize != null && customBatchSize > 0)
                ? Math.min(customBatchSize, 200)
                : gitHubProperties.getContentAnalysisBatchSize();
        final int batchesLimit = (maxBatches != null && maxBatches > 0)
                ? Math.min(maxBatches, 20)
                : 1;
        final long maxFileSize = gitHubProperties.getMaxFileSizeBytes();

        Repository repository = repositoryRepository
                .findById(repositoryId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Repository not found: " + repositoryId
                        )
                );

        int totalBatchProcessed = 0;
        int totalBatchFailed = 0;
        boolean rateLimitHit = false;

        for (int b = 0; b < batchesLimit; b++) {
            List<RepositoryFile> batch =
                    repositoryFileRepository
                            .findByRepositoryIdAndSourceFileTrueAndLinesOfCodeIsNullAndSizeLessThanEqual(
                                    repositoryId,
                                    maxFileSize,
                                    Pageable.ofSize(batchSize)
                            );

            if (batch.isEmpty()) {
                break;
            }

            for (RepositoryFile file : batch) {
                try {
                    String content = gitHubClient.getFileContent(
                            repository.getOwner(),
                            repository.getName(),
                            repository.getDefaultBranch(),
                            file.getPath()
                    );

                    if (content == null) {
                        log.warn("Null content returned for file: {}", file.getPath());
                        file.setLinesOfCode(-2);
                        repositoryFileRepository.save(file);
                        totalBatchFailed++;
                        continue;
                    }

                    int nonBlankLines = (int) content.lines()
                            .filter(line -> !line.isBlank())
                            .count();

                    file.setLinesOfCode(nonBlankLines);
                    repositoryFileRepository.save(file);
                    totalBatchProcessed++;

                } catch (GitHubRateLimitException ex) {
                    log.warn("GitHub rate limit hit during content analysis for repository {}: {}",
                            repositoryId, ex.getMessage());
                    rateLimitHit = true;
                    break;

                } catch (GitHubNotFoundException ex) {
                    log.info("File not found on GitHub, marking as permanently skipped: {}", file.getPath());
                    file.setLinesOfCode(-1);
                    repositoryFileRepository.save(file);
                    totalBatchFailed++;

                } catch (RestClientResponseException ex) {
                    if (ex.getStatusCode().is4xxClientError() && ex.getStatusCode().value() != 429) {
                        log.warn("Client error {} fetching file '{}', marking permanently skipped",
                                ex.getStatusCode().value(), file.getPath());
                        file.setLinesOfCode(-2);
                        repositoryFileRepository.save(file);
                    } else {
                        log.warn("Transient HTTP error fetching file '{}': {}",
                                file.getPath(), ex.getMessage());
                    }
                    totalBatchFailed++;

                } catch (RestClientException ex) {
                    log.warn("Network error fetching file '{}': {}",
                            file.getPath(), ex.getMessage());
                    totalBatchFailed++;

                } catch (Exception ex) {
                    log.warn("Permanent processing error for file '{}': {}", file.getPath(), ex.getMessage());
                    file.setLinesOfCode(-2);
                    repositoryFileRepository.save(file);
                    totalBatchFailed++;
                }
            }

            if (rateLimitHit) {
                break;
            }
        }

        return buildContentAnalysisResponse(repository, totalBatchProcessed, totalBatchFailed, rateLimitHit, maxFileSize);
    }

    /**
     * Checks current content analysis progress without executing any external GitHub requests.
     */
    @Transactional(readOnly = true)
    public RepositoryContentAnalysisResponse getContentAnalysisStatus(Long repositoryId) {
        Repository repository = repositoryRepository
                .findById(repositoryId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Repository not found: " + repositoryId
                        )
                );

        final long maxFileSize = gitHubProperties.getMaxFileSizeBytes();
        return buildContentAnalysisResponse(repository, 0, 0, false, maxFileSize);
    }

    private RepositoryContentAnalysisResponse buildContentAnalysisResponse(
            Repository repository,
            int batchProcessed,
            int batchFailed,
            boolean rateLimitHit,
            long maxFileSize
    ) {
        Long repositoryId = repository.getId();
        long totalSourceFiles = repositoryFileRepository.countByRepositoryIdAndSourceFileTrue(repositoryId);
        long totalProcessed = repositoryFileRepository.countByRepositoryIdAndSourceFileTrueAndLinesOfCodeGreaterThanEqual(repositoryId, 0);
        long remaining = repositoryFileRepository.countByRepositoryIdAndSourceFileTrueAndLinesOfCodeIsNullAndSizeLessThanEqual(repositoryId, maxFileSize);
        long skippedTooLarge = repositoryFileRepository.countByRepositoryIdAndSourceFileTrueAndLinesOfCodeIsNullAndSizeGreaterThan(repositoryId, maxFileSize);
        long permanentlySkipped = repositoryFileRepository.countByRepositoryIdAndSourceFileTrueAndLinesOfCodeLessThan(repositoryId, 0);

        long eligible = totalSourceFiles - skippedTooLarge - permanentlySkipped;
        double percentComplete = eligible > 0
                ? Math.round((totalProcessed / (double) eligible) * 1000.0) / 10.0
                : 100.0;
        percentComplete = Math.min(100.0, Math.max(0.0, percentComplete));

        String status;
        String message;
        if (remaining == 0) {
            status = "COMPLETED";
            message = "Content analysis is complete. All " + totalProcessed + " eligible source files have verified line counts.";
        } else if (rateLimitHit) {
            status = "RATE_LIMITED";
            message = "GitHub API rate limit reached. " + totalProcessed + " of " + eligible + " files processed (" + percentComplete + "%). Resume after rate limit reset.";
        } else {
            status = "IN_PROGRESS";
            message = "Analysis in progress: " + totalProcessed + " of " + eligible + " files analyzed (" + percentComplete + "% complete). " + remaining + " remaining.";
        }

        return new RepositoryContentAnalysisResponse(
                repository.getId(),
                repository.getFullName(),
                batchProcessed,
                batchFailed,
                skippedTooLarge,
                remaining,
                totalSourceFiles,
                totalProcessed,
                permanentlySkipped,
                percentComplete,
                status,
                remaining > 0,
                rateLimitHit,
                message
        );
    }

    // ==================================================
    // 4. EXTRACT FILE EXTENSION
    // ==================================================

    private String getExtension(String fileName) {

        int dotIndex = fileName.lastIndexOf('.');

        // Examples: .gitignore and LICENSE have no conventional extension.
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            return null;
        }

        return fileName.substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);
    }

    // ==================================================
    // 5. INFER LANGUAGE FROM FILE EXTENSION
    // ==================================================

    private String getLanguage(String extension) {

        if (extension == null) {
            return null;
        }

        return switch (extension) {
            case "java" -> "Java";
            case "kt", "kts" -> "Kotlin";
            case "py" -> "Python";
            case "js", "mjs", "cjs" -> "JavaScript";
            case "ts", "mts", "cts" -> "TypeScript";
            case "jsx", "tsx" -> "React";
            case "c" -> "C";
            case "h" -> "C/C++ Header";
            case "cpp", "cc", "cxx", "hpp" -> "C++";
            case "cs" -> "C#";
            case "go" -> "Go";
            case "rs" -> "Rust";
            case "php" -> "PHP";
            case "rb" -> "Ruby";
            case "swift" -> "Swift";
            case "scala" -> "Scala";
            case "groovy" -> "Groovy";
            case "sql" -> "SQL";
            case "html", "htm" -> "HTML";
            case "css", "scss", "sass", "less" -> "CSS";
            case "xml" -> "XML";
            case "json" -> "JSON";
            case "yml", "yaml" -> "YAML";
            case "md", "markdown" -> "Markdown";
            case "sh", "bash" -> "Shell";
            case "gradle" -> "Gradle";
            default -> null;
        };
    }

    // ==================================================
    // 6. IDENTIFY LIKELY TEST FILES
    // ==================================================

    private boolean isTestFile(String path, String fileName) {

        String normalizedPath = path.toLowerCase(Locale.ROOT);
        String normalizedName = fileName.toLowerCase(Locale.ROOT);

        return normalizedPath.contains("/test/")
                || normalizedPath.startsWith("test/")
                || normalizedPath.contains("/tests/")
                || normalizedPath.startsWith("tests/")
                || normalizedName.startsWith("test")
                || normalizedName.endsWith("test.java")
                || normalizedName.endsWith("tests.java")
                || normalizedName.endsWith("spec.js")
                || normalizedName.endsWith("spec.ts")
                || normalizedName.endsWith("test.py");
    }

    // ==================================================
    // 7. IDENTIFY SOURCE-CODE FILES
    // ==================================================

    private boolean isSourceFile(String extension) {

        if (extension == null) {
            return false;
        }

        return switch (extension) {
            case "java", "kt", "kts", "py",
                 "js", "mjs", "cjs", "ts", "mts", "cts",
                 "jsx", "tsx", "c", "h", "cpp", "cc",
                 "cxx", "hpp", "cs", "go", "rs", "php",
                 "rb", "swift", "scala", "groovy" -> true;

            default -> false;
        };
    }

    // ==================================================
    // 8. CONVERT GITHUB TIMESTAMPS TO UTC
    // ==================================================

    private LocalDateTime toUtcLocalDateTime(String timestamp) {

        if (timestamp == null) {
            return null;
        }

        return LocalDateTime.ofInstant(
                Instant.parse(timestamp),
                ZoneOffset.UTC
        );
    }
}
