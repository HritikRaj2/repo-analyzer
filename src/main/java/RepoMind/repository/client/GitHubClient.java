package RepoMind.repository.client;


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

    public String getRepository(String owner, String repository) {

        return restClient.get()
                .uri("/repos/{owner}/{repository}", owner, repository)
                .retrieve()
                .body(String.class);
    }
}
