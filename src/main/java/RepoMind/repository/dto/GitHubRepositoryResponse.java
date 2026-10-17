package RepoMind.repository.dto;


import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubRepositoryResponse(
        Long id,
        String name,

        @JsonProperty("full_name")
        String fullName,

        Owner owner,
        String description,

        @JsonProperty("html_url")
        String htmlUrl,

        @JsonProperty("default_branch")
        String defaultBranch,

        @JsonProperty("stargazers_count")
        Integer stars,

        @JsonProperty("forks_count")
        Integer forks,

        @JsonProperty("open_issues_count")
        Integer openIssues,

        License license,
        String language,

        @JsonProperty("created_at")
        String createdAt,

        @JsonProperty("updated_at")
        String updatedAt,

        @JsonProperty("pushed_at")
        String pushedAt
) {
    public record Owner(String login) {}
    public record License(
            String key,
            String name,
            @JsonProperty("spdx_id") String spdxId
    ) {}
}

