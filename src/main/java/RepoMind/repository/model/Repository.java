package RepoMind.repository.model;


import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "repositories")
@Data
@NoArgsConstructor
public class Repository {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id" , nullable = false , unique = true)
    private String githubId;

    @Column(nullable = false)
    private String name;

    @Column(name = "full_name" , nullable = false)
    private String fullName;

    private String owner;
    private String description;

    @Column(name = "html_url")
    private String htmlUrl;

    @Column(name="default_branch")
    private String defaultBranch;

    private Integer stars;
    private Integer forks;

    @Column(name = "open_issues")
    private Integer openIssues;

    private String license;

    @Column(name = "primary_language")
    private String primaryLanguage;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime pushedAt;
    private LocalDateTime analyzedAt;
}
