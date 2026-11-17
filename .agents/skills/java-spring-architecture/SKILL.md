---
name: java-spring-architecture
description: Enforces standard Java Spring Boot backend packaging, DTO validation rules, global exception handling, and repository patterns for supermarketPOS.
---

# Java Spring Boot Architecture Standards (`backend/`)

Apply these rules whenever adding or modifying backend endpoints, services, DTOs, or repositories under `backend/src/main/java/ke/retailpos/pos_backend/`.

## Core Rules

1. **Layered Architecture:** Keep `@RestController` classes thin (HTTP routing, actor resolution via `AuditLogService.currentActor()`, status codes). Delegate business rules, transactions (`@Transactional`), fiscal `BigDecimal` (`RoundingMode.HALF_UP`) math, and outbox/audit writes to `@Service` classes and Spring Data JPA `@Repository` interfaces (avoid adding new inline `JdbcTemplate` SQL inside controllers).
2. **DTO Pattern:** Never expose JPA `@Entity` models (such as `Cashier`, `AccountingConfig`, `Sale`, `TaxPayment`) directly in API request or response bodies. Always use dedicated Java `record` Request and Response DTOs so sensitive fields (`pinHash`, `mfaSecret`, `erpnextApiSecret`) can never serialize.
3. **Validation:** Annotate incoming request DTO payloads with `@Valid` on controller parameters and Jakarta Bean Validation (`@NotNull`, `@NotBlank`, `@DecimalMin`, `@Size`) on DTO fields.
4. **Standardized Exception Handling:** Prefer `@RestControllerAdvice` with `@ExceptionHandler` for new/refactored error paths to return standardized JSON error envelopes (`code`, `timestamp`, `message`) without changing existing endpoint response contracts relied on by `till-ui` (`ErrorResponse(message)`).
5. **Preserve Unit-Test Constructor Compatibility:** Existing unit tests (`SaleServiceTest`, `AuthControllerTest`, `StockReceivingControllerTest`, `MonthlyTaxServiceTest`) instantiate controllers and services directly without Spring. Whenever injecting a new `@Autowired` bean into an existing class, keep existing shorter constructors delegating `null` cleanly rather than breaking unit tests.
6. **Audit & Auth Consistency:** Record state-changing security, product, stock, override, and payment actions via `AuditLogService`, and keep role restrictions aligned in `SecurityConfig.java`.

## Verification

Run after backend changes:

```bash
cd backend && ./mvnw -B test -Dtest='!*IntegrationTest,!*ApplicationTests'
```
