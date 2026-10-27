package RepoMind.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed binding for the {@code github.*} configuration block in application.yml.
 *
 * <p>Set {@code GITHUB_TOKEN} as an environment variable to authenticate with the
 * GitHub API. An authenticated client is allowed 5000 requests per hour instead of
 * the unauthenticated limit of 60 requests per hour. Never hardcode a real token
 * in source code or application.yml.
 *
 * <p>All fields have defaults that match the yml placeholders, so the application
 * starts correctly with no environment variables set.
 */
@Component
@ConfigurationProperties(prefix = "github")
public class GitHubProperties {

    /** Optional Personal Access Token. Empty string means unauthenticated. */
    private String token = "";

    /** HTTP connection timeout in milliseconds. */
    private int connectTimeoutMs = 5000;

    /** HTTP read/response timeout in milliseconds. */
    private int readTimeoutMs = 15000;

    /** Maximum source-file size in bytes eligible for content analysis. */
    private long maxFileSizeBytes = 1_000_000L;

    /** Number of files to process per content-analysis batch call. */
    private int contentAnalysisBatchSize = 50;

    // --------------------------------------------------
    // Getters and setters — required by @ConfigurationProperties
    // --------------------------------------------------

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public int getContentAnalysisBatchSize() {
        return contentAnalysisBatchSize;
    }

    public void setContentAnalysisBatchSize(int contentAnalysisBatchSize) {
        this.contentAnalysisBatchSize = contentAnalysisBatchSize;
    }

    /**
     * Returns {@code true} if a non-blank GitHub token has been configured.
     */
    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
