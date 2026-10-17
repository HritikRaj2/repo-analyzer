package RepoMind.repository.dto;

public record RepositoryFileAnalysisResponse(
        Long repositoryId,
        String repositoryName,
        int filesSaved,
        long sourceFiles,
        long testFiles,
        boolean truncated
) {
}

