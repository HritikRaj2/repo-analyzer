package RepoMind.repository.dto;


import java.util.List;

public record GitHubTreeResponse(
        String sha,
        String url,
        List<TreeItem> tree,
        boolean truncated
) {
    public record TreeItem(
            String path,
            String mode,
            String type,
            String sha,
            Long size,
            String url
    ) {
    }
}
