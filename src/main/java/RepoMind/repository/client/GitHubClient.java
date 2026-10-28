package RepoMind.repository.client;

import RepoMind.common.config.GitHubProperties;
import RepoMind.repository.dto.GitHubRepositoryResponse;
import RepoMind.repository.dto.GitHubTreeResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HTTP client for the GitHub REST API and raw.githubusercontent.com.
 *
 * <h3>Authentication</h3>
 * When a GitHub Personal Access Token is configured via the {@code GITHUB_TOKEN}
 * environment variable, every request includes an {@code Authorization: Bearer}
 * header, raising the rate limit from 60 to 5000 requests per hour.
 *
 * <h3>Timeouts</h3>
 * Both connection and read timeouts are applied to every request. A request that
 * exceeds either limit throws {@link org.springframework.web.client.ResourceAccessException},
 * which the caller should treat as a transient failure.
 *
 * <h3>Error handling</h3>
 * GitHub-specific HTTP statuses (403/429 rate limit, 404 not found) are surfaced
 * as {@link GitHubRateLimitException} and {@link GitHubNotFoundException} so callers
 * can distinguish permanent failures from transient ones.
 */
@Component
public class GitHubClient {

    private final RestClient restClient;
    private final RestClient rawClient;

    public GitHubClient(GitHubProperties props) {

        // Build a shared HttpClient with connection and read timeouts and redirect following.
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
                .build();

        // Factory that wraps the JDK HttpClient so Spring's RestClient can use it.
        var httpClientFactory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        httpClientFactory.setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));

        // ---- GitHub API client (api.github.com) ----
        RestClient.Builder apiBuilder = RestClient.builder()
                .requestFactory(httpClientFactory)
                .baseUrl("https://api.github.com")
                .defaultHeader("Accept", "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader("User-Agent", "RepoMind-Analyzer");

        if (props.hasToken()) {
            apiBuilder.defaultHeader("Authorization", "Bearer " + props.getToken());
        }

        this.restClient = apiBuilder.build();

        // ---- Raw content client (raw.githubusercontent.com) ----
        RestClient.Builder rawBuilder = RestClient.builder()
                .requestFactory(httpClientFactory)
                .defaultHeader("User-Agent", "RepoMind-Analyzer");

        if (props.hasToken()) {
            rawBuilder.defaultHeader("Authorization", "Bearer " + props.getToken());
        }

        this.rawClient = rawBuilder.build();
    }

    // --------------------------------------------------
    // 1. Fetch repository metadata from GitHub
    // --------------------------------------------------

    public GitHubRepositoryResponse getRepository(
            String owner,
            String repository
    ) {
        return restClient.get()
                .uri("/repos/{owner}/{repository}", owner, repository)
                .retrieve()
                .onStatus(this::isRateLimit, (req, res) -> {
                    throw new GitHubRateLimitException(
                            "GitHub rate limit exceeded fetching repository metadata for "
                                    + owner + "/" + repository
                                    + " (HTTP " + res.getStatusCode().value() + ")"
                    );
                })
                .onStatus(HttpStatus.NOT_FOUND::isSameCodeAs, (req, res) -> {
                    throw new GitHubNotFoundException(
                            "Repository not found on GitHub: " + owner + "/" + repository
                    );
                })
                .body(GitHubRepositoryResponse.class);
    }

    // --------------------------------------------------
    // 2. Fetch the recursive repository file tree
    // --------------------------------------------------

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
                .onStatus(this::isRateLimit, (req, res) -> {
                    throw new GitHubRateLimitException(
                            "GitHub rate limit exceeded fetching file tree for "
                                    + owner + "/" + repository + "@" + branch
                                    + " (HTTP " + res.getStatusCode().value() + ")"
                    );
                })
                .onStatus(HttpStatus.NOT_FOUND::isSameCodeAs, (req, res) -> {
                    throw new GitHubNotFoundException(
                            "Branch or repository not found: "
                                    + owner + "/" + repository + "@" + branch
                    );
                })
                .body(GitHubTreeResponse.class);
    }

    // --------------------------------------------------
    // 3. Fetch the raw text content of a single file.
    //    Returns null if the file is not found (404),
    //    which the caller should treat as a permanent skip.
    //    Throws GitHubRateLimitException on 403/429.
    //    Throws RestClientException on network/timeout errors.
    // --------------------------------------------------

    public String getFileContent(
            String owner,
            String repository,
            String branch,
            String path
    ) {
        URI uri = UriComponentsBuilder
                .fromUriString("https://raw.githubusercontent.com")
                .pathSegment(owner, repository, branch)
                .path("/" + path)
                .build()
                .encode()
                .toUri();

        return rawClient.get()
                .uri(uri)
                .retrieve()
                .onStatus(this::isRateLimit, (req, res) -> {
                    throw new GitHubRateLimitException(
                            "GitHub rate limit exceeded fetching file content: " + path
                                    + " (HTTP " + res.getStatusCode().value() + ")"
                    );
                })
                .onStatus(HttpStatus.NOT_FOUND::isSameCodeAs, (req, res) -> {
                    // File was deleted or renamed after the tree was fetched.
                    // Return null so the caller can record a permanent skip.
                    throw new GitHubNotFoundException(
                            "File not found (may have been deleted): " + path
                    );
                })
                .body(String.class);
    }

    // --------------------------------------------------
    // Helper: matches HTTP 403 and 429, both of which
    // GitHub uses to signal rate-limit exhaustion.
    // --------------------------------------------------

    private boolean isRateLimit(HttpStatusCode status) {
        return status.value() == 403 || status.value() == 429;
    }

    // --------------------------------------------------
    // Typed exceptions — allow callers to handle
    // permanent vs transient GitHub errors differently.
    // --------------------------------------------------

    /**
     * Thrown when GitHub returns 403 or 429, indicating the API rate limit
     * has been exhausted. This is a transient condition; the caller should
     * stop processing and retry after the rate-limit window resets.
     */
    public static class GitHubRateLimitException extends RuntimeException {
        public GitHubRateLimitException(String message) {
            super(message);
        }
    }

    /**
     * Thrown when GitHub returns 404 for a specific resource.
     * This is a permanent condition; the caller should skip the resource
     * and not retry.
     */
    public static class GitHubNotFoundException extends RuntimeException {
        public GitHubNotFoundException(String message) {
            super(message);
        }
    }
}
