package ke.retailpos.pos_backend.config;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler returning standardized JSON error envelopes
 * (code, timestamp, message, fieldErrors) across all REST controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  public record ApiError(
      String code,
      Instant timestamp,
      String message,
      Map<String, String> fieldErrors
  ) {
    public ApiError(String code, String message) {
      this(code, Instant.now(), message, Map.of());
    }
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
    Map<String, String> fields = new LinkedHashMap<>();
    ex.getBindingResult().getFieldErrors()
        .forEach(fe -> fields.putIfAbsent(fe.getField(), fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid value"));

    String summary = fields.isEmpty()
        ? "Validation failed for request payload."
        : fields.entrySet().stream()
            .map(e -> e.getKey() + ": " + e.getValue())
            .collect(Collectors.joining("; "));

    return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", Instant.now(), summary, fields));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> handleUnreadableJson(HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest().body(new ApiError("MALFORMED_REQUEST", "Invalid or unreadable JSON request payload."));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> handleBadArgument(IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(new ApiError("BAD_REQUEST", ex.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiError> handleConflictState(IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("CONFLICT", ex.getMessage()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiError("DATA_INTEGRITY_VIOLATION", "Database constraint violation (duplicate or invalid reference)."));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ApiError("FORBIDDEN", "You do not have permission to perform this action."));
  }
}
