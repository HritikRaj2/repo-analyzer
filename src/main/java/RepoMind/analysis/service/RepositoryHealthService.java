package RepoMind.analysis.service;

import RepoMind.analysis.analyzer.HealthScoreCalculator;
import RepoMind.analysis.analyzer.HealthScoreCalculator.CalculationResult;
import RepoMind.analysis.dto.HealthFindingDto;
import RepoMind.analysis.dto.HealthMetricsDto;
import RepoMind.analysis.dto.RepositoryHealthAnalysisResponse;
import RepoMind.analysis.model.AnalysisStatus;
import RepoMind.analysis.model.RepositoryAnalysis;
import RepoMind.analysis.repository.RepositoryAnalysisRepository;
import RepoMind.dependency.model.Dependency;
import RepoMind.dependency.repository.DependencyRepository;
import RepoMind.repository.model.Repository;
import RepoMind.repository.model.RepositoryFile;
import RepoMind.repository.repository.RepositoryFileRepository;
import RepoMind.repository.repository.RepositoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing the lifecycle, metric calculation, scoring, and persistence
 * of repository health analyses.
 */
@Service
public class RepositoryHealthService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryHealthService.class);

    private final RepositoryRepository repositoryRepository;
    private final RepositoryFileRepository repositoryFileRepository;
    private final DependencyRepository dependencyRepository;
    private final RepositoryAnalysisRepository repositoryAnalysisRepository;
    private final HealthScoreCalculator healthScoreCalculator;

    private final DependencyService dependencyService;

    // In-memory cache keyed by analysis ID to prevent re-querying thousands of file records on every read poll
    private final Map<Long, CalculationResult> analysisCache = new ConcurrentHashMap<>();

    public RepositoryHealthService(
            RepositoryRepository repositoryRepository,
            RepositoryFileRepository repositoryFileRepository,
            DependencyRepository dependencyRepository,
            RepositoryAnalysisRepository repositoryAnalysisRepository,
            HealthScoreCalculator healthScoreCalculator,
            @org.springframework.context.annotation.Lazy DependencyService dependencyService
    ) {
        this.repositoryRepository = repositoryRepository;
        this.repositoryFileRepository = repositoryFileRepository;
        this.dependencyRepository = dependencyRepository;
        this.repositoryAnalysisRepository = repositoryAnalysisRepository;
        this.healthScoreCalculator = healthScoreCalculator;
        this.dependencyService = dependencyService;
    }

    /**
     * Executes repository health analysis end-to-end:
     * PENDING -> IN_PROGRESS -> COMPLETED (or FAILED).
     */
    @Transactional
    public RepositoryHealthAnalysisResponse analyzeRepositoryHealth(Long repositoryId) {
        Repository repository = repositoryRepository.findById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found with ID: " + repositoryId));

        // 1. Create analysis record in PENDING state
        RepositoryAnalysis analysis = new RepositoryAnalysis();
        analysis.setRepository(repository);
        analysis.setStatus(AnalysisStatus.PENDING);
        analysis.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
        analysis = repositoryAnalysisRepository.save(analysis);

        // 2. Transition to IN_PROGRESS
        analysis.setStatus(AnalysisStatus.IN_PROGRESS);
        analysis = repositoryAnalysisRepository.save(analysis);

        try {
            // 3. Load persisted inventory and dependency records
            List<RepositoryFile> files = repositoryFileRepository.findByRepositoryId(repositoryId);
            List<Dependency> dependencies = dependencyRepository.findByRepositoryId(repositoryId);

            if (dependencies.isEmpty() && files.stream().anyMatch(f -> isManifest(f.getPath()))) {
                try {
                    dependencyService.analyzeDependencies(repositoryId);
                    dependencies = dependencyRepository.findByRepositoryId(repositoryId);
                } catch (Exception ex) {
                    log.warn("Auto-ingesting dependencies during health analysis failed: {}", ex.getMessage());
                }
            }

            boolean dependenciesAnalyzed = !dependencies.isEmpty()
                    || (files.stream().anyMatch(f -> isManifest(f.getPath())) && dependencyRepository.countByRepositoryId(repositoryId) > 0);

            // 4. Calculate metrics, completeness, and transparent health score
            CalculationResult result = healthScoreCalculator.calculate(
                    repository,
                    files,
                    dependencies,
                    dependenciesAnalyzed
            );

            // 5. Update analysis entity with metrics and score
            HealthMetricsDto metrics = result.metrics();
            analysis.setTotalFiles(metrics.totalFiles());
            analysis.setTotalDirectories(metrics.totalDirectories());
            analysis.setSourceFiles(metrics.sourceFiles());
            analysis.setTestFiles(metrics.testFiles());
            analysis.setTotalLinesOfCode(metrics.totalLinesOfCode());
            analysis.setHealthScore(result.score());
            analysis.setHealthGrade(result.grade());
            analysis.setHealthConfidence(result.confidence());
            analysis.setDependencyCount(metrics.totalDependencies());

            // 6. Transition to COMPLETED
            analysis.setStatus(AnalysisStatus.COMPLETED);
            analysis.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
            analysis.setErrorMessage(null);
            analysis = repositoryAnalysisRepository.save(analysis);

            // Cache the result for subsequent instant GET lookups
            analysisCache.put(analysis.getId(), result);

            return toResponse(
                    analysis,
                    repository.getId(),
                    repository.getFullName(),
                    result.confidenceExplanation(),
                    result.partialAssessment(),
                    result.assessmentScope(),
                    metrics,
                    result.findings()
            );

        } catch (Exception ex) {
            log.error("Repository health analysis failed for repository ID {}: {}", repositoryId, ex.getMessage(), ex);
            analysis.setStatus(AnalysisStatus.FAILED);
            analysis.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
            analysis.setErrorMessage(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
            repositoryAnalysisRepository.save(analysis);

            throw new IllegalStateException("Health analysis failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Retrieves the latest analysis for a given repository.
     * Automatically computes initial health analysis or refreshes stale 0-file analyses if files now exist.
     */
    @Transactional
    public RepositoryHealthAnalysisResponse getLatestAnalysis(Long repositoryId) {
        if (!repositoryRepository.existsById(repositoryId)) {
            throw new IllegalArgumentException("Repository not found with ID: " + repositoryId);
        }

        var analysisOpt = repositoryAnalysisRepository
                .findTopByRepositoryIdOrderByStartedAtDesc(repositoryId);

        if (analysisOpt.isEmpty()
                || (analysisOpt.get().getTotalFiles() != null && analysisOpt.get().getTotalFiles() == 0
                    && repositoryFileRepository.countByRepositoryId(repositoryId) > 0)) {
            return analyzeRepositoryHealth(repositoryId);
        }

        return buildResponseFromEntity(analysisOpt.get());
    }

    /**
     * Retrieves a specific analysis record by its primary key ID.
     */
    @Transactional(readOnly = true)
    public RepositoryHealthAnalysisResponse getAnalysisById(Long analysisId) {
        RepositoryAnalysis analysis = repositoryAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis record not found with ID: " + analysisId));

        return buildResponseFromEntity(analysis);
    }

    private RepositoryHealthAnalysisResponse buildResponseFromEntity(RepositoryAnalysis analysis) {
        Long repoId = analysis.getRepository() != null ? analysis.getRepository().getId() : null;
        Repository repo = null;
        if (repoId != null) {
            repo = repositoryRepository.findById(repoId).orElse(null);
        }

        String repoFullName = repo != null
                ? repo.getFullName()
                : (analysis.getRepository() != null ? analysis.getRepository().getFullName() : null);

        if (analysis.getStatus() == AnalysisStatus.FAILED || repo == null) {
            return toResponse(
                    analysis,
                    repoId,
                    repoFullName,
                    "Analysis failed or incomplete.",
                    true,
                    "PRELIMINARY_STRUCTURAL",
                    null,
                    Collections.emptyList()
            );
        }

        final Repository finalRepo = repo;
        CalculationResult result = analysisCache.computeIfAbsent(analysis.getId(), id -> {
            List<RepositoryFile> files = repositoryFileRepository.findByRepositoryId(repoId);
            List<Dependency> dependencies = dependencyRepository.findByRepositoryId(repoId);
            boolean dependenciesAnalyzed = !dependencies.isEmpty()
                    || (files.stream().anyMatch(f -> isManifest(f.getPath())) && dependencyRepository.countByRepositoryId(repoId) > 0);

            return healthScoreCalculator.calculate(
                    finalRepo,
                    files,
                    dependencies,
                    dependenciesAnalyzed
            );
        });

        return toResponse(
                analysis,
                repo.getId(),
                repo.getFullName(),
                result.confidenceExplanation(),
                result.partialAssessment(),
                result.assessmentScope(),
                result.metrics(),
                result.findings()
        );
    }

    private RepositoryHealthAnalysisResponse toResponse(
            RepositoryAnalysis entity,
            Long repoId,
            String repoFullName,
            String confidenceExplanation,
            Boolean partialAssessment,
            String assessmentScope,
            HealthMetricsDto metrics,
            List<HealthFindingDto> findings
    ) {
        Long durationMs = null;
        if (entity.getStartedAt() != null && entity.getCompletedAt() != null) {
            durationMs = Duration.between(entity.getStartedAt(), entity.getCompletedAt()).toMillis();
        }

        return new RepositoryHealthAnalysisResponse(
                entity.getId(),
                repoId,
                repoFullName,
                entity.getStatus(),
                entity.getHealthScore(),
                entity.getHealthGrade(),
                entity.getHealthConfidence(),
                confidenceExplanation,
                entity.getStartedAt(),
                entity.getCompletedAt(),
                durationMs,
                metrics,
                findings,
                entity.getErrorMessage(),
                partialAssessment,
                assessmentScope
        );
    }

    private boolean isManifest(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith("pom.xml") || lower.endsWith("build.gradle")
                || lower.endsWith("build.gradle.kts") || lower.endsWith("package.json");
    }
}
