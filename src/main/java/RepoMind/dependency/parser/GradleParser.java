package RepoMind.dependency.parser;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for Gradle {@code build.gradle} (Groovy DSL) and {@code build.gradle.kts} (Kotlin DSL) files.
 *
 * <p>Identifies standard dependency declarations across configurations:
 * implementation, api, compileOnly, runtimeOnly, testImplementation, testCompileOnly,
 * testRuntimeOnly, and annotationProcessor.
 *
 * <p>Preserves coordinate strings and maps configuration names to standard scopes.
 * Flags variable interpolation, version catalogs, and project references as dynamic or unresolved.
 */
@Component
public class GradleParser implements DependencyParser {

    private static final String PACKAGE_MANAGER = "gradle";

    private static final Set<String> SUPPORTED_CONFIGS = Set.of(
            "implementation", "api", "compileonly", "runtimeonly",
            "testimplementation", "testcompileonly", "testruntimeonly",
            "annotationprocessor", "kapt"
    );

    // Matches: configuration('group:artifact:version') or configuration "group:artifact:version"
    // Also matches Kotlin DSL: configuration("group:artifact:version")
    private static final Pattern STRING_NOTATION_PATTERN = Pattern.compile(
            "^\\s*([a-zA-Z0-9_]+)\\s*(?:\\(|\\s)\\s*[\"']([^\"']+)[\"']\\s*\\)?.*$"
    );

    // Matches: configuration group: '...', name: '...', version: '...'
    private static final Pattern MAP_NOTATION_PATTERN = Pattern.compile(
            "^\\s*([a-zA-Z0-9_]+)\\s*(?:\\(|\\s)\\s*group:\\s*[\"']([^\"']+)[\"']\\s*,\\s*name:\\s*[\"']([^\"']+)[\"'](?:\\s*,\\s*version:\\s*[\"']([^\"']+)[\"'])?.*$"
    );

    // Matches version catalogs / variables: configuration(libs.something)
    private static final Pattern CATALOG_NOTATION_PATTERN = Pattern.compile(
            "^\\s*([a-zA-Z0-9_]+)\\s*(?:\\(|\\s)\\s*(libs\\.[a-zA-Z0-9_.]+)\\s*\\)?.*$"
    );

    // Matches project dependencies: configuration(project(':...'))
    private static final Pattern PROJECT_NOTATION_PATTERN = Pattern.compile(
            "^\\s*([a-zA-Z0-9_]+)\\s*(?:\\(|\\s)\\s*project\\([\"']([^\"']+)[\"']\\)\\s*\\)?.*$"
    );

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) {
            return false;
        }
        String normalized = fileName.toLowerCase(Locale.ROOT);
        return normalized.equals("build.gradle")
                || normalized.equals("build.gradle.kts")
                || normalized.endsWith("/build.gradle")
                || normalized.endsWith("/build.gradle.kts")
                || normalized.endsWith("\\build.gradle")
                || normalized.endsWith("\\build.gradle.kts");
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
        String[] lines = content.split("\\r?\\n");

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                continue;
            }

            // 1. Try string coordinate notation: config('group:artifact:version') or config 'group:artifact:version'
            Matcher stringMatcher = STRING_NOTATION_PATTERN.matcher(trimmed);
            if (stringMatcher.matches()) {
                String config = stringMatcher.group(1);
                if (isSupportedConfig(config)) {
                    String coord = stringMatcher.group(2).trim();
                    ParsedDependency dep = parseCoordinateString(config, coord, manifestPath);
                    if (dep != null) {
                        dependencies.add(dep);
                        continue;
                    }
                }
            }

            // 2. Try map notation: config group: '...', name: '...', version: '...'
            Matcher mapMatcher = MAP_NOTATION_PATTERN.matcher(trimmed);
            if (mapMatcher.matches()) {
                String config = mapMatcher.group(1);
                if (isSupportedConfig(config)) {
                    String group = mapMatcher.group(2);
                    String name = mapMatcher.group(3);
                    String version = mapMatcher.group(4);
                    boolean dynamic = version == null || version.contains("$") || version.contains("+");
                    dependencies.add(new ParsedDependency(
                            group + ":" + name,
                            version,
                            PACKAGE_MANAGER,
                            normalizeScope(config),
                            true,
                            manifestPath,
                            dynamic
                    ));
                    continue;
                }
            }

            // 3. Try catalog / version catalog reference: config(libs.xyz)
            Matcher catalogMatcher = CATALOG_NOTATION_PATTERN.matcher(trimmed);
            if (catalogMatcher.matches()) {
                String config = catalogMatcher.group(1);
                if (isSupportedConfig(config)) {
                    String catalogRef = catalogMatcher.group(2);
                    dependencies.add(new ParsedDependency(
                            catalogRef,
                            null,
                            PACKAGE_MANAGER,
                            normalizeScope(config),
                            true,
                            manifestPath,
                            true // catalog reference cannot be fully resolved statically
                    ));
                    continue;
                }
            }

            // 4. Try project dependency: config(project(':subproject'))
            Matcher projectMatcher = PROJECT_NOTATION_PATTERN.matcher(trimmed);
            if (projectMatcher.matches()) {
                String config = projectMatcher.group(1);
                if (isSupportedConfig(config)) {
                    String projectPath = projectMatcher.group(2);
                    dependencies.add(new ParsedDependency(
                            "project" + projectPath,
                            null,
                            PACKAGE_MANAGER,
                            normalizeScope(config),
                            true,
                            manifestPath,
                            true // internal project dependency
                    ));
                }
            }
        }

        return dependencies;
    }

    private boolean isSupportedConfig(String config) {
        return SUPPORTED_CONFIGS.contains(config.toLowerCase(Locale.ROOT));
    }

    private ParsedDependency parseCoordinateString(String config, String coord, String manifestPath) {
        // e.g. "org.springframework.boot:spring-boot-starter-web:3.2.0"
        // or "org.springframework.boot:spring-boot-starter-web"
        if (coord.contains(":") && !coord.startsWith("project:") && !coord.startsWith("files(")) {
            String[] parts = coord.split(":");
            String name;
            String version = null;
            boolean dynamic = false;

            if (parts.length >= 3) {
                name = parts[0] + ":" + parts[1];
                version = parts[2];
                if (version.contains("$") || version.contains("+") || version.equalsIgnoreCase("latest.release")) {
                    dynamic = true;
                }
            } else if (parts.length == 2) {
                name = parts[0] + ":" + parts[1];
                dynamic = true; // missing version, inherited or platform-managed
            } else {
                name = coord;
                dynamic = true;
            }

            return new ParsedDependency(
                    name,
                    version,
                    PACKAGE_MANAGER,
                    normalizeScope(config),
                    true,
                    manifestPath,
                    dynamic
            );
        }
        return null;
    }

    private String normalizeScope(String config) {
        String lower = config.toLowerCase(Locale.ROOT);
        if (lower.contains("test")) {
            return "test";
        }
        if (lower.contains("compileonly")) {
            return "provided";
        }
        if (lower.contains("runtimeonly")) {
            return "runtime";
        }
        if (lower.contains("annotationprocessor") || lower.contains("kapt")) {
            return "annotationProcessor";
        }
        return "compile";
    }
}
