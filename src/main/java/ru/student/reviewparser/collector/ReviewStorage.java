package ru.student.reviewparser.collector;

import ru.student.reviewparser.model.ParsedReview;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ReviewStorage {

    Map<Long, List<ParsedReview>> load() throws IOException;

    void save(Map<Long, List<ParsedReview>> snapshots)
            throws IOException;
}
