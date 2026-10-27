package RepoMind.dependency.repository;

import RepoMind.dependency.model.Dependency;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DependencyRepository extends JpaRepository<Dependency, Long> {
    List<Dependency> findByRepositoryId(Long repositoryId);
    Page<Dependency> findByRepositoryId(Long repositoryId, Pageable pageable);
    List<Dependency> findByRepositoryIdAndPackageManager(Long repositoryId, String packageManager);

    List<Dependency> findByRepositoryIdAndOutdatedTrue(Long repositoryId);

    List<Dependency> findByRepositoryIdAndVulnerableTrue(Long repositoryId);

    long countByRepositoryId(Long repositoryId);

    void deleteByRepositoryId(Long repositoryId);

    void deleteByRepositoryIdAndManifestPath(Long repositoryId, String manifestPath);
}
