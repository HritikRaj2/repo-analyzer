package RepoMind.analysis.analyzer;

import RepoMind.analysis.analyzer.HealthScoreCalculator.CalculationResult;
import RepoMind.dependency.model.Dependency;
import RepoMind.repository.model.Repository;
import RepoMind.repository.model.RepositoryFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HealthScoreCalculatorTest {

    private HealthScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new HealthScoreCalculator();
    }

    @Test
    @DisplayName("Should award high score and high confidence to well-documented repository with tests and completed content analysis")
    void shouldScoreHealthyRepository() {
        Repository repo = new Repository();
        repo.setId(1L);
        repo.setName("repo-mind");
        repo.setLicense("Apache 2.0");
        repo.setPushedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(10));

        List<RepositoryFile> files = new ArrayList<>();

        // Doc files
        RepositoryFile readme = new RepositoryFile();
        readme.setPath("README.md");
        readme.setFileName("README.md");
        files.add(readme);

        RepositoryFile gitignore = new RepositoryFile();
        gitignore.setPath(".gitignore");
        gitignore.setFileName(".gitignore");
        files.add(gitignore);

        // Source files (100% have measured lines)
        for (int i = 0; i < 10; i++) {
            RepositoryFile src = new RepositoryFile();
            src.setPath("src/App" + i + ".java");
            src.setFileName("App" + i + ".java");
            src.setSourceFile(true);
            src.setTestFile(false);
            src.setLinesOfCode(100);
            src.setSize(2000L);
            src.setLanguage("Java");
            src.setExtension("java");
            files.add(src);
        }

        // Test files (3 tests for 10 source files -> 0.3 ratio)
        for (int i = 0; i < 3; i++) {
            RepositoryFile test = new RepositoryFile();
            test.setPath("src/test/App" + i + "Test.java");
            test.setFileName("App" + i + "Test.java");
            test.setSourceFile(true);
            test.setTestFile(true);
            test.setLinesOfCode(50);
            test.setSize(1000L);
            test.setLanguage("Java");
            test.setExtension("java");
            files.add(test);
        }

        List<Dependency> dependencies = List.of(
                createDependency("org.springframework.boot:spring-boot-starter-web", "maven")
        );

        CalculationResult result = calculator.calculate(repo, files, dependencies, true);

        assertNotNull(result);
        assertTrue(result.score() >= 90.0, "Expected score >= 90 but got: " + result.score());
        assertEquals("A", result.grade());
        assertEquals("HIGH", result.confidence());
        assertFalse(result.partialAssessment());
        assertEquals("FULL_ASSESSMENT", result.assessmentScope());
        assertEquals(13, result.metrics().sourceFiles());
        assertEquals(3, result.metrics().testFiles());
        assertTrue(result.metrics().contentAnalysisComplete());
    }

    @Test
    @DisplayName("Should detect README.adoc without producing false missing documentation warning")
    void shouldRecognizeReadmeAdoc() {
        Repository repo = new Repository();
        repo.setId(10L);
        repo.setName("spring-boot");
        repo.setLicense("Apache-2.0");
        repo.setPushedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(2));

        List<RepositoryFile> files = new ArrayList<>();
        RepositoryFile adocReadme = new RepositoryFile();
        adocReadme.setPath("README.adoc");
        adocReadme.setFileName("README.adoc");
        files.add(adocReadme);

        RepositoryFile gitignore = new RepositoryFile();
        gitignore.setPath(".gitignore");
        gitignore.setFileName(".gitignore");
        files.add(gitignore);

        CalculationResult result = calculator.calculate(repo, files, List.of(), true);

        // DOC-001 (missing readme) must NOT be generated
        boolean hasMissingReadmeWarning = result.findings().stream()
                .anyMatch(f -> "DOC-001".equals(f.id()));
        assertFalse(hasMissingReadmeWarning, "README.adoc should be recognized as valid documentation");
    }

    @Test
    @DisplayName("Should mark confidence LOW and grade as Preliminary when source files are mostly unanalyzed")
    void shouldMarkPreliminaryWhenContentAnalysisIncomplete() {
        Repository repo = new Repository();
        repo.setId(20L);
        repo.setName("large-spring-repo");
        repo.setLicense("Apache-2.0");
        repo.setPushedAt(LocalDateTime.now(ZoneOffset.UTC).minusDays(5));

        List<RepositoryFile> files = new ArrayList<>();
        RepositoryFile readme = new RepositoryFile();
        readme.setPath("README.md");
        readme.setFileName("README.md");
        files.add(readme);

        // 100 source files, but only 2 have lines counted (98% unanalyzed)
        for (int i = 0; i < 100; i++) {
            RepositoryFile src = new RepositoryFile();
            src.setPath("src/File" + i + ".java");
            src.setFileName("File" + i + ".java");
            src.setSourceFile(true);
            src.setTestFile(i >= 80);
            src.setSize(2500L);
            src.setLinesOfCode(i < 2 ? 150 : null); // only first 2 files have linesOfCode
            files.add(src);
        }

        CalculationResult result = calculator.calculate(repo, files, List.of(), true);

        assertEquals("LOW", result.confidence(), "Confidence should be LOW when 98% of source files are unanalyzed");
        assertTrue(result.partialAssessment(), "Must be flagged as a partial assessment");
        assertEquals("PRELIMINARY_STRUCTURAL", result.assessmentScope());
        assertTrue(result.grade().contains("Preliminary"), "Grade should be flagged as Preliminary: " + result.grade());

        // Incomplete content analysis finding must be present
        assertTrue(result.findings().stream().anyMatch(f -> "METRIC-001".equals(f.id())),
                "Expected METRIC-001 warning finding regarding incomplete content analysis");
    }

    @Test
    @DisplayName("Should clearly clarify that test-to-source ratio is not test coverage")
    void shouldClarifyTestRatioIsNotCoverage() {
        Repository repo = new Repository();
        repo.setId(30L);
        repo.setName("test-ratio-repo");

        List<RepositoryFile> files = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            RepositoryFile f = new RepositoryFile();
            f.setPath("File" + i + ".java");
            f.setFileName("File" + i + ".java");
            f.setSourceFile(true);
            f.setTestFile(i < 4); // 4 test files
            f.setLinesOfCode(50);
            files.add(f);
        }

        CalculationResult result = calculator.calculate(repo, files, List.of(), false);

        String explanation = result.metrics().ratioExplanation();
        assertNotNull(explanation);
        assertTrue(explanation.contains("NOT represent test coverage"), "Explanation must state it is not test coverage");
        assertTrue(explanation.contains("Structural ratio"), "Must explicitly identify as structural ratio");
    }

    @Test
    @DisplayName("Should properly account for oversized and permanently skipped files")
    void shouldPartitionSkippedAndOversizedFiles() {
        Repository repo = new Repository();
        repo.setId(40L);
        repo.setName("skip-repo");

        List<RepositoryFile> files = new ArrayList<>();

        // Normal source file
        RepositoryFile normal = new RepositoryFile();
        normal.setPath("Normal.java");
        normal.setFileName("Normal.java");
        normal.setSourceFile(true);
        normal.setSize(1000L);
        normal.setLinesOfCode(100);
        files.add(normal);

        // Oversized file (> 1 MB)
        RepositoryFile oversized = new RepositoryFile();
        oversized.setPath("BigData.java");
        oversized.setFileName("BigData.java");
        oversized.setSourceFile(true);
        oversized.setSize(5_000_000L); // 5 MB
        oversized.setLinesOfCode(null);
        files.add(oversized);

        // Permanently skipped file (404 sentinel -1)
        RepositoryFile missing = new RepositoryFile();
        missing.setPath("Deleted.java");
        missing.setFileName("Deleted.java");
        missing.setSourceFile(true);
        missing.setSize(500L);
        missing.setLinesOfCode(-1);
        files.add(missing);

        CalculationResult result = calculator.calculate(repo, files, List.of(), true, 1_000_000L);

        assertEquals(3, result.metrics().sourceFiles());
        assertEquals(1, result.metrics().filesWithMeasuredLines());
        assertEquals(1, result.metrics().filesSkippedTooLarge());
        assertEquals(1, result.metrics().filesPermanentlySkipped());
        assertEquals(0, result.metrics().filesMissingLineCounts());
        assertTrue(result.metrics().contentAnalysisComplete(), "Eligible files are 100% processed");
    }

    private Dependency createDependency(String name, String pm) {
        Dependency d = new Dependency();
        d.setName(name);
        d.setPackageManager(pm);
        d.setScope("compile");
        d.setDirectDependency(true);
        return d;
    }
}
