package RepoMind.analysis.model;

import RepoMind.repository.model.Repository;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Entity
@NoArgsConstructor
@Table(name = "repository_analyses")
public class RepositoryAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    private Integer totalFiles;
    private Integer totalDirectories;

    private Long totalLinesOfCode;

    private Integer sourceFiles;

    private Integer testFiles;

    private Integer contributors;
    private Integer commits;

    @Column(name = "health_score")
    private Double healthScore;

    @Column(name = "health_confidence")
    private String healthConfidence;

    @Column(name = "health_grade")
    private String healthGrade;

    @Column(name = "dependency_count")
    private Integer dependencyCount;

    @Column(name = "metrics_json", columnDefinition = "TEXT")
    private String metricsJson;

    @Column(name = "findings_json", columnDefinition = "TEXT")
    private String findingsJson;

    private String errorMessage;
}
