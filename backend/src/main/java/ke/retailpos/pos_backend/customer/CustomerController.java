package ke.retailpos.pos_backend.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

  private final CustomerRepository customers;

  public CustomerController(CustomerRepository customers) {
    this.customers = customers;
  }

  public record CustomerDto(
      UUID id,
      String phoneNumber,
      String name,
      BigDecimal pointsBalance
  ) {}

  @GetMapping("/by-phone/{phone}")
  public ResponseEntity<CustomerDto> byPhone(@PathVariable String phone) {
    return customers.findByPhoneNumber(phone)
        .map(c -> ResponseEntity.ok(new CustomerDto(c.getId(), c.getPhoneNumber(), c.getName(), c.getPointsBalance())))
        .orElse(ResponseEntity.notFound().build());
  }

  @GetMapping
  public List<CustomerDto> getAll(@RequestParam(defaultValue = "200") int limit) {
    int cap = Math.max(1, Math.min(limit, 500));
    return customers.findAll().stream()
        .limit(cap)
        .map(c -> new CustomerDto(c.getId(), maskPhone(c.getPhoneNumber()), c.getName(), c.getPointsBalance()))
        .toList();
  }

  private static String maskPhone(String phone) {
    if (phone == null || phone.length() < 6) {
      return "****";
    }
    return phone.substring(0, 4) + "***" + phone.substring(phone.length() - 3);
  }

  public record RegisterCustomerRequest(
      @NotBlank String phoneNumber,
      @NotBlank String name
  ) {}

  @PostMapping
  public ResponseEntity<CustomerDto> register(@Valid @RequestBody RegisterCustomerRequest req) {
    return customers.findByPhoneNumber(req.phoneNumber())
        .map(existing -> ResponseEntity.ok(new CustomerDto(
            existing.getId(),
            existing.getPhoneNumber(),
            existing.getName(),
            existing.getPointsBalance()
        )))
        .orElseGet(() -> {
          Customer c = new Customer(
              UUID.randomUUID(),
              req.phoneNumber(),
              req.name(),
              BigDecimal.ZERO,
              Instant.now()
          );
          Customer saved = customers.save(c);
          return ResponseEntity.status(HttpStatus.CREATED).body(new CustomerDto(
              saved.getId(),
              saved.getPhoneNumber(),
              saved.getName(),
              saved.getPointsBalance()
          ));
        });
  }
}
