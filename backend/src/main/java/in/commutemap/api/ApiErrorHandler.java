package in.commutemap.api;

import in.commutemap.places.GooglePlacesClient;
import in.commutemap.places.PlacesRateLimiter;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/**
 * Bad requests get a 400 with an RFC 9457 "problem details" body that names each wrong parameter:
 * {@code {"title":"Invalid request","errors":{"limit":"must be 15 to 90 minutes"}}}.
 */
@RestControllerAdvice
public class ApiErrorHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers,
                                                                  HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(invalid(errors(ex.getBindingResult())));
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers, HttpStatusCode status,
                                                                            WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        ex.getParameterValidationResults().forEach(r -> {
            if (r instanceof ParameterErrors pe) {
                // a whole object of parameters (SearchParams): report each of its fields
                errors.putAll(errors(pe));
            } else {
                errors.put(r.getMethodParameter().getParameterName(),
                        r.getResolvableErrors().stream().map(MessageSourceResolvable::getDefaultMessage)
                                .findFirst().orElse("is not valid"));
            }
        });
        return ResponseEntity.badRequest().body(invalid(errors));
    }

    @ExceptionHandler(BindException.class)
    ResponseEntity<ProblemDetail> bind(BindException ex) {
        return ResponseEntity.badRequest().body(invalid(errors(ex.getBindingResult())));
    }

    @ExceptionHandler(PlacesRateLimiter.RateLimitedException.class)
    ResponseEntity<ProblemDetail> rateLimited(PlacesRateLimiter.RateLimitedException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        p.setTitle("Too many requests");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds())).body(p);
    }

    @ExceptionHandler(NearbyController.GoogleNotConfiguredException.class)
    ResponseEntity<ProblemDetail> googleNotConfigured(NearbyController.GoogleNotConfiguredException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        p.setTitle("Not available");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(p);
    }

    @ExceptionHandler(GooglePlacesClient.GoogleUnavailableException.class)
    ResponseEntity<ProblemDetail> googleUnavailable(GooglePlacesClient.GoogleUnavailableException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        p.setTitle("Google Places is not answering");
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(p);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ProblemDetail> illegalArgument(IllegalArgumentException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        p.setTitle("Invalid request");
        return ResponseEntity.badRequest().body(p);
    }

    private static Map<String, String> errors(Errors result) {
        Map<String, String> errors = new TreeMap<>();
        for (FieldError e : result.getFieldErrors()) {
            String msg = e.isBindingFailure() ? "must be a number" : e.getDefaultMessage();
            errors.putIfAbsent(e.getField(), msg);
        }
        return errors;
    }

    private static ProblemDetail invalid(Map<String, String> errors) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Some parameters are not valid.");
        p.setTitle("Invalid request");
        p.setProperty("errors", errors);
        return p;
    }
}
