package RepoMind.dependency.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MavenPomParserTest {

    private MavenPomParser parser;

    @BeforeEach
    void setUp() {
        parser = new MavenPomParser();
    }

    @Test
    @DisplayName("Should recognize pom.xml filenames")
    void shouldSupportPomXml() {
        assertTrue(parser.supports("pom.xml"));
        assertTrue(parser.supports("backend/pom.xml"));
        assertFalse(parser.supports("build.gradle"));
        assertFalse(parser.supports("package.json"));
    }

    @Test
    @DisplayName("Should parse standard Maven dependencies and scopes")
    void shouldParseStandardDependencies() throws Exception {
        String pomXml = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-app</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                            <version>3.2.0</version>
                        </dependency>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """;

        List<ParsedDependency> deps = parser.parse(pomXml, "pom.xml");

        assertEquals(2, deps.size());

        ParsedDependency web = deps.get(0);
        assertEquals("org.springframework.boot:spring-boot-starter-web", web.name());
        assertEquals("3.2.0", web.version());
        assertEquals("compile", web.scope());
        assertEquals("maven", web.packageManager());
        assertTrue(web.directDependency());
        assertFalse(web.dynamicOrUnresolved());

        ParsedDependency test = deps.get(1);
        assertEquals("org.junit.jupiter:junit-jupiter", test.name());
        assertEquals("5.10.0", test.version());
        assertEquals("test", test.scope());
    }

    @Test
    @DisplayName("Should ignore dependencies declared only in dependencyManagement")
    void shouldIgnoreDependencyManagement() throws Exception {
        String pomXml = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-app</artifactId>
                    <version>1.0.0</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>com.google.guava</groupId>
                                <artifactId>guava</artifactId>
                                <version>32.1.2-jre</version>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.slf4j</groupId>
                            <artifactId>slf4j-api</artifactId>
                            <version>2.0.9</version>
                        </dependency>
                    </dependencies>
                </project>
                """;

        List<ParsedDependency> deps = parser.parse(pomXml, "pom.xml");

        assertEquals(1, deps.size());
        assertEquals("org.slf4j:slf4j-api", deps.get(0).name());
    }

    @Test
    @DisplayName("Should mark property-based and omitted versions as unresolved")
    void shouldMarkPropertyVersionsAsUnresolved() throws Exception {
        String pomXml = """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>test-app</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>${lombok.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>org.postgresql</groupId>
                            <artifactId>postgresql</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;

        List<ParsedDependency> deps = parser.parse(pomXml, "pom.xml");

        assertEquals(2, deps.size());

        ParsedDependency lombok = deps.get(0);
        assertEquals("${lombok.version}", lombok.version());
        assertTrue(lombok.dynamicOrUnresolved());

        ParsedDependency pg = deps.get(1);
        assertNull(pg.version());
        assertTrue(pg.dynamicOrUnresolved());
    }
}
