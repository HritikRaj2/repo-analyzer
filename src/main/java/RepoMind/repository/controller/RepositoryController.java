package RepoMind.repository.controller;


import RepoMind.repository.dto.RepositoryFileAnalysisResponse;
import RepoMind.repository.model.Repository;
import RepoMind.repository.service.RepositoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import RepoMind.repository.dto.RepositoryContentAnalysisResponse;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {
    private RepositoryService repositoryService;

    public RepositoryController(RepositoryService repositoryService){
        this.repositoryService= repositoryService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<Repository> analyzeRepository(
            @RequestBody String githubUrl) {

        return ResponseEntity.ok(
                repositoryService.analyzeRepository(githubUrl)
        );
    }

    @PostMapping("/{repositoryId}/files")
    public ResponseEntity<RepositoryFileAnalysisResponse> analyzeRepositoryFiles(
            @PathVariable Long repositoryId
    ) {
        return ResponseEntity.ok(
                repositoryService.analyzeRepositoryFiles(repositoryId)
        );
    }


    @PostMapping("/{repositoryId}/content-analysis")
    public ResponseEntity<RepositoryContentAnalysisResponse> analyzeRepositoryContent(
            @PathVariable Long repositoryId,
            @RequestParam(required = false) Integer batchSize,
            @RequestParam(required = false) Integer maxBatches
    ) {
        return ResponseEntity.ok(
                repositoryService.analyzeRepositoryContent(repositoryId, batchSize, maxBatches)
        );
    }

    @GetMapping("/{repositoryId}/content-analysis/status")
    public ResponseEntity<RepositoryContentAnalysisResponse> getContentAnalysisStatus(
            @PathVariable Long repositoryId
    ) {
        return ResponseEntity.ok(
                repositoryService.getContentAnalysisStatus(repositoryId)
        );
    }

}
