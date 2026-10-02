package RepoMind.repository.repository;

import RepoMind.repository.model.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RepositoryRepository extends JpaRepository<Repository, Long> {
    Optional<Repository> findByGithubId(String githubId);
    boolean existsByGithubId(String githubId);
}
