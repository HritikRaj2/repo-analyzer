package RepoMind.repository.controller;

import RepoMind.repository.client.GitHubClient;
import RepoMind.repository.dto.GitHubTreeResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/github")
public class GitHubTreeTestController {

    private final GitHubClient gitHubClient;

    public GitHubTreeTestController(GitHubClient gitHubClient) {
        this.gitHubClient = gitHubClient;
    }

    @GetMapping("/tree")
    public GitHubTreeResponse getTree(
            @RequestParam String owner,
            @RequestParam String repo,
            @RequestParam String branch
    ) {
        return gitHubClient.getRepositoryTree(owner, repo, branch);
    }
}
