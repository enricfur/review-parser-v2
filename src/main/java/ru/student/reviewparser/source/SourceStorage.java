package ru.student.reviewparser.source;

import java.io.IOException;
import java.util.List;

public interface SourceStorage {

    List<SourceRegistry.Source> load() throws IOException;

    void save(List<SourceRegistry.Source> sources)
            throws IOException;
}
