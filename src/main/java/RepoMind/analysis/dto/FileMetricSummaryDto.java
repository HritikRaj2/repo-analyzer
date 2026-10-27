package RepoMind.analysis.dto;

public record FileMetricSummaryDto(
        String path,
        String fileName,
        Long size,
        Integer linesOfCode,
        String language,
        Boolean isTestFile
) {
}
