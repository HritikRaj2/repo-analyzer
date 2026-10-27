package RepoMind.repository.repository;

import RepoMind.repository.model.Repository;
import RepoMind.repository.model.RepositoryFile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepositoryFileRepository extends JpaRepository<RepositoryFile, Long> {

    // --- Existing methods (preserved) ---

    List<RepositoryFile> findByRepository(Repository repository);

    List<RepositoryFile> findByRepositoryId(Long repositoryId);

    long countByRepositoryId(Long repositoryId);

    void deleteByRepositoryId(Long repositoryId);

    // --- New methods for efficient content-analysis batching ---

    /**
     * Returns a paginated batch of source files that have not yet been
     * analyzed (linesOfCode is null) and are within the size limit.
     *
     * <p>Pass {@code Pageable.ofSize(batchSize)} to get the first batch.
     * Spring Data translates this to a single database query with a LIMIT clause,
     * so only {@code batchSize} rows are loaded into memory at a time, regardless
     * of how many total files the repository has.
     *
     * @param repositoryId  the repository's primary key
     * @param maxSizeBytes  maximum file size in bytes; files larger than this are excluded
     * @param pageable      controls the page size; use {@code Pageable.ofSize(n)}
     */
    List<RepositoryFile> findByRepositoryIdAndSourceFileTrueAndLinesOfCodeIsNullAndSizeLessThanEqual(
            Long repositoryId,
            Long maxSizeBytes,
            Pageable pageable
    );

    /**
     * Returns the number of source files that still need content analysis
     * (linesOfCode is null and size is within the configured limit).
     *
     * <p>This is a COUNT query — no file data is loaded into memory.
     * It replaces the second full {@code findByRepositoryId} call that
     * previously loaded all files just to count eligible ones.
     *
     * @param repositoryId  the repository's primary key
     * @param maxSizeBytes  maximum file size in bytes
     */
    long countByRepositoryIdAndSourceFileTrueAndLinesOfCodeIsNullAndSizeLessThanEqual(
            Long repositoryId,
            Long maxSizeBytes
    );

    /**
     * Returns the number of source files that are too large to be analyzed
     * and have not had their lines counted yet.
     *
     * <p>Used to compute the {@code filesSkippedTooLarge} metric without
     * loading any file entities into memory.
     *
     * @param repositoryId  the repository's primary key
     * @param minSizeBytes  size threshold; files strictly greater than this are counted
     */
    long countByRepositoryIdAndSourceFileTrueAndLinesOfCodeIsNullAndSizeGreaterThan(
            Long repositoryId,
            Long minSizeBytes
    );

    /**
     * Returns total source files for a repository.
     */
    long countByRepositoryIdAndSourceFileTrue(Long repositoryId);

    /**
     * Returns total source files with successfully measured line counts (linesOfCode >= minLoc).
     */
    long countByRepositoryIdAndSourceFileTrueAndLinesOfCodeGreaterThanEqual(Long repositoryId, Integer minLoc);

    /**
     * Returns total source files permanently skipped due to error or missing resource (linesOfCode < maxLoc, e.g. < 0).
     */
    long countByRepositoryIdAndSourceFileTrueAndLinesOfCodeLessThan(Long repositoryId, Integer maxLoc);
}
