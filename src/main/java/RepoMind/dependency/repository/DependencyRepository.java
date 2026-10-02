package RepoMind.dependency.repository;

import RepoMind.dependency.model.Dependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DependencyRepository extends JpaRepository<Dependency, Long> {
    List<Dependency> findByRepositoryId(Long repositoryId);
    List<Dependency> findByRepositoryIdAndPackageManager(Long repositoryId, String packageManager);

    List<Dependency> findByRepositoryIdAndOutdatedTrue(Long repositoryId);

    List<Dependency> findByRepositoryIdAndVulnerableTrue(Long repositoryId);

}
