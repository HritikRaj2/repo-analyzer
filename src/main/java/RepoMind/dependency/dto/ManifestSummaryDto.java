package RepoMind.dependency.dto;

public record ManifestSummaryDto(
        String manifestPath,
        String packageManager,
        int dependencyCount,
        String status,
        String message
) {
}
