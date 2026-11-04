package ru.student.reviewparser.source;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.student.reviewparser.model.ParsedReview;

import java.util.List;

@RestController
public class SourceController {

    private final SourceService sourceService;

    public SourceController(SourceService sourceService) {
        this.sourceService = sourceService;
    }

    @PostMapping("/sources")
    public ResponseEntity<RegisterSourceResponse> registerSource(
            @RequestBody RegisterSourceRequest request
    ) {
        SourceRegistry.Source source =
                sourceService.registerAndCollectAsync(request.url());

        RegisterSourceResponse response =
                new RegisterSourceResponse(
                        source.id(),
                        source.url(),
                        sourceService.findCollectionStatus(source.id())
                );

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(response);
    }

    @GetMapping("/sources")
    public List<SourceStatusResponse> getSources() {
        return sourceService.findAllSources()
                .stream()
                .map(source -> new SourceStatusResponse(
                        source.id(),
                        source.url(),
                        sourceService.findCollectionStatus(source.id())
                ))
                .toList();
    }

    @GetMapping(
            value = "/answer",
            produces = "application/json;charset=UTF-8"
    )
    public List<ParsedReview> getAnswer(
            @RequestParam(required = false) Long sourceId
    ) {
        if (sourceId != null) {
            return sourceService.findReviewsBySource(sourceId);
        }

        return sourceService.findAllReviews();
    }

    public record RegisterSourceRequest(String url) {
    }

    public record RegisterSourceResponse(
            long id,
            String url,
            String status
    ) {
    }

    public record SourceStatusResponse(
            long id,
            String url,
            String status
    ) {
    }
}
