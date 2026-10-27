package RepoMind.dependency.parser;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java parser for Node.js {@code package.json} manifests.
 *
 * <p>Parses {@code dependencies}, {@code devDependencies}, {@code peerDependencies},
 * and {@code optionalDependencies} using standard JDK regex without requiring
 * external JSON parsing libraries. Preserves declared version ranges and records
 * corresponding npm scopes.
 */
@Component
public class NpmPackageJsonParser implements DependencyParser {

    private static final String PACKAGE_MANAGER = "npm";

    // Matches individual "packageName": "versionRange" declarations
    private static final Pattern ENTRY_PATTERN = Pattern.compile(
            "\"([^\"]+)\"\\s*:\\s*\"([^\"]+)\""
    );

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) {
            return false;
        }
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.equals("package.json")
                || normalized.endsWith("/package.json")
                || normalized.endsWith("\\package.json");
    }

    @Override
    public String getPackageManager() {
        return PACKAGE_MANAGER;
    }

    @Override
    public List<ParsedDependency> parse(String content, String manifestPath) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }

        List<ParsedDependency> dependencies = new ArrayList<>();

        // 1. Production dependencies
        parseSection(content, "dependencies", "runtime", manifestPath, dependencies);

        // 2. Development dependencies
        parseSection(content, "devDependencies", "development", manifestPath, dependencies);

        // 3. Peer dependencies
        parseSection(content, "peerDependencies", "peer", manifestPath, dependencies);

        // 4. Optional dependencies
        parseSection(content, "optionalDependencies", "optional", manifestPath, dependencies);

        return dependencies;
    }

    private void parseSection(
            String jsonContent,
            String sectionName,
            String scope,
            String manifestPath,
            List<ParsedDependency> dependencies
    ) {
        // Find "sectionName"\s*:\s*\{ ... \}
        Pattern sectionPattern = Pattern.compile(
                "\"" + Pattern.quote(sectionName) + "\"\\s*:\\s*\\{([^}]*)\\}",
                Pattern.DOTALL
        );

        Matcher sectionMatcher = sectionPattern.matcher(jsonContent);
        if (sectionMatcher.find()) {
            String sectionBlock = sectionMatcher.group(1);
            Matcher entryMatcher = ENTRY_PATTERN.matcher(sectionBlock);

            while (entryMatcher.find()) {
                String packageName = entryMatcher.group(1).trim();
                String versionRange = entryMatcher.group(2).trim();

                boolean dynamicRange = versionRange.startsWith("^")
                        || versionRange.startsWith("~")
                        || versionRange.contains(">")
                        || versionRange.contains("<")
                        || versionRange.contains("*")
                        || versionRange.equalsIgnoreCase("latest");

                dependencies.add(new ParsedDependency(
                        packageName,
                        versionRange,
                        PACKAGE_MANAGER,
                        scope,
                        true,
                        manifestPath,
                        dynamicRange
                ));
            }
        }
    }
}
