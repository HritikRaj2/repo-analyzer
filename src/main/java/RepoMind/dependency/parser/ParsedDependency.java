package RepoMind.dependency.parser;

/**
 * Normalized representation of a dependency extracted from any manifest format.
 *
 * @param name                coordinate or package name (e.g. "org.springframework.boot:spring-boot-starter-web" or "react")
 * @param version             declared version or range (e.g. "3.2.0", "^18.2.0", "${spring.version}")
 * @param packageManager      manifest package manager identifier ("maven", "gradle", "npm")
 * @param scope               dependency scope (e.g. "compile", "test", "runtime", "provided", "development", "peer")
 * @param directDependency    whether declared directly in the manifest
 * @param manifestPath        relative file path of the manifest (e.g. "pom.xml", "client/package.json")
 * @param dynamicOrUnresolved true if the version is variable, dynamic range, or unresolved property
 */
public record ParsedDependency(
        String name,
        String version,
        String packageManager,
        String scope,
        Boolean directDependency,
        String manifestPath,
        Boolean dynamicOrUnresolved
) {
}
