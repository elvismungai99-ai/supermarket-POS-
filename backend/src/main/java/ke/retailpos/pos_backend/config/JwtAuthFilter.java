package ke.retailpos.pos_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
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

  private boolean isStillActive(JwtService.TokenClaims claims) {
    if (cashierRepository == null) {
      return true;
    }
    return cashierRepository.findById(claims.cashierId())
        .map(c -> c.isActive() && "ACTIVE".equalsIgnoreCase(c.getStatus()))
        .orElse(false);
  }
}
