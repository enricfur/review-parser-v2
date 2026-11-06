package ru.student.reviewparser.source;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.student.reviewparser.model.ParsedReview;

import java.math.BigDecimal;
import java.util.Comparator;
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
            @RequestParam(required = false) Long sourceId,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        List<ParsedReview> reviews;

        if (sourceId != null) {
            reviews = sourceService.findReviewsBySource(sourceId);
        } else {
            reviews = sourceService.findAllReviews();
        }

        if (minRating != null) {
            reviews = reviews.parallelStream()
                    .filter(review ->
                            review.rating().compareTo(minRating) >= 0
                    )
                    .toList();
        }

        if (sortBy == null) {
            return reviews;
        }

        Comparator<ParsedReview> comparator = switch (sortBy) {
            case "publishedAt" ->
                    Comparator.comparing(ParsedReview::publishedAt);
            case "rating" ->
                    Comparator.comparing(ParsedReview::rating);
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported sortBy: " + sortBy
                    );
        };

        if ("desc".equalsIgnoreCase(direction)) {
            comparator = comparator.reversed();
        } else if (!"asc".equalsIgnoreCase(direction)) {
            throw new IllegalArgumentException(
                    "Unsupported direction: " + direction
            );
        }

        return reviews.parallelStream()
                .sorted(comparator)
                .toList();
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
