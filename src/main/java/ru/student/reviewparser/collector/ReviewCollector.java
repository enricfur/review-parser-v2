package ru.student.reviewparser.collector;

import ru.student.reviewparser.model.ParsedReview;

import java.io.IOException;
import java.util.List;

public interface ReviewCollector {

    List<ParsedReview> collectFromObject(
            String sourceKey,
            String objectUrl
    ) throws IOException, InterruptedException;
}
