package ru.student.reviewparser.source;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

@RestControllerAdvice
public class SourceExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleInvalidInput(
            IllegalArgumentException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error", "INVALID_INPUT",
                        "message", safeMessage(exception)
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleInvalidJson(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity
                .badRequest()
                .body(Map.of(
                        "error", "INVALID_JSON",
                        "message", "Request body must contain valid JSON"
                ));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> handleLoadingError(
            IOException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(Map.of(
                        "error", "SOURCE_LOADING_FAILED",
                        "message", "Could not load reviews from source"
                ));
    }

    @ExceptionHandler(InterruptedException.class)
    public ResponseEntity<Map<String, String>> handleInterrupted(
            InterruptedException exception
    ) {
        Thread.currentThread().interrupt();

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "COLLECTION_INTERRUPTED",
                        "message", "Source collection was interrupted"
                ));
    }

    @ExceptionHandler(RejectedExecutionException.class)
    public ResponseEntity<Map<String, String>> handleCollectionRejected(
            RejectedExecutionException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "error", "COLLECTION_UNAVAILABLE",
                        "message", "Collection service is busy or unavailable"
                ));
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();

        return message == null || message.isBlank()
                ? "Request could not be processed"
                : message;
    }
}
