package RepoMind.repository.controller;


import RepoMind.repository.service.RepositoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {
    private RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService){
        this.repositoryService= repositoryService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeRepository(@RequestBody String githubUrl){
        return ResponseEntity.ok(repositoryService.analyzeRepository(githubUrl));
    }
}
