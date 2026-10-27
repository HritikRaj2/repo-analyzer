package RepoMind.dependency.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NpmPackageJsonParserTest {

    private NpmPackageJsonParser parser;

    @BeforeEach
    void setUp() {
        parser = new NpmPackageJsonParser();
    }

    @Test
    @DisplayName("Should recognize package.json filenames")
    void shouldSupportPackageJson() {
        assertTrue(parser.supports("package.json"));
        assertTrue(parser.supports("frontend/package.json"));
        assertFalse(parser.supports("pom.xml"));
    }

    @Test
    @DisplayName("Should parse dependencies, devDependencies, and map scopes")
    void shouldParseNpmDependencies() throws Exception {
        String packageJson = """
                {
                  "name": "sample-ui",
                  "version": "1.0.0",
                  "dependencies": {
                    "react": "^18.2.0",
                    "axios": "~1.6.0"
                  },
                  "devDependencies": {
                    "typescript": "^5.2.0",
                    "jest": "29.7.0"
                  },
                  "peerDependencies": {
                    "react-dom": ">=18.0.0"
                  }
                }
                """;

        List<ParsedDependency> deps = parser.parse(packageJson, "package.json");

        assertEquals(5, deps.size());

        ParsedDependency react = deps.stream().filter(d -> d.name().equals("react")).findFirst().orElseThrow();
        assertEquals("^18.2.0", react.version());
        assertEquals("runtime", react.scope());
        assertEquals("npm", react.packageManager());
        assertTrue(react.directDependency());
        assertTrue(react.dynamicOrUnresolved()); // has ^

        ParsedDependency jest = deps.stream().filter(d -> d.name().equals("jest")).findFirst().orElseThrow();
        assertEquals("29.7.0", jest.version());
        assertEquals("development", jest.scope());
        assertFalse(jest.dynamicOrUnresolved()); // fixed version

        ParsedDependency reactDom = deps.stream().filter(d -> d.name().equals("react-dom")).findFirst().orElseThrow();
        assertEquals(">=18.0.0", reactDom.version());
        assertEquals("peer", reactDom.scope());
    }
}
