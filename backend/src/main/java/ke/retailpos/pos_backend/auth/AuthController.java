package ke.retailpos.pos_backend.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import ke.retailpos.pos_backend.audit.AuditLogService;
import ke.retailpos.pos_backend.config.AuthenticatedUser;
import ke.retailpos.pos_backend.config.JwtService;
import ke.retailpos.pos_backend.config.RevokedToken;
import ke.retailpos.pos_backend.config.RevokedTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  public record LoginRequest(String username, String pin, String totpCode) {
    public LoginRequest(String username, String pin) {
      this(username, pin, null);
    }
  }
  public record LoginResponse(UUID cashierId, String username, String name, String role, String status, String token, boolean mfaEnabled) {
    public LoginResponse(UUID cashierId, String username, String name, String role) {
      this(cashierId, username, name, role, "ACTIVE", null, false);
    }
    public LoginResponse(UUID cashierId, String username, String name, String role, String status) {
      this(cashierId, username, name, role, status, null, false);
    }
    public LoginResponse(UUID cashierId, String username, String name, String role, String status, String token) {
      this(cashierId, username, name, role, status, token, false);
    }
  }
  public record ErrorResponse(String message, Long remainingLockSeconds, Integer remainingAttempts) {
    public ErrorResponse(String message) {
      this(message, null, null);
    }
  }

  public record RegisterRequest(
      @NotBlank String username,
      @NotBlank String name,
      @NotBlank String pin,
      String role
  ) {}

  public record RegisterResponse(
      UUID cashierId,
      String username,
      String name,
      String role,
      String status,
      String message
  ) {}

  public record SupervisorOverrideRequest(
      @NotBlank String username,
      @NotBlank String pin,
      String reason
  ) {}

  public record SupervisorOverrideResponse(
      boolean authorized,
      UUID supervisorId,
      String supervisorName,
      String supervisorRole,
      UUID overrideToken
  ) {
    public SupervisorOverrideResponse(boolean authorized, UUID supervisorId, String supervisorName, String supervisorRole) {
      this(authorized, supervisorId, supervisorName, supervisorRole, null);
    }
  }

  public record MfaCodeRequest(@NotBlank String code) {}
  public record MfaDisableRequest(@NotBlank String pin) {}

  private final CashierRepository cashiers;
  private final PasswordEncoder passwordEncoder;
  private final LoginRateLimiter rateLimiter;
  private final JwtService jwtService;
  private final AuditLogService auditLogService;
  private final RevokedTokenRepository revokedTokenRepository;
  private final SupervisorOverrideTokenRepository overrideTokenRepository;
  private final TotpService totpService;

  public AuthController(
      CashierRepository cashiers,
      PasswordEncoder passwordEncoder,
      LoginRateLimiter rateLimiter
  ) {
    this(
        cashiers,
        passwordEncoder,
        rateLimiter,
        new JwtService("kenya-retail-pos-dev-secret-change-in-production-2026", 3600, new ObjectMapper()),
        null,
        null,
        null,
        new TotpService()
    );
  }

  public AuthController(
      CashierRepository cashiers,
      PasswordEncoder passwordEncoder,
      LoginRateLimiter rateLimiter,
      JwtService jwtService,
      AuditLogService auditLogService
  ) {
    this(cashiers, passwordEncoder, rateLimiter, jwtService, auditLogService, null, null, new TotpService());
  }

  @Autowired
  public AuthController(
      CashierRepository cashiers,
      PasswordEncoder passwordEncoder,
      LoginRateLimiter rateLimiter,
      JwtService jwtService,
      AuditLogService auditLogService,
      RevokedTokenRepository revokedTokenRepository,
      SupervisorOverrideTokenRepository overrideTokenRepository,
      TotpService totpService
  ) {
    this.cashiers = cashiers;
    this.passwordEncoder = passwordEncoder;
    this.rateLimiter = rateLimiter;
    this.jwtService = jwtService;
    this.auditLogService = auditLogService;
    this.revokedTokenRepository = revokedTokenRepository;
    this.overrideTokenRepository = overrideTokenRepository;
    this.totpService = totpService != null ? totpService : new TotpService();
  }

  @PostMapping("/login")
  public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    if (request.username() == null || request.pin() == null || request.username().isBlank()) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Username and PIN are required"));
    }

    String username = request.username().trim();
    String pin = request.pin().trim();
    String clientIp = AuditLogService.currentClientIp();

    if (rateLimiter.isBlocked(username, clientIp)) {
      long remainingSec = rateLimiter.getRemainingLockSeconds(username, clientIp);
      recordAudit(null, username, null, "LOGIN_LOCKED", "cashier", username, clientIp, "Rate limit active");
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ErrorResponse(
          "Account temporarily locked due to multiple failed login attempts. Please wait " + remainingSec + " seconds or ask your manager to reset your PIN.",
          remainingSec,
          0
      ));
    }

    var opt = cashiers.findByUsername(username);
    if (opt.isEmpty()) {
      rateLimiter.recordFailedAttempt(username, clientIp);
      recordAudit(null, username, null, "LOGIN_FAILED", "cashier", username, clientIp, "Unknown username");
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(new ErrorResponse("Invalid username or PIN"));
    }

    Cashier c = opt.get();

    if ("PENDING_APPROVAL".equalsIgnoreCase(c.getStatus()) || (!c.isActive() && !"DELETED".equalsIgnoreCase(c.getStatus()))) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(
          "Account pending manager authorization. Please request a store manager/admin to authorize your account before signing in."
      ));
    }

    if ("DELETED".equalsIgnoreCase(c.getStatus()) || "DISABLED".equalsIgnoreCase(c.getStatus())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(
          "This account has been disabled or removed. Please contact your administrator."
      ));
    }

    if (!passwordEncoder.matches(pin, c.getPinHash())) {
      rateLimiter.recordFailedAttempt(username, clientIp);
      if (rateLimiter.isBlocked(username, clientIp)) {
        long remainingSec = rateLimiter.getRemainingLockSeconds(username, clientIp);
        recordAudit(c.getId(), c.getUsername(), c.getRole(), "LOGIN_LOCKED", "cashier", c.getId().toString(), clientIp, "Locked after 5 failed attempts");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(new ErrorResponse(
            "Account temporarily locked for " + remainingSec + " seconds due to 5 consecutive failed attempts. Contact your manager if you forgot your PIN.",
            remainingSec,
            0
        ));
      } else {
        int remaining = LoginRateLimiter.MAX_ATTEMPTS - rateLimiter.getFailedAttempts(username);
        recordAudit(c.getId(), c.getUsername(), c.getRole(), "LOGIN_FAILED", "cashier", c.getId().toString(), clientIp, "Invalid PIN");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(
            "Invalid username or PIN. " + remaining + " attempt(s) remaining before temporary lockout.",
            null,
            remaining
        ));
      }
    }

    if (c.isMfaEnabled() && c.getMfaSecret() != null) {
      String code = request.totpCode();
      if (code == null || code.isBlank()) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(
            "MFA_REQUIRED: Enter your 6-digit authenticator code."
        ));
      }
      if (!totpService.verifyCode(c.getMfaSecret(), code)) {
        rateLimiter.recordFailedAttempt(username, clientIp);
        recordAudit(c.getId(), c.getUsername(), c.getRole(), "LOGIN_MFA_FAILED", "cashier", c.getId().toString(), clientIp, "Invalid TOTP code");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(
            "Invalid 6-digit authenticator code."
        ));
      }
    }

    rateLimiter.reset(username, clientIp);
    String token = jwtService.generateToken(c.getId(), c.getUsername(), c.getRole());
    recordAudit(c.getId(), c.getUsername(), c.getRole(), "LOGIN_SUCCESS", "cashier", c.getId().toString(), clientIp, "Signed in");

    return ResponseEntity.ok(new LoginResponse(
        c.getId(),
        c.getUsername(),
        c.getName(),
        c.getRole(),
        c.getStatus(),
        token,
        c.isMfaEnabled()
    ));
  }

  @PostMapping("/logout")
  public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7).trim();
      JwtService.TokenClaims claims = jwtService.verifyToken(token);
      if (claims != null) {
        if (revokedTokenRepository != null && claims.jti() != null && !claims.jti().isBlank()) {
          if (!revokedTokenRepository.existsById(claims.jti())) {
            Instant exp = claims.expiresAt() != null ? claims.expiresAt() : Instant.now().plusSeconds(3600);
            revokedTokenRepository.save(new RevokedToken(claims.jti(), exp));
          }
        }
        recordAudit(
            claims.cashierId(),
            claims.username(),
            claims.role(),
            "LOGOUT",
            "session",
            claims.jti() != null ? claims.jti() : "unknown",
            AuditLogService.currentClientIp(),
            "Signed out and revoked JWT"
        );
      }
    }
    return ResponseEntity.ok(Map.of("message", "Signed out and session token revoked."));
  }

  @PostMapping("/mfa/setup")
  public ResponseEntity<?> setupMfa() {
    AuthenticatedUser actor = AuditLogService.currentActor();
    if (actor == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Authentication required"));
    }
    var opt = cashiers.findById(actor.userId());
    if (opt.isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("User not found"));
    }
    Cashier c = opt.get();
    String secret = totpService.generateSecret();
    c.setMfaSecret(secret);
    c.setMfaEnabled(false);
    cashiers.save(c);
    String otpAuthUri = totpService.buildOtpAuthUri("KenyaRetailPOS", c.getUsername(), secret);
    recordAudit(c.getId(), c.getUsername(), c.getRole(), "MFA_SETUP_INITIATED", "cashier", c.getId().toString(), AuditLogService.currentClientIp(), "Generated TOTP secret");
    return ResponseEntity.ok(Map.of(
        "secret", secret,
        "otpAuthUri", otpAuthUri,
        "issuer", "KenyaRetailPOS",
        "username", c.getUsername()
    ));
  }

  @PostMapping("/mfa/enable")
  public ResponseEntity<?> enableMfa(@Valid @RequestBody MfaCodeRequest req) {
    AuthenticatedUser actor = AuditLogService.currentActor();
    if (actor == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Authentication required"));
    }
    var opt = cashiers.findById(actor.userId());
    if (opt.isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("User not found"));
    }
    Cashier c = opt.get();
    if (c.getMfaSecret() == null || c.getMfaSecret().isBlank()) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Run MFA setup first to generate a secret."));
    }
    if (!totpService.verifyCode(c.getMfaSecret(), req.code())) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid 6-digit verification code."));
    }
    c.setMfaEnabled(true);
    cashiers.save(c);
    recordAudit(c.getId(), c.getUsername(), c.getRole(), "MFA_ENABLED", "cashier", c.getId().toString(), AuditLogService.currentClientIp(), "Enabled TOTP 2FA");
    return ResponseEntity.ok(Map.of("mfaEnabled", true, "message", "Two-factor authentication enabled."));
  }

  @PostMapping("/mfa/disable")
  public ResponseEntity<?> disableMfa(@Valid @RequestBody MfaDisableRequest req) {
    AuthenticatedUser actor = AuditLogService.currentActor();
    if (actor == null) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Authentication required"));
    }
    var opt = cashiers.findById(actor.userId());
    if (opt.isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse("User not found"));
    }
    Cashier c = opt.get();
    if (!passwordEncoder.matches(req.pin().trim(), c.getPinHash())) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse("Invalid PIN."));
    }
    c.setMfaEnabled(false);
    c.setMfaSecret(null);
    cashiers.save(c);
    recordAudit(c.getId(), c.getUsername(), c.getRole(), "MFA_DISABLED", "cashier", c.getId().toString(), AuditLogService.currentClientIp(), "Disabled TOTP 2FA");
    return ResponseEntity.ok(Map.of("mfaEnabled", false, "message", "Two-factor authentication disabled."));
  }

  @PostMapping("/register")
  public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
    String username = req.username().trim();
    if (cashiers.findByUsername(username).isPresent()) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
          "Username '" + username + "' is already taken. Please choose another username."
      ));
    }

    String role = req.role() != null && !req.role().isBlank() ? req.role().trim().toUpperCase() : "CASHIER";
    if (!"CASHIER".equals(role) && !"SUPERVISOR".equals(role) && !"SUPER_ADMIN".equals(role)) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Unsupported role: " + role));
    }

    AuthenticatedUser actor = AuditLogService.currentActor();
    boolean isCallerSuperAdmin = actor != null && "SUPER_ADMIN".equalsIgnoreCase(actor.role());
    boolean isInitialSuperAdminBootstrap = "SUPER_ADMIN".equals(role)
        && cashiers.countByRoleIgnoreCaseAndActiveTrue("SUPER_ADMIN") == 0;

    if (!"CASHIER".equals(role) && !isCallerSuperAdmin && !isInitialSuperAdminBootstrap) {
      recordAudit(
          actor != null ? actor.userId() : null,
          username,
          role,
          "PRIVILEGED_REGISTER_BLOCKED",
          "cashier",
          username,
          AuditLogService.currentClientIp(),
          "Blocked self-registration attempt for privileged role " + role
      );
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(
          "Privileged roles (Manager / Super Admin) cannot self-register. Ask an authenticated Super Admin to create your account."
      ));
    }

    Optional<String> pinErr = PinPolicy.validate(req.pin());
    if (pinErr.isPresent()) {
      return ResponseEntity.badRequest().body(new ErrorResponse(pinErr.get()));
    }

    return createAccountInternal(req, role);
  }

  /**
   * Dedicated Super Admin provisioning endpoint for Managers, Super Admins, and pre-approved Cashiers.
   */
  @PostMapping("/admin/create-user")
  public ResponseEntity<?> adminCreateUser(@Valid @RequestBody RegisterRequest req) {
    String username = req.username().trim();
    if (cashiers.findByUsername(username).isPresent()) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(
          "Username '" + username + "' is already taken."
      ));
    }
    String role = req.role() != null && !req.role().isBlank() ? req.role().trim().toUpperCase() : "SUPERVISOR";
    if (!"CASHIER".equals(role) && !"SUPERVISOR".equals(role) && !"SUPER_ADMIN".equals(role)) {
      return ResponseEntity.badRequest().body(new ErrorResponse("Unsupported role: " + role));
    }
    Optional<String> pinErr = PinPolicy.validate(req.pin());
    if (pinErr.isPresent()) {
      return ResponseEntity.badRequest().body(new ErrorResponse(pinErr.get()));
    }

    Cashier user = new Cashier(
        UUID.randomUUID(),
        username,
        req.name().trim(),
        passwordEncoder.encode(req.pin().trim()),
        role,
        true,
        "ACTIVE",
        Instant.now()
    );
    cashiers.save(user);
    recordAudit(
        null, null, null,
        "ADMIN_PROVISIONED_USER",
        "cashier",
        user.getId().toString(),
        AuditLogService.currentClientIp(),
        "Provisioned user @" + username + " with role " + role
    );
    return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(
        user.getId(),
        user.getUsername(),
        user.getName(),
        user.getRole(),
        user.getStatus(),
        "User account @" + username + " (" + role + ") created and activated."
    ));
  }

  /**
   * Verifies a Store Manager / Super Admin PIN for real-time till overrides (line void / item discount).
   */
  @PostMapping("/verify-supervisor-override")
  public ResponseEntity<?> verifySupervisorOverride(@Valid @RequestBody SupervisorOverrideRequest req) {
    String username = req.username().trim();
    String clientIp = AuditLogService.currentClientIp();

    if (rateLimiter.isBlocked(username, clientIp)) {
      long remainingSec = rateLimiter.getRemainingLockSeconds(username, clientIp);
      return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
          .body(new ErrorResponse("Supervisor account temporarily locked for " + remainingSec + "s.", remainingSec, 0));
    }

    var opt = cashiers.findByUsername(username);
    if (opt.isEmpty()) {
      rateLimiter.recordFailedAttempt(username, clientIp);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(new ErrorResponse("Invalid supervisor credentials"));
    }

    Cashier sup = opt.get();
    boolean isPrivileged = "SUPERVISOR".equalsIgnoreCase(sup.getRole()) || "SUPER_ADMIN".equalsIgnoreCase(sup.getRole());
    if (!isPrivileged || !sup.isActive() || !"ACTIVE".equalsIgnoreCase(sup.getStatus())) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(new ErrorResponse("Only active Store Managers or Super Admins can authorize till overrides."));
    }

    if (!passwordEncoder.matches(req.pin().trim(), sup.getPinHash())) {
      rateLimiter.recordFailedAttempt(username, clientIp);
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
          .body(new ErrorResponse("Invalid supervisor PIN"));
    }

    rateLimiter.reset(username, clientIp);
    AuthenticatedUser cashierActor = AuditLogService.currentActor();
    UUID overrideTokenId = UUID.randomUUID();
    if (overrideTokenRepository != null) {
      overrideTokenRepository.save(new SupervisorOverrideToken(
          overrideTokenId,
          sup.getId(),
          cashierActor != null ? cashierActor.userId() : null,
          req.reason(),
          Instant.now().plusSeconds(300)
      ));
    }
    recordAudit(
        sup.getId(),
        sup.getUsername(),
        sup.getRole(),
        "SUPERVISOR_TILL_OVERRIDE",
        "till",
        cashierActor != null ? cashierActor.userId().toString() : "unknown",
        clientIp,
        "Authorized till override" + (req.reason() != null ? ": " + req.reason() : "")
    );

    return ResponseEntity.ok(new SupervisorOverrideResponse(
        true,
        sup.getId(),
        sup.getName(),
        sup.getRole(),
        overrideTokenId
    ));
  }

  private ResponseEntity<?> createAccountInternal(RegisterRequest req, String role) {
    String username = req.username().trim();
    boolean isManagerOrSuperAdmin = "SUPER_ADMIN".equals(role) || "SUPERVISOR".equals(role);
    boolean active = isManagerOrSuperAdmin;
    String status = isManagerOrSuperAdmin ? "ACTIVE" : "PENDING_APPROVAL";

    Cashier newCashier = new Cashier(
        UUID.randomUUID(),
        username,
        req.name().trim(),
        passwordEncoder.encode(req.pin().trim()),
        role,
        active,
        status,
        Instant.now()
    );
    cashiers.save(newCashier);

    recordAudit(
        newCashier.getId(),
        newCashier.getUsername(),
        role,
        "USER_REGISTERED",
        "cashier",
        newCashier.getId().toString(),
        AuditLogService.currentClientIp(),
        "Registered with status " + status
    );

    String message;
    if ("SUPER_ADMIN".equals(role)) {
      message = "Super Admin account created successfully. You can now sign in.";
    } else if ("SUPERVISOR".equals(role)) {
      message = "Admin / Manager account created successfully. You can now sign in.";
    } else {
      message = "Account created successfully. A manager must authorize your account before you can log in.";
    }

    return ResponseEntity.status(HttpStatus.CREATED).body(new RegisterResponse(
        newCashier.getId(),
        newCashier.getUsername(),
        newCashier.getName(),
        newCashier.getRole(),
        newCashier.getStatus(),
        message
    ));
  }

  private void recordAudit(
      UUID actorId,
      String actorUsername,
      String actorRole,
      String action,
      String targetEntity,
      String targetId,
      String ip,
      String details
  ) {
    if (auditLogService != null) {
      auditLogService.record(actorId, actorUsername, actorRole, action, targetEntity, targetId, ip, details);
    }
  }
}
