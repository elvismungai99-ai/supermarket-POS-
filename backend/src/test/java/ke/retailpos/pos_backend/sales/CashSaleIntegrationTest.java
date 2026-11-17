package ke.retailpos.pos_backend.sales;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import ke.retailpos.pos_backend.TestcontainersConfiguration;
import ke.retailpos.pos_backend.auth.Cashier;
import ke.retailpos.pos_backend.auth.CashierRepository;
import ke.retailpos.pos_backend.product.Product;
import ke.retailpos.pos_backend.product.ProductRepository;
import ke.retailpos.pos_backend.shift.ShiftController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CashSaleIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ProductRepository productRepository;

  @Autowired
  private CashierRepository cashierRepository;

  @Autowired
  private SaleRepository saleRepository;

  @Autowired
  private SaleLineRepository saleLineRepository;

  @Autowired
  private PaymentRepository paymentRepository;

  @Autowired
  private OutboxRepository outboxRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  private UUID tillId;
  private UUID cashierId;
  private String testCashierName;
  private Product milk;
  private Product flour;

  @BeforeEach
  void setUp() throws Exception {
    jdbcTemplate.update("DELETE FROM outbox");
    jdbcTemplate.update("DELETE FROM payment");
    jdbcTemplate.update("DELETE FROM sale_line");
    jdbcTemplate.update("DELETE FROM sale");
    jdbcTemplate.update("DELETE FROM shift");

    tillId = UUID.randomUUID();

    // Create dynamic test cashier
    testCashierName = "Test Cashier " + UUID.randomUUID().toString().substring(0, 8);
    Cashier cashier = cashierRepository.save(new Cashier(
        UUID.randomUUID(),
        "cashier_" + UUID.randomUUID().toString().substring(0, 8),
        testCashierName,
        "$2a$10$2/G4ntG3vJPCxWLbCdj4L.oD30Zykz1pa9vby1V2tFrwPPWiISY5a",
        "CASHIER",
        true,
        "ACTIVE",
        java.time.Instant.now()
    ));
    cashierId = cashier.getId();

    // Ensure products exist
    milk = productRepository.findById(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
        .orElseGet(() -> productRepository.save(new Product(
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
            "Brookside Whole Milk 500ml",
            new BigDecimal("65.00"),
            "A",
            true
        )));

    flour = productRepository.findById(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
        .orElseGet(() -> productRepository.save(new Product(
            UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"),
            "Jogoo Maize Meal 2kg",
            new BigDecimal("170.00"),
            "B",
            true
        )));

    // Open Shift on Till
    var openShiftReq = new ShiftController.OpenShiftRequest(tillId, cashierId, new BigDecimal("1000.00"));
    mockMvc.perform(post("/api/shifts/open")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(openShiftReq)))
        .andExpect(status().isCreated());
  }

  @Test
  void testCashSaleSavedInOneTransactionWithOutboxAndReceipt() throws Exception {
    // 2 x Milk @ 65.00 (Cat A, 16% VAT) = 130.00
    // 1 x Maize Flour @ 170.00 (Cat B, 0% VAT) = 170.00
    // Total = KES 300.00
    // Tendered = KES 500.00 -> Change = KES 200.00
    var saleReq = new CreateSaleRequest(
        UUID.randomUUID(),
        tillId,
        cashierId,
        List.of(
            new CreateSaleRequest.SaleItemRequest(milk.getId(), new BigDecimal("2.000"), new BigDecimal("65.00")),
            new CreateSaleRequest.SaleItemRequest(flour.getId(), new BigDecimal("1.000"), new BigDecimal("170.00"))
        ),
        new CreateSaleRequest.PaymentRequest("CASH", new BigDecimal("500.00"))
    );

    var response = mockMvc.perform(post("/api/sales")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(saleReq)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.saleId").exists())
        .andExpect(jsonPath("$.receiptNumber").exists())
        .andExpect(jsonPath("$.cashierName").value(testCashierName))
        .andExpect(jsonPath("$.totalKes").value(300.00))
        .andExpect(jsonPath("$.amountTenderedKes").value(500.00))
        .andExpect(jsonPath("$.changeKes").value(200.00))
        .andExpect(jsonPath("$.paymentMethod").value("CASH"))
        .andExpect(jsonPath("$.lines.length()").value(2))
        .andExpect(jsonPath("$.taxBreakdowns.length()").value(2))
        .andReturn();

    ReceiptDto receipt = objectMapper.readValue(response.getResponse().getContentAsString(), ReceiptDto.class);
    assertNotNull(receipt.saleId());

    // Verify database entities created in the single transaction
    assertTrue(saleRepository.findById(receipt.saleId()).isPresent(), "Sale row must be saved");
    assertEquals(2, saleLineRepository.findBySaleId(receipt.saleId()).size(), "2 sale lines must be saved");

    var payments = paymentRepository.findBySaleId(receipt.saleId());
    assertEquals(1, payments.size(), "Payment row must be saved");
    assertEquals("CASH", payments.get(0).getMethod());
    assertEquals(new BigDecimal("300.00"), payments.get(0).getAmountKes());

    // Verify Outbox rows
    var outboxList = outboxRepository.findAll();
    assertTrue(outboxList.stream().anyMatch(o -> "sales.completed".equals(o.getTopic())),
        "Outbox must contain sales.completed topic");
    assertTrue(outboxList.stream().anyMatch(o -> "etims.invoice.queued".equals(o.getTopic())),
        "Outbox must contain etims.invoice.queued topic");
  }

  @Test
  void testSaleWithoutOpenShiftFails() throws Exception {
    UUID closedTill = UUID.randomUUID();
    var saleReq = new CreateSaleRequest(
        UUID.randomUUID(),
        closedTill,
        cashierId,
        List.of(new CreateSaleRequest.SaleItemRequest(milk.getId(), new BigDecimal("1.000"), new BigDecimal("65.00"))),
        new CreateSaleRequest.PaymentRequest("CASH", new BigDecimal("100.00"))
    );

    mockMvc.perform(post("/api/sales")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(saleReq)))
        .andExpect(status().isConflict());
  }
}
