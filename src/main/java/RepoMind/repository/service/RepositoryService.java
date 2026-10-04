package RepoMind.repository.service;

import RepoMind.repository.client.GitHubClient;
import org.springframework.stereotype.Service;

@Service
public class RepositoryService {

    private final GitHubClient gitHubClient;

    public RepositoryService(GitHubClient gitHubClient) {
        this.gitHubClient = gitHubClient;
    }

    public String analyzeRepository(String githubUrl) {

        System.out.println("Received GitHub URL: " + githubUrl);

        String owner = "spring-projects";
        String repository = "spring-boot";

        return gitHubClient.getRepository(owner, repository);
    }
}
