package ru.student.reviewparser.collector;

import java.io.IOException;

@FunctionalInterface
public interface JsonLoader {

    String load(String sourceUrl)
            throws IOException, InterruptedException;
}
