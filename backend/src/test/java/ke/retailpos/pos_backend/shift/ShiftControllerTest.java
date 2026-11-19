package ke.retailpos.pos_backend.shift;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import ke.retailpos.pos_backend.auth.Cashier;
import ke.retailpos.pos_backend.auth.CashierRepository;
import ke.retailpos.pos_backend.config.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class ShiftControllerTest {

  private ShiftRepository shiftRepository;
  private CashierRepository cashierRepository;
  private ShiftController shiftController;

  @BeforeEach
  void setUp() {
    shiftRepository = mock(ShiftRepository.class);
    cashierRepository = mock(CashierRepository.class);
    shiftController = new ShiftController(shiftRepository, cashierRepository);
  }

  @Test
  void testManagerCannotOpenTillShift() {
    UUID managerId = UUID.randomUUID();
    UUID tillId = UUID.randomUUID();
    Cashier manager = new Cashier(managerId, "mgr_sarah", "Sarah Auma", "hash", "SUPERVISOR", true, "ACTIVE", Instant.now());

    when(shiftRepository.findFirstByTillIdAndStatusOrderByOpenedAtDesc(tillId, "OPEN")).thenReturn(Optional.empty());
    when(cashierRepository.findById(managerId)).thenReturn(Optional.of(manager));

    ResponseEntity<?> resp = shiftController.openShift(new ShiftController.OpenShiftRequest(tillId, managerId, new BigDecimal("1000.00")));

    assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    assertTrue(resp.getBody() instanceof ShiftController.ErrorResponse);
    ShiftController.ErrorResponse err = (ShiftController.ErrorResponse) resp.getBody();
    assertTrue(err.message().contains("Only cashiers are permitted to open shifts"));
  }

  @Test
  void testCashierCanOpenTillShift() {
    UUID cashierId = UUID.randomUUID();
    UUID tillId = UUID.randomUUID();
    Cashier cashier = new Cashier(cashierId, "cashier_kevin", "Kevin Mutua", "hash", "CASHIER", true, "ACTIVE", Instant.now());

    when(shiftRepository.findFirstByTillIdAndStatusOrderByOpenedAtDesc(tillId, "OPEN")).thenReturn(Optional.empty());
    when(cashierRepository.findById(cashierId)).thenReturn(Optional.of(cashier));

    Shift saved = new Shift(UUID.randomUUID(), tillId, cashier, Instant.now(), new BigDecimal("1000.00"), "OPEN");
    when(shiftRepository.save(any(Shift.class))).thenReturn(saved);

    ResponseEntity<?> resp = shiftController.openShift(new ShiftController.OpenShiftRequest(tillId, cashierId, new BigDecimal("1000.00")));

    assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    assertTrue(resp.getBody() instanceof ShiftController.ShiftDto);
    ShiftController.ShiftDto dto = (ShiftController.ShiftDto) resp.getBody();
    assertEquals("Kevin Mutua", dto.cashierName());
    assertEquals("OPEN", dto.status());
  }

  @Test
  void testCashierReopenShiftIsIdempotent() {
    UUID cashierId = UUID.randomUUID();
    UUID tillId = UUID.randomUUID();
    Cashier cashier = new Cashier(cashierId, "cashier_kevin", "Kevin Mutua", "hash", "CASHIER", true, "ACTIVE", Instant.now());
    Shift existing = new Shift(UUID.randomUUID(), tillId, cashier, Instant.now(), new BigDecimal("1000.00"), "OPEN");

    when(cashierRepository.findById(cashierId)).thenReturn(Optional.of(cashier));
    when(shiftRepository.findFirstByTillIdAndStatusOrderByOpenedAtDesc(tillId, "OPEN")).thenReturn(Optional.of(existing));

    ResponseEntity<?> resp = shiftController.openShift(new ShiftController.OpenShiftRequest(tillId, cashierId, new BigDecimal("1000.00")));

    assertEquals(HttpStatus.OK, resp.getStatusCode());
    assertTrue(resp.getBody() instanceof ShiftController.ShiftDto);
    ShiftController.ShiftDto dto = (ShiftController.ShiftDto) resp.getBody();
    assertEquals("Kevin Mutua", dto.cashierName());
    assertEquals("OPEN", dto.status());
  }

  @Test
  void testDifferentCashierCannotOpenShiftWhenTillOccupied() {
    UUID cashier1Id = UUID.randomUUID();
    UUID cashier2Id = UUID.randomUUID();
    UUID tillId = UUID.randomUUID();
    Cashier cashier1 = new Cashier(cashier1Id, "cashier_kevin", "Kevin Mutua", "hash", "CASHIER", true, "ACTIVE", Instant.now());
    Cashier cashier2 = new Cashier(cashier2Id, "cashier_john", "John Kamau", "hash", "CASHIER", true, "ACTIVE", Instant.now());
    Shift existing = new Shift(UUID.randomUUID(), tillId, cashier1, Instant.now(), new BigDecimal("1000.00"), "OPEN");

    when(cashierRepository.findById(cashier2Id)).thenReturn(Optional.of(cashier2));
    when(shiftRepository.findFirstByTillIdAndStatusOrderByOpenedAtDesc(tillId, "OPEN")).thenReturn(Optional.of(existing));

    ResponseEntity<?> resp = shiftController.openShift(new ShiftController.OpenShiftRequest(tillId, cashier2Id, new BigDecimal("500.00")));

    assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
    assertTrue(resp.getBody() instanceof ShiftController.ErrorResponse);
    ShiftController.ErrorResponse err = (ShiftController.ErrorResponse) resp.getBody();
    assertTrue(err.message().contains("Till is already occupied by cashier Kevin Mutua"));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void testGetCurrentShiftReturnsNoContentWhenNoneOpen() {
    UUID tillId = UUID.randomUUID();
    when(shiftRepository.findFirstByTillIdAndStatusOrderByOpenedAtDesc(tillId, "OPEN")).thenReturn(Optional.empty());

    ResponseEntity<?> resp = shiftController.getCurrentShift(tillId);

    assertEquals(HttpStatus.NO_CONTENT, resp.getStatusCode());
  }

  @Test
  void testDifferentCashierCannotCloseAnotherCashiersShift() {
    UUID ownerId = UUID.randomUUID();
    UUID otherCashierId = UUID.randomUUID();
    UUID shiftId = UUID.randomUUID();
    Cashier owner = new Cashier(ownerId, "cashier_a", "Cashier A", "hash", "CASHIER", true, "ACTIVE", Instant.now());
    Shift openShift = new Shift(shiftId, UUID.randomUUID(), owner, Instant.now(), new BigDecimal("1000.00"), "OPEN");

    when(shiftRepository.findById(shiftId)).thenReturn(Optional.of(openShift));

    var otherPrincipal = new AuthenticatedUser(otherCashierId, "cashier_b", "CASHIER");
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(otherPrincipal, null, java.util.List.of())
    );

    ResponseEntity<?> blocked = shiftController.closeShift(new ShiftController.CloseShiftRequest(shiftId, new BigDecimal("0.00")));
    assertEquals(HttpStatus.FORBIDDEN, blocked.getStatusCode());
    verify(shiftRepository, never()).save(any());

    // Owner closing their own shift succeeds
    var ownerPrincipal = new AuthenticatedUser(ownerId, "cashier_a", "CASHIER");
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(ownerPrincipal, null, java.util.List.of())
    );
    when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> inv.getArgument(0));
    ResponseEntity<?> allowed = shiftController.closeShift(new ShiftController.CloseShiftRequest(shiftId, new BigDecimal("1000.00")));
    assertEquals(HttpStatus.OK, allowed.getStatusCode());
  }
}
