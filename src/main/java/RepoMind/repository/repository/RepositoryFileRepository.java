package RepoMind.repository.repository;

import RepoMind.repository.model.Repository;
import RepoMind.repository.model.RepositoryFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepositoryFileRepository extends JpaRepository<RepositoryFile, Long> {
    List<RepositoryFile> findByRepository(Repository repository);
    List<RepositoryFile> findByRepository(Long repositoryId);
    long countByRepositoryId(Long repositoryId);
}
