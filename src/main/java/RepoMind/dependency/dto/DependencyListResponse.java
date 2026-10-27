package RepoMind.dependency.dto;

import java.util.List;
import java.util.Map;

public record DependencyListResponse(
        Long repositoryId,
        long totalCount,
        int page,
        int size,
        int totalPages,
        Map<String, Long> packageManagerBreakdown,
        List<DependencyDto> dependencies
) {
}
