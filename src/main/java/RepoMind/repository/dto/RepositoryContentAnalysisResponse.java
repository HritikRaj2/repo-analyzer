package RepoMind.repository.dto;

/**
 * Structured response for repository content analysis execution and progress reporting.
 *
 * @param repositoryId             primary key of the repository
 * @param repositoryName           full name of the repository (e.g. "owner/repo")
 * @param filesProcessed           number of source files processed in this batch execution
 * @param filesFailed              number of source files that encountered errors in this batch execution
 * @param filesSkippedTooLarge     total count of source files exceeding maximum file size threshold
 * @param filesRemaining           total eligible source files remaining to be analyzed
 * @param totalSourceFiles         total number of source files inventoried in the repository
 * @param totalProcessed           cumulative count of source files with verified line counts (> 0)
 * @param totalPermanentlySkipped  count of source files permanently excluded (e.g. 404 or unreadable)
 * @param percentComplete          overall percentage of eligible source files analyzed (0.0 to 100.0)
 * @param status                   progress status ("COMPLETED", "IN_PROGRESS", "RATE_LIMITED")
 * @param hasMore                  true if eligible source files still remain to be analyzed
 * @param rateLimitHit             true if execution stopped early due to GitHub API rate limiting
 * @param message                  human-readable progress summary and next steps
 */
public record RepositoryContentAnalysisResponse(
        Long repositoryId,
        String repositoryName,
        int filesProcessed,
        int filesFailed,
        long filesSkippedTooLarge,
        long filesRemaining,
        long totalSourceFiles,
        long totalProcessed,
        long totalPermanentlySkipped,
        double percentComplete,
        String status,
        boolean hasMore,
        boolean rateLimitHit,
        String message
) {
    /**
     * Backward-compatible constructor preserving the original 6-argument signature.
     */
    public RepositoryContentAnalysisResponse(
            Long repositoryId,
            String repositoryName,
            int filesProcessed,
            int filesFailed,
            long filesSkippedTooLarge,
            long filesRemaining
    ) {
        this(
                repositoryId,
                repositoryName,
                filesProcessed,
                filesFailed,
                filesSkippedTooLarge,
                filesRemaining,
                filesProcessed + filesRemaining + filesSkippedTooLarge,
                filesProcessed,
                0,
                (filesProcessed + filesRemaining > 0)
                        ? Math.round((filesProcessed / (double) (filesProcessed + filesRemaining)) * 1000.0) / 10.0
                        : 100.0,
                filesRemaining == 0 ? "COMPLETED" : "IN_PROGRESS",
                filesRemaining > 0,
                false,
                filesRemaining == 0
                        ? "Content analysis complete for all eligible source files."
                        : "Batch executed: " + filesProcessed + " processed, " + filesRemaining + " remaining."
        );
    }
}
