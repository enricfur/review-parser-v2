package ru.student.reviewparser.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedReview(
        String externalId,
        String sourceUrl,
        String author,
        String title,
        String content,
        BigDecimal rating,
        LocalDate publishedAt
) {
}
