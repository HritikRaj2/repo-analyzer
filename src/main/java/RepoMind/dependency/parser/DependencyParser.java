package RepoMind.dependency.parser;

import java.util.List;

/**
 * Strategy interface for parsing dependency manifests into normalized dependency declarations.
 */
public interface DependencyParser {

    /**
     * Checks if this parser can handle the specified file name or path.
     *
     * @param fileName file name or relative path
     * @return true if supported
     */
    boolean supports(String fileName);

    /**
     * Parses the manifest file content into a list of normalized dependencies.
     *
     * @param content      raw text content of the manifest
     * @param manifestPath path of the manifest file in the repository
     * @return list of parsed dependencies
     * @throws Exception if parsing fails due to malformed content
     */
    List<ParsedDependency> parse(String content, String manifestPath) throws Exception;

    /**
     * Returns the package manager name ("maven", "gradle", "npm").
     */
    String getPackageManager();
}
