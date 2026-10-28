package RepoMind.dependency.service;

import RepoMind.dependency.dto.DependencyAnalysisResponse;
import RepoMind.dependency.dto.DependencyDto;
import RepoMind.dependency.dto.DependencyListResponse;
import RepoMind.dependency.dto.ManifestSummaryDto;
import RepoMind.dependency.model.Dependency;
import RepoMind.dependency.parser.DependencyParser;
import RepoMind.dependency.parser.ParsedDependency;
import RepoMind.dependency.repository.DependencyRepository;
import RepoMind.repository.client.GitHubClient;
import RepoMind.repository.client.GitHubClient.GitHubNotFoundException;
import RepoMind.repository.client.GitHubClient.GitHubRateLimitException;
import RepoMind.repository.model.Repository;
import RepoMind.repository.model.RepositoryFile;
import RepoMind.repository.repository.RepositoryFileRepository;
import RepoMind.repository.repository.RepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service managing manifest discovery, content retrieval, dependency parsing,
 * and safe persistence.
 */
@Service
public class DependencyService {

    private static final Logger log = LoggerFactory.getLogger(DependencyService.class);

    private final RepositoryRepository repositoryRepository;
    private final RepositoryFileRepository repositoryFileRepository;
    private final DependencyRepository dependencyRepository;
    private final GitHubClient gitHubClient;
    private final List<DependencyParser> parsers;
    private final TransactionTemplate transactionTemplate;
    private final RepoMind.repository.service.RepositoryService repositoryService;

    public DependencyService(
            RepositoryRepository repositoryRepository,
            RepositoryFileRepository repositoryFileRepository,
            DependencyRepository dependencyRepository,
            GitHubClient gitHubClient,
            List<DependencyParser> parsers,
            PlatformTransactionManager transactionManager,
            @org.springframework.context.annotation.Lazy RepoMind.repository.service.RepositoryService repositoryService
    ) {
        this.repositoryRepository = repositoryRepository;
        this.repositoryFileRepository = repositoryFileRepository;
        this.dependencyRepository = dependencyRepository;
        this.gitHubClient = gitHubClient;
        this.parsers = parsers;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.repositoryService = repositoryService;
    }

    /**
     * Executes dependency analysis for a repository.
     *
     * <ol>
     *   <li>Validates that the repository exists in the database.</li>
     *   <li>Scans persisted file records for known manifests.</li>
     *   <li>Fetches manifest contents from GitHub via {@link GitHubClient}.</li>
     *   <li>Parses dependencies into normalized models.</li>
     *   <li>Safely updates persisted dependencies without erasing existing data on network failures.</li>
     * </ol>
     */
    public DependencyAnalysisResponse analyzeDependencies(Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found with ID: " + repositoryId));

        List<RepositoryFile> allFiles = repositoryFileRepository.findByRepositoryId(repositoryId);
        if (allFiles.isEmpty()) {
            try {
                repositoryService.analyzeRepositoryFiles(repositoryId);
                allFiles = repositoryFileRepository.findByRepositoryId(repositoryId);
            } catch (Exception ex) {
                log.warn("Auto-syncing files during dependency analysis failed: {}", ex.getMessage());
            }
        }

        if (allFiles.isEmpty()) {
            throw new IllegalStateException("Repository file inventory is empty. Please run file analysis first.");
        }

        List<RepositoryFile> supportedManifests = new ArrayList<>();
        List<String> unsupportedManifests = new ArrayList<>();

        // 1. Identify supported vs unsupported manifests from file inventory
        for (RepositoryFile file : allFiles) {
            String fileName = file.getFileName();
            if (fileName == null) {
                continue;
            }
            String lowerName = fileName.toLowerCase(Locale.ROOT);

            if (isSupportedManifest(file.getPath())) {
                supportedManifests.add(file);
            } else if (isKnownUnsupportedManifest(lowerName)) {
                unsupportedManifests.add(file.getPath());
            }
        }

        List<ManifestSummaryDto> manifestSummaries = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<ParsedDependency> allParsedDependencies = new ArrayList<>();

        // 2. Fetch and parse manifests outside broad database transactions
        for (RepositoryFile manifestFile : supportedManifests) {
            DependencyParser parser = findParser(manifestFile.getPath());
            if (parser == null) {
                unsupportedManifests.add(manifestFile.getPath());
                continue;
            }

            try {
                String content = gitHubClient.getFileContent(
                        repository.getOwner(),
                        repository.getName(),
                        repository.getDefaultBranch(),
                        manifestFile.getPath()
                );

                if (content == null || content.isBlank()) {
                    manifestSummaries.add(new ManifestSummaryDto(
                            manifestFile.getPath(),
                            parser.getPackageManager(),
                            0,
                            "EMPTY_CONTENT",
                            "Manifest file content was empty"
                    ));
                    continue;
                }

                List<ParsedDependency> parsed = parser.parse(content, manifestFile.getPath());
                allParsedDependencies.addAll(parsed);

                manifestSummaries.add(new ManifestSummaryDto(
                        manifestFile.getPath(),
                        parser.getPackageManager(),
                        parsed.size(),
                        "SUCCESS",
                        "Successfully parsed " + parsed.size() + " dependencies"
                ));

            } catch (GitHubRateLimitException ex) {
                String msg = "Rate limit exceeded while fetching " + manifestFile.getPath() + ": " + ex.getMessage();
                log.warn(msg);
                errors.add(msg);
                manifestSummaries.add(new ManifestSummaryDto(
                        manifestFile.getPath(),
                        parser.getPackageManager(),
                        0,
                        "RATE_LIMITED",
                        ex.getMessage()
                ));
                // Stop further network requests if rate limit is exhausted
                break;

            } catch (GitHubNotFoundException ex) {
                String msg = "Manifest not found on GitHub: " + manifestFile.getPath();
                log.info(msg);
                errors.add(msg);
                manifestSummaries.add(new ManifestSummaryDto(
                        manifestFile.getPath(),
                        parser.getPackageManager(),
                        0,
                        "NOT_FOUND",
                        ex.getMessage()
                ));

            } catch (RestClientException ex) {
                String msg = "Network error fetching " + manifestFile.getPath() + ": " + ex.getMessage();
                log.warn(msg);
                errors.add(msg);
                manifestSummaries.add(new ManifestSummaryDto(
                        manifestFile.getPath(),
                        parser.getPackageManager(),
                        0,
                        "NETWORK_ERROR",
                        ex.getMessage()
                ));

            } catch (Exception ex) {
                String msg = "Failed to parse manifest " + manifestFile.getPath() + ": " + ex.getMessage();
                log.error(msg, ex);
                errors.add(msg);
                manifestSummaries.add(new ManifestSummaryDto(
                        manifestFile.getPath(),
                        parser.getPackageManager(),
                        0,
                        "PARSE_ERROR",
                        ex.getMessage()
                ));
            }
        }

        // 3. Persist results safely: if at least one manifest succeeded or no manifests found
        List<Dependency> savedEntities = Collections.emptyList();
        boolean hasSuccessfulParse = manifestSummaries.stream().anyMatch(m -> "SUCCESS".equals(m.status()));

        if (hasSuccessfulParse || supportedManifests.isEmpty()) {
            savedEntities = saveParsedDependencies(repository, allParsedDependencies);
        } else {
            // Keep existing dependencies if all attempted manifest fetches/parses failed
            log.warn("Preserving existing dependencies because all manifest parsing operations failed.");
            savedEntities = dependencyRepository.findByRepositoryId(repositoryId);
        }

        // 4. Build response summaries and breakdowns
        Map<String, Long> packageManagerBreakdown = savedEntities.stream()
                .filter(d -> d.getPackageManager() != null)
                .collect(Collectors.groupingBy(Dependency::getPackageManager, Collectors.counting()));

        Map<String, Long> scopeBreakdown = savedEntities.stream()
                .filter(d -> d.getScope() != null)
                .collect(Collectors.groupingBy(Dependency::getScope, Collectors.counting()));

        List<DependencyDto> dtoList = savedEntities.stream()
                .map(this::toDto)
                .toList();

        return new DependencyAnalysisResponse(
                repository.getId(),
                repository.getFullName(),
                supportedManifests.size(),
                manifestSummaries,
                dtoList.size(),
                packageManagerBreakdown,
                scopeBreakdown,
                unsupportedManifests,
                errors,
                dtoList
        );
    }

    /**
     * Atomically replaces repository dependencies with freshly parsed dependencies.
     */
    protected List<Dependency> saveParsedDependencies(Repository repository, List<ParsedDependency> parsed) {
        return transactionTemplate.execute(status -> {
            // Clear previous dependencies for this repository to ensure idempotency
            dependencyRepository.deleteByRepositoryId(repository.getId());

            if (parsed.isEmpty()) {
                return Collections.emptyList();
            }

            // Deduplicate effective declarations within the same manifest and scope
            Map<String, Dependency> distinctDeps = new LinkedHashMap<>();

            for (ParsedDependency p : parsed) {
                String key = p.manifestPath() + "|" + p.name() + "|" + p.scope();
                if (!distinctDeps.containsKey(key)) {
                    Dependency entity = new Dependency();
                    entity.setRepository(repository);
                    entity.setName(p.name());
                    entity.setVersion(p.version());
                    // Conservative: do not fabricate latest version, outdated status, or vulnerability status
                    entity.setLatestVersion(null);
                    entity.setPackageManager(p.packageManager());
                    entity.setDirectDependency(p.directDependency());
                    entity.setOutdated(null);
                    entity.setVulnerable(null);
                    entity.setScope(p.scope());
                    entity.setManifestPath(p.manifestPath());
                    distinctDeps.put(key, entity);
                }
            }

            return dependencyRepository.saveAll(new ArrayList<>(distinctDeps.values()));
        });
    }

    /**
     * Retrieves paginated dependencies for a repository.
     */
    @Transactional(readOnly = true)
    public DependencyListResponse getDependencies(Long repositoryId, Pageable pageable) {
        if (!repositoryRepository.existsById(repositoryId)) {
            throw new IllegalArgumentException("Repository not found with ID: " + repositoryId);
        }

        Page<Dependency> page = dependencyRepository.findByRepositoryId(repositoryId, pageable);

        Map<String, Long> packageManagerBreakdown = dependencyRepository.findByRepositoryId(repositoryId).stream()
                .filter(d -> d.getPackageManager() != null)
                .collect(Collectors.groupingBy(Dependency::getPackageManager, Collectors.counting()));

        List<DependencyDto> dtoList = page.getContent().stream()
                .map(this::toDto)
                .toList();

        return new DependencyListResponse(
                repositoryId,
                page.getTotalElements(),
                page.getNumber(),
                page.getSize(),
                page.getTotalPages(),
                packageManagerBreakdown,
                dtoList
        );
    }

    private DependencyParser findParser(String path) {
        for (DependencyParser parser : parsers) {
            if (parser.supports(path)) {
                return parser;
            }
        }
        return null;
    }

    private boolean isSupportedManifest(String path) {
        return findParser(path) != null;
    }

    private boolean isKnownUnsupportedManifest(String fileName) {
        return switch (fileName) {
            case "cargo.toml", "requirements.txt", "pipfile", "pyproject.toml",
                 "go.mod", "gemfile", "composer.json" -> true;
            default -> false;
        };
    }

    private DependencyDto toDto(Dependency dep) {
        return new DependencyDto(
                dep.getId(),
                dep.getName(),
                dep.getVersion(),
                dep.getLatestVersion(),
                dep.getPackageManager(),
                dep.getScope(),
                dep.getDirectDependency(),
                dep.getOutdated(),
                dep.getVulnerable(),
                dep.getManifestPath()
        );
    }
}
