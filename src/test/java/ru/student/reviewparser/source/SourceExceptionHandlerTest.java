package ru.student.reviewparser.source;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceExceptionHandlerTest {

    @Test
    void returnsServiceUnavailableWhenExecutorRejectsTask() {
        SourceExceptionHandler handler =
                new SourceExceptionHandler();

        var response = handler.handleCollectionRejected(
                new RejectedExecutionException(
                        "Executor queue is full"
                )
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                response.getStatusCode()
        );

        assertEquals(
                Map.of(
                        "error", "COLLECTION_UNAVAILABLE",
                        "message",
                        "Collection service is busy or unavailable"
                ),
                response.getBody()
        );
    }
}
