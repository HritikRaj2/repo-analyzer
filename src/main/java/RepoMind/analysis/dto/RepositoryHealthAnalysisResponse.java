package RepoMind.analysis.dto;

import RepoMind.analysis.model.AnalysisStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Structured response DTO for repository health analysis, distinguishing
 * preliminary/partial structural assessments from full comprehensive evaluations.
 */
public record RepositoryHealthAnalysisResponse(
        Long analysisId,
        Long repositoryId,
        String repositoryName,
        AnalysisStatus status,
        Double healthScore,
        String healthGrade,
        String confidence,
        String confidenceExplanation,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        Long durationMs,
        HealthMetricsDto metrics,
        List<HealthFindingDto> findings,
        String errorMessage,
        Boolean partialAssessment,
        String assessmentScope
) {
    /**
     * Backward-compatible 14-parameter constructor.
     */
    public RepositoryHealthAnalysisResponse(
            Long analysisId,
            Long repositoryId,
            String repositoryName,
            AnalysisStatus status,
            Double healthScore,
            String healthGrade,
            String confidence,
            String confidenceExplanation,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            Long durationMs,
            HealthMetricsDto metrics,
            List<HealthFindingDto> findings,
            String errorMessage
    ) {
        this(
                analysisId,
                repositoryId,
                repositoryName,
                status,
                healthScore,
                healthGrade,
                confidence,
                confidenceExplanation,
                startedAt,
                completedAt,
                durationMs,
                metrics,
                findings,
                errorMessage,
                confidence == null || !confidence.equalsIgnoreCase("HIGH"),
                (confidence != null && confidence.equalsIgnoreCase("HIGH"))
                        ? "FULL_ASSESSMENT"
                        : "PRELIMINARY_STRUCTURAL"
        );
    }
}
