package RepoMind.dependency.dto;

import java.util.List;
import java.util.Map;

public record DependencyAnalysisResponse(
        Long repositoryId,
        String repositoryName,
        int totalManifestsFound,
        List<ManifestSummaryDto> manifestsProcessed,
        int totalDependencies,
        Map<String, Long> packageManagerBreakdown,
        Map<String, Long> scopeBreakdown,
        List<String> unsupportedManifests,
        List<String> errors,
        List<DependencyDto> dependencies
) {
}
