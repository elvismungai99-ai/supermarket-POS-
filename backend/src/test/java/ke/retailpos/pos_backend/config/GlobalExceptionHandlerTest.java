package ke.retailpos.pos_backend.config;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void testStandardizedErrorEnvelopesForDomainExceptions() {
    var bad = handler.handleBadArgument(new IllegalArgumentException("Invalid month format"));
    assertEquals(HttpStatus.BAD_REQUEST, bad.getStatusCode());
    assertEquals("BAD_REQUEST", bad.getBody().code());
    assertEquals("Invalid month format", bad.getBody().message());
    assertNotNull(bad.getBody().timestamp());

    var conflict = handler.handleConflictState(new IllegalStateException("Shift already open"));
    assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
    assertEquals("CONFLICT", conflict.getBody().code());

    var integrity = handler.handleDataIntegrity(new DataIntegrityViolationException("duplicate key"));
    assertEquals(HttpStatus.CONFLICT, integrity.getStatusCode());
    assertEquals("DATA_INTEGRITY_VIOLATION", integrity.getBody().code());

    var denied = handler.handleAccessDenied(new AccessDeniedException("Denied"));
    assertEquals(HttpStatus.FORBIDDEN, denied.getStatusCode());
    assertEquals("FORBIDDEN", denied.getBody().code());
  }
}
