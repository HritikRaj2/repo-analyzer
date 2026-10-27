package RepoMind.dependency.dto;

public record DependencyDto(
        Long id,
        String name,
        String version,
        String latestVersion,
        String packageManager,
        String scope,
        Boolean directDependency,
        Boolean outdated,
        Boolean vulnerable,
        String manifestPath
) {
}
