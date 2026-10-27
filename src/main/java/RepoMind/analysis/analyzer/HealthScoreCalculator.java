package RepoMind.analysis.analyzer;

import RepoMind.analysis.dto.FileMetricSummaryDto;
import RepoMind.analysis.dto.HealthFindingDto;
import RepoMind.analysis.dto.HealthMetricsDto;
import RepoMind.dependency.model.Dependency;
import RepoMind.repository.model.Repository;
import RepoMind.repository.model.RepositoryFile;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Transparent, deterministic calculator for repository health metrics, completeness,
 * and explainable scores.
 *
 * <p>Separates measured facts from heuristics. Distinguishes preliminary structural
 * assessments from full comprehensive assessments. Does not fabricate test coverage,
 * cyclomatic complexity, or security vulnerabilities without underlying proof.
 */
@Component
public class HealthScoreCalculator {

    private static final long DEFAULT_MAX_FILE_SIZE_BYTES = 1_000_000L;

    public record CalculationResult(
            Double score,
            String grade,
            String confidence,
            String confidenceExplanation,
            Boolean partialAssessment,
            String assessmentScope,
            HealthMetricsDto metrics,
            List<HealthFindingDto> findings
    ) {
    }

    public CalculationResult calculate(
            Repository repository,
            List<RepositoryFile> files,
            List<Dependency> dependencies,
            boolean dependenciesAnalyzed
    ) {
        return calculate(repository, files, dependencies, dependenciesAnalyzed, DEFAULT_MAX_FILE_SIZE_BYTES);
    }

    public CalculationResult calculate(
            Repository repository,
            List<RepositoryFile> files,
            List<Dependency> dependencies,
            boolean dependenciesAnalyzed,
            long maxFileSizeBytes
    ) {
        List<HealthFindingDto> findings = new ArrayList<>();

        int totalFiles = files.size();
        int sourceFiles = 0;
        int testFiles = 0;
        long totalLinesOfCode = 0;
        int filesWithMeasuredLines = 0;
        int filesMissingLines = 0;
        int filesSkippedTooLarge = 0;
        int filesPermanentlySkipped = 0;

        Map<String, Long> languageDist = new HashMap<>();
        Map<String, Long> extensionDist = new HashMap<>();
        Set<String> directories = new HashSet<>();

        boolean hasReadme = false;
        boolean hasLicense = repository.getLicense() != null && !repository.getLicense().isBlank();
        boolean hasGitignore = false;

        for (RepositoryFile f : files) {
            String path = f.getPath();
            String lowerPath = path.toLowerCase(Locale.ROOT);
            String fileName = f.getFileName() != null ? f.getFileName().toLowerCase(Locale.ROOT) : "";

            // Directory tracking
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash > 0) {
                directories.add(path.substring(0, lastSlash));
            }

            // Documentation files detection — supports .md, .adoc, .rst, .txt, .markdown, etc.
            if (isReadmeFile(fileName)) {
                hasReadme = true;
            }
            if (isLicenseFile(fileName)) {
                hasLicense = true;
            }
            if (fileName.equals(".gitignore")) {
                hasGitignore = true;
            }

            // Language / Extension distribution
            if (f.getLanguage() != null && !f.getLanguage().isBlank()) {
                languageDist.merge(f.getLanguage(), 1L, Long::sum);
            }
            if (f.getExtension() != null && !f.getExtension().isBlank()) {
                extensionDist.merge(f.getExtension(), 1L, Long::sum);
            }

            // Source and content completeness categorization
            if (Boolean.TRUE.equals(f.getSourceFile())) {
                sourceFiles++;
                Integer loc = f.getLinesOfCode();
                if (loc != null && loc >= 0) {
                    filesWithMeasuredLines++;
                    totalLinesOfCode += loc;
                } else if (loc != null && loc < 0) {
                    // Sentinel -1 (404), -2 (unsupported/read error), etc.
                    filesPermanentlySkipped++;
                } else if (f.getSize() != null && f.getSize() > maxFileSizeBytes) {
                    filesSkippedTooLarge++;
                } else {
                    filesMissingLines++;
                }
            }

            if (Boolean.TRUE.equals(f.getTestFile())) {
                testFiles++;
            }
        }

        // Top 5 largest files
        List<FileMetricSummaryDto> largestFiles = files.stream()
                .filter(f -> f.getSize() != null && f.getSize() > 0)
                .sorted(Comparator.comparing(RepositoryFile::getSize, Comparator.reverseOrder()))
                .limit(5)
                .map(f -> new FileMetricSummaryDto(
                        f.getPath(),
                        f.getFileName(),
                        f.getSize(),
                        f.getLinesOfCode(),
                        f.getLanguage(),
                        f.getTestFile()
                ))
                .toList();

        // Source-to-test structural ratio
        Double testToSourceRatio = null;
        if (sourceFiles > 0) {
            testToSourceRatio = Math.round((testFiles / (double) sourceFiles) * 1000.0) / 1000.0;
        }

        // Dependency metrics
        Integer depCount = dependenciesAnalyzed ? dependencies.size() : null;
        Map<String, Long> packageManagerDist = dependenciesAnalyzed
                ? dependencies.stream().filter(d -> d.getPackageManager() != null)
                .collect(Collectors.groupingBy(Dependency::getPackageManager, Collectors.counting()))
                : Collections.emptyMap();

        Map<String, Long> scopeDist = dependenciesAnalyzed
                ? dependencies.stream().filter(d -> d.getScope() != null)
                .collect(Collectors.groupingBy(Dependency::getScope, Collectors.counting()))
                : Collections.emptyMap();

        // -------------------------------------------------------------
        // COMPLETENESS & CONFIDENCE CALCULATION
        // -------------------------------------------------------------
        long eligibleSourceFiles = sourceFiles - filesSkippedTooLarge - filesPermanentlySkipped;
        double contentCompletenessRatio = eligibleSourceFiles > 0
                ? (filesWithMeasuredLines / (double) eligibleSourceFiles)
                : 1.0;
        double contentCompletenessPercent = Math.round(contentCompletenessRatio * 1000.0) / 10.0;
        boolean contentAnalysisComplete = filesMissingLines == 0 && eligibleSourceFiles > 0;
        boolean partialAssessment = contentCompletenessRatio < 0.80;
        String assessmentScope = partialAssessment ? "PRELIMINARY_STRUCTURAL" : "FULL_ASSESSMENT";

        String confidence;
        String confidenceExplanation;
        if (totalFiles == 0) {
            confidence = "LOW";
            confidenceExplanation = "Repository file tree has not been ingested yet. Run file analysis first.";
        } else if (eligibleSourceFiles > 0 && contentCompletenessRatio < 0.25) {
            confidence = "LOW";
            confidenceExplanation = "Preliminary assessment: Only " + filesWithMeasuredLines + " of " + eligibleSourceFiles
                    + " eligible source files (" + contentCompletenessPercent + "%) analyzed for lines of code. "
                    + "Run content analysis to process remaining " + filesMissingLines + " files.";
        } else if (eligibleSourceFiles > 0 && contentCompletenessRatio < 0.80) {
            confidence = "MEDIUM";
            confidenceExplanation = "Partial assessment: " + filesWithMeasuredLines + " of " + eligibleSourceFiles
                    + " eligible source files (" + contentCompletenessPercent + "%) analyzed for lines of code.";
        } else {
            confidence = dependenciesAnalyzed ? "HIGH" : "MEDIUM";
            confidenceExplanation = dependenciesAnalyzed
                    ? "Comprehensive assessment: " + contentCompletenessPercent + "% of eligible source files analyzed for line counts, and dependency manifests are fully analyzed."
                    : "High content completeness (" + contentCompletenessPercent + "%), but dependency analysis has not been executed.";
        }

        // -------------------------------------------------------------
        // SCORING RUBRIC (Max 100 points)
        // -------------------------------------------------------------
        double docScore = 0.0;
        double testScore = 0.0;
        double activityScore = 0.0;
        double depScore = 0.0;

        // 1. Documentation & Community Health (Max 25 pts)
        if (hasReadme) {
            docScore += 10.0;
        } else {
            findings.add(new HealthFindingDto(
                    "DOC-001",
                    "DOCUMENTATION",
                    "WARNING",
                    "Missing README file",
                    "No README.md, README.adoc, or equivalent documentation file was discovered in repository root.",
                    "Add a clear README (such as README.md or README.adoc) describing project purpose, setup instructions, and usage."
            ));
        }

        if (hasLicense) {
            docScore += 10.0;
        } else {
            findings.add(new HealthFindingDto(
                    "DOC-002",
                    "DOCUMENTATION",
                    "WARNING",
                    "Missing open source LICENSE",
                    "Repository metadata and file inventory do not indicate an explicit software license.",
                    "Specify an open-source or proprietary LICENSE to clarify usage rights."
            ));
        }

        if (hasGitignore) {
            docScore += 5.0;
        } else {
            findings.add(new HealthFindingDto(
                    "DOC-003",
                    "DOCUMENTATION",
                    "INFO",
                    "Missing .gitignore",
                    "No .gitignore file detected in repository root.",
                    "Add a .gitignore tailored to your build tools to prevent committing build artifacts."
            ));
        }

        // 2. Automated Testing Infrastructure (Max 30 pts)
        if (sourceFiles == 0) {
            testScore = 15.0; // Non-code or documentation repo
        } else if (testFiles == 0) {
            testScore = 0.0;
            findings.add(new HealthFindingDto(
                    "TEST-001",
                    "TESTING",
                    "HIGH",
                    "No automated test files detected",
                    "Found " + sourceFiles + " source files but 0 test files matching common testing patterns.",
                    "Implement automated unit and integration tests to verify core application functionality."
            ));
        } else if (testToSourceRatio != null) {
            if (testToSourceRatio >= 0.25) {
                testScore = 30.0;
                findings.add(new HealthFindingDto(
                        "TEST-002",
                        "TESTING",
                        "INFO",
                        "Healthy test file ratio",
                        "Structural test-to-source ratio is " + testToSourceRatio + " (" + testFiles + " test files for " + sourceFiles + " source files). Measures repository structure only, not code coverage.",
                        "Maintain this testing practice and ensure critical execution paths are exercised."
                ));
            } else if (testToSourceRatio >= 0.10) {
                testScore = 20.0;
            } else {
                testScore = 10.0;
                findings.add(new HealthFindingDto(
                        "TEST-003",
                        "TESTING",
                        "WARNING",
                        "Low test file ratio",
                        "Structural test-to-source ratio is " + testToSourceRatio + " (" + testFiles + " test files for " + sourceFiles + " source files). Measures repository structure only, not code coverage.",
                        "Consider adding more unit test suites to reduce regression risk."
                ));
            }
        }

        // 3. Maintenance & Push Recency (Max 25 pts)
        LocalDateTime pushedAt = repository.getPushedAt();
        if (pushedAt != null) {
            long daysSincePush = ChronoUnit.DAYS.between(pushedAt, LocalDateTime.now(ZoneOffset.UTC));
            if (daysSincePush <= 60) {
                activityScore = 25.0;
            } else if (daysSincePush <= 180) {
                activityScore = 18.0;
            } else if (daysSincePush <= 365) {
                activityScore = 12.0;
            } else {
                activityScore = 5.0;
                findings.add(new HealthFindingDto(
                        "MAINT-001",
                        "MAINTENANCE",
                        "WARNING",
                        "Repository inactive for over 1 year",
                        "Last commit push was " + daysSincePush + " days ago (" + pushedAt + ").",
                        "Check for abandoned dependencies and outdated frameworks before building upon this project."
                ));
            }
        } else {
            activityScore = 15.0; // Unknown push date
        }

        // 4. Dependency Management (Max 20 pts)
        if (dependenciesAnalyzed) {
            if (!dependencies.isEmpty()) {
                depScore = 20.0;
                findings.add(new HealthFindingDto(
                        "DEP-001",
                        "DEPENDENCIES",
                        "INFO",
                        "Dependencies tracked",
                        "Discovered " + dependencies.size() + " declared dependencies across package managers: " + packageManagerDist.keySet() + ".",
                        "Regularly scan dependencies for security advisories and version updates."
                ));
            } else {
                depScore = 12.0;
                findings.add(new HealthFindingDto(
                        "DEP-002",
                        "DEPENDENCIES",
                        "INFO",
                        "No dependencies declared",
                        "No external dependencies found in supported manifest files.",
                        "If this project relies on external libraries, ensure manifests (pom.xml, build.gradle, package.json) are present."
                ));
            }
        } else {
            depScore = 10.0;
            findings.add(new HealthFindingDto(
                    "DEP-003",
                    "DEPENDENCIES",
                    "INFO",
                    "Dependency analysis pending",
                    "Dependency analysis has not been executed yet for this repository.",
                    "Run POST /api/repositories/" + repository.getId() + "/dependencies/analyze to ingest dependency metadata."
            ));
        }

        // 5. Incomplete Analysis Finding
        if (partialAssessment && eligibleSourceFiles > 0) {
            findings.add(new HealthFindingDto(
                    "METRIC-001",
                    "CODE_METRICS",
                    "WARNING",
                    "Content line-count analysis incomplete (" + contentCompletenessPercent + "% complete)",
                    "Only " + filesWithMeasuredLines + " of " + eligibleSourceFiles + " eligible source files have measured line counts (" + filesMissingLines + " pending).",
                    "Call POST /api/repositories/" + repository.getId() + "/content-analysis to progressively process remaining source files."
            ));
        }

        double totalScore = Math.min(100.0, Math.max(0.0, docScore + testScore + activityScore + depScore));
        totalScore = Math.round(totalScore * 10.0) / 10.0;

        String rawGrade = assignGrade(totalScore);
        String grade = (contentCompletenessRatio < 0.25 && eligibleSourceFiles > 0)
                ? rawGrade + " (Preliminary)"
                : rawGrade;

        HealthMetricsDto metricsDto = new HealthMetricsDto(
                totalFiles,
                directories.size(),
                sourceFiles,
                testFiles,
                testToSourceRatio,
                "Structural ratio of test files to source code files (" + testFiles + " test files for " + sourceFiles + " source files). This is a structural file indicator only and does NOT measure runtime test execution, line coverage, or branch coverage.",
                totalLinesOfCode,
                filesWithMeasuredLines,
                filesMissingLines,
                filesSkippedTooLarge,
                filesPermanentlySkipped,
                contentCompletenessPercent,
                contentAnalysisComplete,
                languageDist,
                extensionDist,
                largestFiles,
                dependenciesAnalyzed,
                depCount,
                packageManagerDist,
                scopeDist
        );

        return new CalculationResult(
                totalScore,
                grade,
                confidence,
                confidenceExplanation,
                partialAssessment,
                assessmentScope,
                metricsDto,
                findings
        );
    }

    private boolean isReadmeFile(String lowerName) {
        if (lowerName == null) return false;
        return lowerName.equals("readme")
                || lowerName.equals("readme.md")
                || lowerName.equals("readme.adoc")
                || lowerName.equals("readme.asciidoc")
                || lowerName.equals("readme.rst")
                || lowerName.equals("readme.txt")
                || lowerName.equals("readme.markdown")
                || lowerName.equals("readme.mdown")
                || lowerName.equals("readme.html")
                || lowerName.startsWith("readme.");
    }

    private boolean isLicenseFile(String lowerName) {
        if (lowerName == null) return false;
        return lowerName.equals("license")
                || lowerName.equals("license.md")
                || lowerName.equals("license.txt")
                || lowerName.equals("license.adoc")
                || lowerName.equals("copying")
                || lowerName.equals("copying.txt")
                || lowerName.equals("unlicense")
                || lowerName.startsWith("license.");
    }

    private String assignGrade(double score) {
        if (score >= 90.0) return "A";
        if (score >= 75.0) return "B";
        if (score >= 60.0) return "C";
        if (score >= 45.0) return "D";
        return "F";
    }
}
