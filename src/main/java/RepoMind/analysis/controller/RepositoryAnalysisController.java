package RepoMind.analysis.controller;

import RepoMind.analysis.dto.RepositoryHealthAnalysisResponse;
import RepoMind.analysis.service.RepositoryHealthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/analysis")
public class RepositoryAnalysisController {

    private final RepositoryHealthService repositoryHealthService;

    public RepositoryAnalysisController(RepositoryHealthService repositoryHealthService) {
        this.repositoryHealthService = repositoryHealthService;
    }

    /**
     * Executes repository health analysis and returns the complete result.
     */
    @PostMapping
    public ResponseEntity<RepositoryHealthAnalysisResponse> analyzeRepositoryHealth(
            @PathVariable Long repositoryId
    ) {
        RepositoryHealthAnalysisResponse response = repositoryHealthService.analyzeRepositoryHealth(repositoryId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the latest health analysis for the specified repository.
     */
    @GetMapping("/latest")
    public ResponseEntity<RepositoryHealthAnalysisResponse> getLatestAnalysis(
            @PathVariable Long repositoryId
    ) {
        RepositoryHealthAnalysisResponse response = repositoryHealthService.getLatestAnalysis(repositoryId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a specific health analysis record by its primary key ID.
     */
    @GetMapping("/{analysisId}")
    public ResponseEntity<RepositoryHealthAnalysisResponse> getAnalysisById(
            @PathVariable Long repositoryId,
            @PathVariable Long analysisId
    ) {
        RepositoryHealthAnalysisResponse response = repositoryHealthService.getAnalysisById(analysisId);
        return ResponseEntity.ok(response);
    }
}
