package RepoMind.repository.client;

import RepoMind.repository.dto.GitHubRepositoryResponse;
import RepoMind.repository.dto.GitHubTreeResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GitHubClient {

    private final RestClient restClient;

    public GitHubClient() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.github.com")
                .build();
    }

    public GitHubRepositoryResponse getRepository(
            String owner,
            String repository
    ) {
        return restClient.get()
                .uri("/repos/{owner}/{repository}", owner, repository)
                .retrieve()
                .body(GitHubRepositoryResponse.class);
    }

    public GitHubTreeResponse getRepositoryTree(
            String owner,
            String repository,
            String branch
    ) {
        return restClient.get()
                .uri(
                        "/repos/{owner}/{repository}/git/trees/{branch}?recursive=1",
                        owner,
                        repository,
                        branch
                )
                .retrieve()
                .body(GitHubTreeResponse.class);
    }
}
