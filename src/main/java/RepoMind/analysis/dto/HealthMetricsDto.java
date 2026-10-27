package RepoMind.analysis.dto;

import java.util.List;
import java.util.Map;

/**
 * Detailed metric breakdown for repository health analysis.
 */
public record HealthMetricsDto(
        Integer totalFiles,
        Integer totalDirectories,
        Integer sourceFiles,
        Integer testFiles,
        Double testToSourceRatio,
        String ratioExplanation,
        Long totalLinesOfCode,
        Integer filesWithMeasuredLines,
        Integer filesMissingLineCounts,
        Integer filesSkippedTooLarge,
        Integer filesPermanentlySkipped,
        Double contentCompletenessPercent,
        Boolean contentAnalysisComplete,
        Map<String, Long> languageDistribution,
        Map<String, Long> extensionDistribution,
        List<FileMetricSummaryDto> largestFiles,
        Boolean dependencyAnalysisCompleted,
        Integer totalDependencies,
        Map<String, Long> packageManagerDistribution,
        Map<String, Long> scopeDistribution
) {
    /**
     * Backward-compatible 18-parameter constructor.
     */
    public HealthMetricsDto(
            Integer totalFiles,
            Integer totalDirectories,
            Integer sourceFiles,
            Integer testFiles,
            Double testToSourceRatio,
            String ratioExplanation,
            Long totalLinesOfCode,
            Integer filesWithMeasuredLines,
            Integer filesMissingLineCounts,
            Integer filesSkippedTooLarge,
            Integer filesPermanentlySkipped,
            Map<String, Long> languageDistribution,
            Map<String, Long> extensionDistribution,
            List<FileMetricSummaryDto> largestFiles,
            Boolean dependencyAnalysisCompleted,
            Integer totalDependencies,
            Map<String, Long> packageManagerDistribution,
            Map<String, Long> scopeDistribution
    ) {
        this(
                totalFiles,
                totalDirectories,
                sourceFiles,
                testFiles,
                testToSourceRatio,
                ratioExplanation,
                totalLinesOfCode,
                filesWithMeasuredLines,
                filesMissingLineCounts,
                filesSkippedTooLarge,
                filesPermanentlySkipped,
                (sourceFiles != null && sourceFiles > 0 && filesWithMeasuredLines != null)
                        ? Math.round((filesWithMeasuredLines / (double) Math.max(1, sourceFiles - (filesSkippedTooLarge != null ? filesSkippedTooLarge : 0) - (filesPermanentlySkipped != null ? filesPermanentlySkipped : 0))) * 1000.0) / 10.0
                        : 0.0,
                filesMissingLineCounts != null && filesMissingLineCounts == 0,
                languageDistribution,
                extensionDistribution,
                largestFiles,
                dependencyAnalysisCompleted,
                totalDependencies,
                packageManagerDistribution,
                scopeDistribution
        );
    }
}
