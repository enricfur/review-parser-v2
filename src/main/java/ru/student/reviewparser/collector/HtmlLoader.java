package ru.student.reviewparser.collector;

import java.io.IOException;

@FunctionalInterface
public interface HtmlLoader {

    String load(String sourceUrl) throws IOException, InterruptedException;
}
