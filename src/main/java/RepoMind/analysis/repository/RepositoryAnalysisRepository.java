package RepoMind.analysis.repository;

import RepoMind.analysis.model.AnalysisStatus;
import RepoMind.analysis.model.RepositoryAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RepositoryAnalysisRepository extends JpaRepository<RepositoryAnalysis, Long> {
    List<RepositoryAnalysis> findByRepositoryId(Long repositoryId);
    Optional<RepositoryAnalysis> findTopByRepositoryIdOrderByStartedAtDesc(Long repositoryId);

    List<RepositoryAnalysis> findByStatus(AnalysisStatus status);
}
