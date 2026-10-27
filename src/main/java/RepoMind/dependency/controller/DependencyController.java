package RepoMind.dependency.controller;

import RepoMind.dependency.dto.DependencyAnalysisResponse;
import RepoMind.dependency.dto.DependencyListResponse;
import RepoMind.dependency.service.DependencyService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repositories/{repositoryId}/dependencies")
public class DependencyController {

    private final DependencyService dependencyService;

    public DependencyController(DependencyService dependencyService) {
        this.dependencyService = dependencyService;
    }

    /**
     * Discovers manifests, fetches contents, parses dependencies, and persists them.
     */
    @PostMapping("/analyze")
    public ResponseEntity<DependencyAnalysisResponse> analyzeDependencies(
            @PathVariable Long repositoryId
    ) {
        DependencyAnalysisResponse response = dependencyService.analyzeDependencies(repositoryId);
        return ResponseEntity.ok(response);
    }

    /**
     * Lists persisted dependencies for a repository with optional pagination.
     */
    @GetMapping
    public ResponseEntity<DependencyListResponse> getDependencies(
            @PathVariable Long repositoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        Sort sort = "desc".equalsIgnoreCase(direction) ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, Math.min(size, 200), sort);
        DependencyListResponse response = dependencyService.getDependencies(repositoryId, pageable);
        return ResponseEntity.ok(response);
    }
}
