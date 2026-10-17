package RepoMind.repository.service;

import RepoMind.repository.client.GitHubClient;
import RepoMind.repository.dto.GitHubRepositoryResponse;
import RepoMind.repository.dto.GitHubTreeResponse;
import RepoMind.repository.dto.RepositoryFileAnalysisResponse;
import RepoMind.repository.model.Repository;
import RepoMind.repository.model.RepositoryFile;
import RepoMind.repository.repository.RepositoryFileRepository;
import RepoMind.repository.repository.RepositoryRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RepositoryService {

    private final GitHubClient gitHubClient;
    private final RepositoryRepository repositoryRepository;
    private final RepositoryFileRepository repositoryFileRepository;

    public RepositoryService(
            GitHubClient gitHubClient,
            RepositoryRepository repositoryRepository,
            RepositoryFileRepository repositoryFileRepository
    ) {
        this.gitHubClient = gitHubClient;
        this.repositoryRepository = repositoryRepository;
        this.repositoryFileRepository = repositoryFileRepository;
    }

    // --------------------------------------------------
    // 1. Analyze repository metadata and save it
    // --------------------------------------------------

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
                    0, repositoryName.length() - 4
            );
        }

        GitHubRepositoryResponse response =
                gitHubClient.getRepository(owner, repositoryName);

        String githubId = String.valueOf(response.id());

        Repository repository = repositoryRepository
                .findByGithubId(githubId)
                .orElseGet(Repository::new);

        repository.setGithubId(githubId);
        //repository.setOriginalUrl(cleanUrl);
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

        repository.setCreatedAt(toUtcLocalDateTime(response.createdAt()));
        repository.setUpdatedAt(toUtcLocalDateTime(response.updatedAt()));
        repository.setPushedAt(toUtcLocalDateTime(response.pushedAt()));
        repository.setAnalyzedAt(LocalDateTime.now(ZoneOffset.UTC));

        return repositoryRepository.save(repository);
    }

    // --------------------------------------------------
    // 2. Fetch repository file tree and save file metadata
    // --------------------------------------------------

    @Transactional
    public RepositoryFileAnalysisResponse analyzeRepositoryFiles(
            Long repositoryId
    ) {

        // Find the repository already saved in PostgreSQL
        Repository repository = repositoryRepository.findById(repositoryId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Repository not found: " + repositoryId
                        )
                );

        // Fetch the recursive file tree from GitHub
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

        // Do not replace existing records with incomplete tree data
        if (treeResponse.truncated()) {
            throw new IllegalStateException(
                    "GitHub returned a truncated file tree. "
                            + "Existing file records were not replaced."
            );
        }

        List<RepositoryFile> files = new ArrayList<>();

        // Process files only; skip directory entries
        for (GitHubTreeResponse.TreeItem item : treeResponse.tree()) {

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

            // File contents have not been downloaded yet
            file.setLinesOfCode(null);

            file.setTestFile(isTestFile(path, fileName));
            file.setSourceFile(isSourceFile(extension));

            files.add(file);
        }

        // Remove old records before saving the refreshed file inventory
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

    // --------------------------------------------------
    // 3. Extract file extension
    // --------------------------------------------------

    private String getExtension(String fileName) {

        int dotIndex = fileName.lastIndexOf('.');

        // Treat names such as .gitignore as extensionless
        if (dotIndex <= 0 || dotIndex == fileName.length() - 1) {
            return null;
        }

        return fileName.substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);
    }

    // --------------------------------------------------
    // 4. Infer language from file extension
    // --------------------------------------------------

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

    // --------------------------------------------------
    // 5. Identify likely test files
    // --------------------------------------------------

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

    // --------------------------------------------------
    // 6. Identify likely source-code files
    // --------------------------------------------------

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

    // --------------------------------------------------
    // 7. Convert GitHub timestamps to UTC
    // --------------------------------------------------

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
