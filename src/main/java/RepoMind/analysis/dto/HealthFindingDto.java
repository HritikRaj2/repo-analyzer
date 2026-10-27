package RepoMind.analysis.dto;

public record HealthFindingDto(
        String id,
        String category,
        String severity,
        String title,
        String evidence,
        String recommendation
) {
}
