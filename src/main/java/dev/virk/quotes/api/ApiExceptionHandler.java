package dev.virk.quotes.api;

import dev.virk.quotes.quote.QuoteExceptions.QuoteAlreadyAcceptedException;
import dev.virk.quotes.quote.QuoteExceptions.QuoteExpiredException;
import dev.virk.quotes.quote.QuoteExceptions.QuoteNotFoundException;
import dev.virk.quotes.rates.InvalidRateTableException;
import dev.virk.quotes.rates.NoRateException;
import dev.virk.quotes.rates.RateTableNotFoundException;
import java.net.URI;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Every error leaves as application/problem+json (RFC 9457), with a stable `type` URN. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setType(URI.create("urn:quote-service:problem:" + type));
        p.setTitle(title);
        return p;
    }

    @ExceptionHandler(QuoteNotFoundException.class)
    ProblemDetail quoteNotFound(QuoteNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "quote-not-found", "Quote not found", e.getMessage());
    }

    @ExceptionHandler(RateTableNotFoundException.class)
    ProblemDetail tableNotFound(RateTableNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "rate-table-not-found", "Rate table not found", e.getMessage());
    }

    @ExceptionHandler(QuoteExpiredException.class)
    ProblemDetail expired(QuoteExpiredException e) {
        return problem(HttpStatus.CONFLICT, "quote-expired", "Quote expired", e.getMessage());
    }

    @ExceptionHandler(QuoteAlreadyAcceptedException.class)
    ProblemDetail alreadyAccepted(QuoteAlreadyAcceptedException e) {
        return problem(HttpStatus.CONFLICT, "quote-already-accepted", "Quote already accepted", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail contention(OptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "concurrent-update", "Concurrent update",
                "the quote kept changing while this request ran, try again");
    }

    @ExceptionHandler(NoRateException.class)
    ProblemDetail noRate(NoRateException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "no-rate", "No rate for this parcel", e.getMessage());
    }

    @ExceptionHandler(InvalidRateTableException.class)
    ProblemDetail badTable(InvalidRateTableException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-rate-table", "Invalid rate table", e.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "validation", "Validation failed",
                "the request body has " + ex.getBindingResult().getErrorCount() + " invalid field(s)");
        List<FieldProblem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new FieldProblem(f.getField(), f.getDefaultMessage()))
                .sorted(java.util.Comparator.comparing(FieldProblem::field))
                .toList();
        p.setProperty("errors", errors);
        return handleExceptionInternal(ex, p, headers, HttpStatus.BAD_REQUEST, request);
    }

    public record FieldProblem(String field, String message) {}
}
