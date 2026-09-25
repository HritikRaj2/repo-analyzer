package RepoMind.repository.model;


import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "repository_files")
public class RepositoryFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repository repository;

    @Column(nullable = false)
    private String path;

    private String fileName;
    private  String extension;
    private String language;

    private Long size;

    private Integer linesOfCode;

    private Boolean testFile;

    private Boolean sourceFile;

}
