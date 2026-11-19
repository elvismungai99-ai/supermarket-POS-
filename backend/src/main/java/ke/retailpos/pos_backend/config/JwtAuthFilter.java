package ke.retailpos.pos_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import ke.retailpos.pos_backend.auth.CashierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

  private final JwtService jwtService;
  private final CashierRepository cashierRepository;
  private final RevokedTokenRepository revokedTokenRepository;

  public JwtAuthFilter(JwtService jwtService) {
    this(jwtService, null, null);
  }

  public JwtAuthFilter(JwtService jwtService, CashierRepository cashierRepository) {
    this(jwtService, cashierRepository, null);
  }

  @Autowired
  public JwtAuthFilter(
      JwtService jwtService,
      CashierRepository cashierRepository,
      RevokedTokenRepository revokedTokenRepository
  ) {
    this.jwtService = jwtService;
    this.cashierRepository = cashierRepository;
    this.revokedTokenRepository = revokedTokenRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String authHeader = request.getHeader("Authorization");
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7).trim();
      JwtService.TokenClaims claims = jwtService.verifyToken(token);
      if (claims != null && !isRevoked(claims) && isStillActive(claims)) {
        var principal = new AuthenticatedUser(claims.cashierId(), claims.username(), claims.role());
        var auth = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_" + claims.role()))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
      }
    }
    filterChain.doFilter(request, response);
  }

  private boolean isRevoked(JwtService.TokenClaims claims) {
    if (revokedTokenRepository == null || claims.jti() == null) {
      return false;
    }
    return revokedTokenRepository.existsById(claims.jti());
  }

  private static final long ACTIVE_CACHE_TTL_MS = 15_000L;
  private static final int MAX_CACHE_ENTRIES = 2_000;
  private record CachedStatus(boolean active, long expiresAtMs) {}
  private static final Map<UUID, CachedStatus> activeStatusCache = new ConcurrentHashMap<>();

  public static void evictCashier(UUID cashierId) {
    if (cashierId != null) {
      activeStatusCache.remove(cashierId);
    }
  }

  private boolean isStillActive(JwtService.TokenClaims claims) {
    if (cashierRepository == null) {
      return true;
    }
    long now = System.currentTimeMillis();
    CachedStatus cached = activeStatusCache.get(claims.cashierId());
    if (cached != null && cached.expiresAtMs() > now) {
      return cached.active();
    }
    boolean active = cashierRepository.findById(claims.cashierId())
        .map(c -> c.isActive() && "ACTIVE".equalsIgnoreCase(c.getStatus()))
        .orElse(false);
    if (activeStatusCache.size() >= MAX_CACHE_ENTRIES) {
      activeStatusCache.entrySet().removeIf(e -> e.getValue().expiresAtMs() <= now);
    }
    activeStatusCache.put(claims.cashierId(), new CachedStatus(active, now + ACTIVE_CACHE_TTL_MS));
    return active;
  }
}
