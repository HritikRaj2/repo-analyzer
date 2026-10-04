package RepoMind.dependency.model;


import RepoMind.repository.model.Repository;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "dependencies")
public class Dependency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id" , nullable = false)
    private Repository repository;

    @Column(nullable = false)
    private String name;

    private String version;
    private String latestVersion;
    private String packageManager;

    private Boolean directDependency;
    private Boolean outdated;
    private Boolean vulnerable;

    private String scope;

}
