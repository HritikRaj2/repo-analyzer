package RepoMind.dependency.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GradleParserTest {

    private GradleParser parser;

    @BeforeEach
    void setUp() {
        parser = new GradleParser();
    }

    @Test
    @DisplayName("Should recognize build.gradle and build.gradle.kts")
    void shouldSupportGradleManifests() {
        assertTrue(parser.supports("build.gradle"));
        assertTrue(parser.supports("build.gradle.kts"));
        assertTrue(parser.supports("app/build.gradle"));
        assertFalse(parser.supports("pom.xml"));
    }

    @Test
    @DisplayName("Should parse Groovy string and map notation dependencies")
    void shouldParseGroovyDependencies() {
        String gradleContent = """
                dependencies {
                    implementation 'org.springframework.boot:spring-boot-starter-web:3.2.0'
                    testImplementation "org.junit.jupiter:junit-jupiter:5.10.0"
                    compileOnly group: 'org.projectlombok', name: 'lombok', version: '1.18.30'
                }
                """;

        List<ParsedDependency> deps = parser.parse(gradleContent, "build.gradle");

        assertEquals(3, deps.size());

        ParsedDependency web = deps.get(0);
        assertEquals("org.springframework.boot:spring-boot-starter-web", web.name());
        assertEquals("3.2.0", web.version());
        assertEquals("compile", web.scope());
        assertFalse(web.dynamicOrUnresolved());

        ParsedDependency test = deps.get(1);
        assertEquals("org.junit.jupiter:junit-jupiter", test.name());
        assertEquals("5.10.0", test.version());
        assertEquals("test", test.scope());

        ParsedDependency lombok = deps.get(2);
        assertEquals("org.projectlombok:lombok", lombok.name());
        assertEquals("1.18.30", lombok.version());
        assertEquals("provided", lombok.scope());
    }

    @Test
    @DisplayName("Should parse Kotlin DSL dependencies and flag dynamic versions")
    void shouldParseKotlinDslDependencies() {
        String ktsContent = """
                dependencies {
                    implementation("io.ktor:ktor-server-core:2.3.5")
                    testImplementation("io.mockk:mockk:1.13.8")
                    implementation("org.example:dynamic-lib:$variableVersion")
                }
                """;

        List<ParsedDependency> deps = parser.parse(ktsContent, "build.gradle.kts");

        assertEquals(3, deps.size());

        ParsedDependency ktor = deps.get(0);
        assertEquals("io.ktor:ktor-server-core", ktor.name());
        assertEquals("2.3.5", ktor.version());
        assertEquals("compile", ktor.scope());

        ParsedDependency dynamic = deps.get(2);
        assertEquals("org.example:dynamic-lib", dynamic.name());
        assertEquals("$variableVersion", dynamic.version());
        assertTrue(dynamic.dynamicOrUnresolved());
    }
}
