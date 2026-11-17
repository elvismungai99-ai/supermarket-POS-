package ke.retailpos.pos_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private final JwtAuthFilter jwtAuthFilter;
  private final boolean requireAuth;

  public SecurityConfig(
      JwtAuthFilter jwtAuthFilter,
      @Value("${pos.security.require-auth:true}") boolean requireAuth
  ) {
    this.jwtAuthFilter = jwtAuthFilter;
    this.requireAuth = requireAuth;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(headers -> headers
            .contentSecurityPolicy(csp -> csp.policyDirectives(
                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self' http://127.0.0.1:9001 http://localhost:9001; frame-ancestors 'none'"
            ))
            .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
            .frameOptions(frame -> frame.deny())
        )
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(auth -> {
          auth.requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll();
          auth.requestMatchers("/api/auth/login", "/api/auth/register", "/api/auth/logout", "/api/payments/mpesa-callback").permitAll();
          if (requireAuth) {
            auth.requestMatchers("/actuator/metrics", "/actuator/metrics/**", "/actuator/prometheus", "/actuator/info")
                .hasRole("SUPER_ADMIN");
            auth.requestMatchers("/api/auth/admin/**", "/api/admin/**", "/api/accounting/**").hasRole("SUPER_ADMIN");
            auth.requestMatchers("/api/auth/mfa/**").authenticated();
            auth.requestMatchers(HttpMethod.GET, "/api/customers").hasAnyRole("SUPERVISOR", "SUPER_ADMIN");
            auth.requestMatchers(HttpMethod.GET,
                "/api/reports/daily-sales",
                "/api/reports/tax-invoices/export",
                "/api/reports/monthly-tax",
                "/api/reports/monthly-tax/export")
                .hasAnyRole("SUPERVISOR", "SUPER_ADMIN");
            auth.requestMatchers("/api/reports/**").hasRole("SUPER_ADMIN");
            auth.requestMatchers(HttpMethod.POST, "/api/branches/**").hasRole("SUPER_ADMIN");
            auth.requestMatchers(HttpMethod.GET, "/api/branches/**").authenticated();
            auth.requestMatchers("/api/cashiers/**", "/api/inventory/**", "/api/returns/**")
                .hasAnyRole("SUPERVISOR", "SUPER_ADMIN");
            auth.requestMatchers(HttpMethod.POST, "/api/products/**").hasAnyRole("SUPERVISOR", "SUPER_ADMIN");
            auth.requestMatchers(HttpMethod.PUT, "/api/products/**").hasAnyRole("SUPERVISOR", "SUPER_ADMIN");
            auth.requestMatchers("/api/sales/**", "/api/shifts/**", "/api/auth/verify-supervisor-override")
                .hasRole("CASHIER");
            auth.requestMatchers("/api/**").authenticated();
          } else {
            auth.requestMatchers("/api/**").permitAll();
          }
          auth.anyRequest().authenticated();
        });
    return http.build();
  }
}
