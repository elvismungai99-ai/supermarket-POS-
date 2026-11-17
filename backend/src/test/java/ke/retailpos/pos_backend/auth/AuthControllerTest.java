package ke.retailpos.pos_backend.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import ke.retailpos.pos_backend.config.JwtService;
import ke.retailpos.pos_backend.config.RevokedToken;
import ke.retailpos.pos_backend.config.RevokedTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

class AuthControllerTest {

  private CashierRepository cashierRepository;
  private PasswordEncoder passwordEncoder;
  private LoginRateLimiter rateLimiter;
  private JwtService jwtService;
  private RevokedTokenRepository revokedTokenRepository;
  private SupervisorOverrideTokenRepository overrideTokenRepository;
  private TotpService totpService;
  private AuthController authController;

  @BeforeEach
  void setUp() {
    cashierRepository = mock(CashierRepository.class);
    passwordEncoder = new BCryptPasswordEncoder();
    rateLimiter = new LoginRateLimiter();
    jwtService = new JwtService("kenya-retail-pos-dev-secret-change-in-production-2026", 3600, new ObjectMapper());
    revokedTokenRepository = mock(RevokedTokenRepository.class);
    overrideTokenRepository = mock(SupervisorOverrideTokenRepository.class);
    totpService = new TotpService();
    authController = new AuthController(
        cashierRepository,
        passwordEncoder,
        rateLimiter,
        jwtService,
        null,
        revokedTokenRepository,
        overrideTokenRepository,
        totpService
    );
  }

  @Test
  void testSuccessfulLogin() {
    String pin = "1234";
    String hash = passwordEncoder.encode(pin);
    Cashier cashier = new Cashier(UUID.randomUUID(), "test_user", "Test Cashier", hash, "CASHIER", true, "ACTIVE", null);
    when(cashierRepository.findByUsername("test_user")).thenReturn(Optional.of(cashier));

    ResponseEntity<?> resp = authController.login(new AuthController.LoginRequest("test_user", "1234"));

    assertEquals(HttpStatus.OK, resp.getStatusCode());
    assertTrue(resp.getBody() instanceof AuthController.LoginResponse);
    AuthController.LoginResponse body = (AuthController.LoginResponse) resp.getBody();
    assertEquals("test_user", body.username());
    assertEquals("CASHIER", body.role());
    assertEquals("ACTIVE", body.status());
    assertNotNull(body.token());
  }

  @Test
  void testPendingApprovalAccountCannotLogin() {
    String pin = "1234";
    String hash = passwordEncoder.encode(pin);
    Cashier cashier = new Cashier(UUID.randomUUID(), "new_cashier", "Bob Otieno", hash, "CASHIER", false, "PENDING_APPROVAL", null);
    when(cashierRepository.findByUsername("new_cashier")).thenReturn(Optional.of(cashier));

    ResponseEntity<?> resp = authController.login(new AuthController.LoginRequest("new_cashier", "1234"));

    assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
    assertTrue(resp.getBody() instanceof AuthController.ErrorResponse);
    AuthController.ErrorResponse err = (AuthController.ErrorResponse) resp.getBody();
    assertTrue(err.message().contains("pending manager authorization"));
  }

  @Test
  void testRateLimitingAfter5FailedAttempts() {
    String realHash = passwordEncoder.encode("correct_pin");
    Cashier cashier = new Cashier(UUID.randomUUID(), "john", "John Doe", realHash, "CASHIER", true, "ACTIVE", null);
    when(cashierRepository.findByUsername("john")).thenReturn(Optional.of(cashier));

    for (int i = 0; i < 4; i++) {
      ResponseEntity<?> r = authController.login(new AuthController.LoginRequest("john", "wrong_pin"));
      assertEquals(HttpStatus.UNAUTHORIZED, r.getStatusCode());
    }

    ResponseEntity<?> r5 = authController.login(new AuthController.LoginRequest("john", "wrong_pin"));
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, r5.getStatusCode());

    ResponseEntity<?> r6 = authController.login(new AuthController.LoginRequest("john", "correct_pin"));
    assertEquals(HttpStatus.TOO_MANY_REQUESTS, r6.getStatusCode());

    rateLimiter.reset("john");

    ResponseEntity<?> rSuccess = authController.login(new AuthController.LoginRequest("john", "correct_pin"));
    assertEquals(HttpStatus.OK, rSuccess.getStatusCode());
  }

  @Test
  void testRegisterNewCashierSetsPendingApproval() {
    when(cashierRepository.findByUsername("alice")).thenReturn(Optional.empty());

    ResponseEntity<?> resp = authController.register(new AuthController.RegisterRequest("alice", "Alice Wambui", "482910", "CASHIER"));

    assertEquals(HttpStatus.CREATED, resp.getStatusCode());
    assertTrue(resp.getBody() instanceof AuthController.RegisterResponse);
    AuthController.RegisterResponse regResp = (AuthController.RegisterResponse) resp.getBody();
    assertEquals("alice", regResp.username());
    assertEquals("PENDING_APPROVAL", regResp.status());
    verify(cashierRepository).save(any(Cashier.class));
  }

  @Test
  void testWeakPinRejectedOnRegistration() {
    when(cashierRepository.findByUsername("bob")).thenReturn(Optional.empty());

    ResponseEntity<?> resp1 = authController.register(new AuthController.RegisterRequest("bob", "Bob", "1234", "CASHIER"));
    assertEquals(HttpStatus.BAD_REQUEST, resp1.getStatusCode());

    ResponseEntity<?> resp2 = authController.register(new AuthController.RegisterRequest("bob", "Bob", "123456", "CASHIER"));
    assertEquals(HttpStatus.BAD_REQUEST, resp2.getStatusCode());

    ResponseEntity<?> resp3 = authController.register(new AuthController.RegisterRequest("bob", "Bob", "000000", "CASHIER"));
    assertEquals(HttpStatus.BAD_REQUEST, resp3.getStatusCode());
  }

  @Test
  void testBootstrapSuperAdminAllowedOnlyWhenNoneExistThenBlocked() {
    when(cashierRepository.findByUsername("super_mwangi")).thenReturn(Optional.empty());
    when(cashierRepository.countByRoleIgnoreCaseAndActiveTrue("SUPER_ADMIN")).thenReturn(0L);

    ResponseEntity<?> resp = authController.register(new AuthController.RegisterRequest("super_mwangi", "Super Mwangi", "739204", "SUPER_ADMIN"));
    assertEquals(HttpStatus.CREATED, resp.getStatusCode());

    // Once a Super Admin exists, unauthenticated self-registration as SUPER_ADMIN or SUPERVISOR is forbidden (403)
    when(cashierRepository.findByUsername("attacker")).thenReturn(Optional.empty());
    when(cashierRepository.countByRoleIgnoreCaseAndActiveTrue("SUPER_ADMIN")).thenReturn(1L);

    ResponseEntity<?> blockedAdmin = authController.register(new AuthController.RegisterRequest("attacker", "Attacker", "482910", "SUPER_ADMIN"));
    assertEquals(HttpStatus.FORBIDDEN, blockedAdmin.getStatusCode());

    ResponseEntity<?> blockedMgr = authController.register(new AuthController.RegisterRequest("attacker_mgr", "Fake Mgr", "482910", "SUPERVISOR"));
    assertEquals(HttpStatus.FORBIDDEN, blockedMgr.getStatusCode());
  }

  @Test
  void testVerifySupervisorOverrideSuccessAndRejectsCashier() {
    String hash = passwordEncoder.encode("9999");
    Cashier mgr = new Cashier(UUID.randomUUID(), "mgr_sarah", "Sarah Auma", hash, "SUPERVISOR", true, "ACTIVE", null);
    Cashier cashier = new Cashier(UUID.randomUUID(), "c1", "Cashier One", hash, "CASHIER", true, "ACTIVE", null);

    when(cashierRepository.findByUsername("mgr_sarah")).thenReturn(Optional.of(mgr));
    when(cashierRepository.findByUsername("c1")).thenReturn(Optional.of(cashier));

    ResponseEntity<?> okResp = authController.verifySupervisorOverride(
        new AuthController.SupervisorOverrideRequest("mgr_sarah", "9999", "Void milk")
    );
    assertEquals(HttpStatus.OK, okResp.getStatusCode());
    var overrideBody = (AuthController.SupervisorOverrideResponse) okResp.getBody();
    assertTrue(overrideBody.authorized());
    assertNotNull(overrideBody.overrideToken());
    verify(overrideTokenRepository).save(any(SupervisorOverrideToken.class));

    ResponseEntity<?> deniedResp = authController.verifySupervisorOverride(
        new AuthController.SupervisorOverrideRequest("c1", "9999", "Void milk")
    );
    assertEquals(HttpStatus.FORBIDDEN, deniedResp.getStatusCode());
  }

  @Test
  void testLogoutRevokesJwtAndTotpChallengeEnforced() {
    String secret = totpService.generateSecret();
    Cashier admin = new Cashier(
        UUID.randomUUID(), "chief", "Chief Admin", passwordEncoder.encode("739204"), "SUPER_ADMIN", true, "ACTIVE", Instant.now()
    );
    admin.setMfaSecret(secret);
    admin.setMfaEnabled(true);
    when(cashierRepository.findByUsername("chief")).thenReturn(Optional.of(admin));

    // Missing TOTP fails with MFA_REQUIRED
    ResponseEntity<?> missingMfa = authController.login(new AuthController.LoginRequest("chief", "739204"));
    assertEquals(HttpStatus.UNAUTHORIZED, missingMfa.getStatusCode());
    assertTrue(((AuthController.ErrorResponse) missingMfa.getBody()).message().startsWith("MFA_REQUIRED"));

    // Valid TOTP succeeds and issues token
    String code = totpService.generateCurrentCode(secret);
    ResponseEntity<?> okLogin = authController.login(new AuthController.LoginRequest("chief", "739204", code));
    assertEquals(HttpStatus.OK, okLogin.getStatusCode());
    String token = ((AuthController.LoginResponse) okLogin.getBody()).token();
    assertNotNull(token);

    // Logout revokes token jti
    ResponseEntity<?> logoutResp = authController.logout("Bearer " + token);
    assertEquals(HttpStatus.OK, logoutResp.getStatusCode());
    verify(revokedTokenRepository).save(any(RevokedToken.class));
  }
}
