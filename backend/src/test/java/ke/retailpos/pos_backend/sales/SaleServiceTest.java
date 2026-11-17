package ke.retailpos.pos_backend.sales;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import ke.retailpos.pos_backend.auth.Cashier;
import ke.retailpos.pos_backend.auth.CashierRepository;
import ke.retailpos.pos_backend.auth.SupervisorOverrideToken;
import ke.retailpos.pos_backend.auth.SupervisorOverrideTokenRepository;
import ke.retailpos.pos_backend.customer.Customer;
import ke.retailpos.pos_backend.customer.CustomerRepository;
import ke.retailpos.pos_backend.pricing.PricingService;
import ke.retailpos.pos_backend.product.Product;
import ke.retailpos.pos_backend.product.ProductRepository;
import ke.retailpos.pos_backend.providers.payment.FakePaymentProvider;
import ke.retailpos.pos_backend.providers.payment.PaymentProvider;
import ke.retailpos.pos_backend.shift.Shift;
import ke.retailpos.pos_backend.shift.ShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SaleServiceTest {

  private SaleRepository sales;
  private SaleLineRepository saleLines;
  private PaymentRepository payments;
  private OutboxRepository outboxRepository;
  private ProductRepository products;
  private CashierRepository cashiers;
  private ShiftRepository shifts;
  private CustomerRepository customers;
  private PricingService pricingService;
  private FakePaymentProvider paymentProvider;
  private ObjectMapper objectMapper;

  private SaleService saleService;

  private UUID cashierId;
  private UUID tillId;
  private UUID productId;
  private Product product;
  private Cashier cashier;
  private Shift shift;

  @BeforeEach
  void setUp() {
    sales = mock(SaleRepository.class);
    saleLines = mock(SaleLineRepository.class);
    payments = mock(PaymentRepository.class);
    outboxRepository = mock(OutboxRepository.class);
    products = mock(ProductRepository.class);
    cashiers = mock(CashierRepository.class);
    shifts = mock(ShiftRepository.class);
    customers = mock(CustomerRepository.class);
    pricingService = new PricingService();
    paymentProvider = new FakePaymentProvider();
    objectMapper = new ObjectMapper();

    saleService = new SaleService(
        sales, saleLines, payments, outboxRepository,
        products, cashiers, shifts, customers,
        pricingService, paymentProvider, objectMapper
    );

    cashierId = UUID.randomUUID();
    tillId = UUID.randomUUID();
    productId = UUID.randomUUID();

    cashier = new Cashier(cashierId, "test_cashier", "Test Cashier", "hash", "CASHIER", true);
    shift = new Shift(UUID.randomUUID(), tillId, cashier, Instant.now(), new BigDecimal("1000.00"), "OPEN");
    product = new Product(productId, "Brookside Whole Milk 500ml", new BigDecimal("65.00"), "A", true, new BigDecimal("50.000"), new BigDecimal("10.000"));

    when(cashiers.findById(cashierId)).thenReturn(Optional.of(cashier));
    when(shifts.findFirstByTillIdAndStatusOrderByOpenedAtDesc(tillId, "OPEN")).thenReturn(Optional.of(shift));
    when(products.findById(productId)).thenReturn(Optional.of(product));
  }

  @Test
  void testSuccessfulCashSaleReducesStockAndCreatesOutbox() {
    CreateSaleRequest req = new CreateSaleRequest(
        UUID.randomUUID(),
        tillId,
        cashierId,
        List.of(new CreateSaleRequest.SaleItemRequest(productId, new BigDecimal("2"), new BigDecimal("65.00"))),
        new CreateSaleRequest.PaymentRequest("CASH", new BigDecimal("200.00"))
    );

    ReceiptDto receipt = saleService.processSale(req);

    assertNotNull(receipt);
    assertEquals(new BigDecimal("130.00"), receipt.totalKes());
    assertEquals(new BigDecimal("200.00"), receipt.amountTenderedKes());
    assertEquals(new BigDecimal("70.00"), receipt.changeKes());
    assertEquals("CASH", receipt.paymentMethod());

    // Verify stock decreased by 2 (50 - 2 = 48)
    assertEquals(new BigDecimal("48.000"), product.getStockQuantity());
    verify(products).save(product);

    // Verify sale, lines, payment, and 2 outbox rows saved
    verify(sales).save(any(Sale.class));
    verify(saleLines).save(any(SaleLine.class));
    verify(payments).save(any(Payment.class));
    verify(outboxRepository, times(2)).save(any(Outbox.class));
  }

  @Test
  void testSuccessfulMpesaPayment() {
    CreateSaleRequest req = new CreateSaleRequest(
        UUID.randomUUID(),
        tillId,
        cashierId,
        List.of(new CreateSaleRequest.SaleItemRequest(productId, new BigDecimal("1"), new BigDecimal("65.00"))),
        new CreateSaleRequest.PaymentRequest("MPESA", new BigDecimal("65.00"), "0712345678", null)
    );

    ReceiptDto receipt = saleService.processSale(req);

    assertNotNull(receipt);
    assertEquals("MPESA", receipt.paymentMethod());
    assertNotNull(receipt.paymentReference());
    assertTrue(receipt.paymentReference().startsWith("WS"));
    assertEquals(0, receipt.changeKes().compareTo(BigDecimal.ZERO));
  }

  @Test
  void testMpesaWithoutPhoneFails() {
    CreateSaleRequest req = new CreateSaleRequest(
        UUID.randomUUID(),
        tillId,
        cashierId,
        List.of(new CreateSaleRequest.SaleItemRequest(productId, new BigDecimal("1"), new BigDecimal("65.00"))),
        new CreateSaleRequest.PaymentRequest("MPESA", new BigDecimal("65.00"), "", null)
    );

    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> saleService.processSale(req));
    assertTrue(ex.getMessage().contains("Customer phone number is required"));
  }

  @Test
  void testLoyaltyPointsAwardedAndRedeemed() {
    Customer customer = new Customer(UUID.randomUUID(), "0712345678", "David Ochieng", new BigDecimal("50.00"), Instant.now());
    when(customers.findByPhoneNumber("0712345678")).thenReturn(Optional.of(customer));

    // Cart total: 200 KES. Redeem 30 points -> payable = 170 KES
    Product item = new Product(productId, "Rice 2kg", new BigDecimal("200.00"), "B", true);
    when(products.findById(productId)).thenReturn(Optional.of(item));

    CreateSaleRequest req = new CreateSaleRequest(
        UUID.randomUUID(),
        tillId,
        cashierId,
        List.of(new CreateSaleRequest.SaleItemRequest(productId, new BigDecimal("1"), new BigDecimal("200.00"))),
        new CreateSaleRequest.PaymentRequest("CASH", new BigDecimal("170.00"), "0712345678", new BigDecimal("30.00"))
    );

    ReceiptDto receipt = saleService.processSale(req);

    assertNotNull(receipt);
    // Customer spent 200, points balance was 50 - 30 (redeemed) + 2.00 (earned for 200 KES) = 22.00
    assertEquals(new BigDecimal("22.00"), customer.getPointsBalance());
    verify(customers, atLeastOnce()).save(customer);
  }

  @Test
  void testManagerCannotProcessSaleAtTill() {
    UUID managerId = UUID.randomUUID();
    Cashier manager = new Cashier(managerId, "mgr_sarah", "Sarah Auma", "hash", "SUPERVISOR", true);
    when(cashiers.findById(managerId)).thenReturn(Optional.of(manager));

    CreateSaleRequest req = new CreateSaleRequest(
        UUID.randomUUID(),
        tillId,
        managerId,
        List.of(new CreateSaleRequest.SaleItemRequest(productId, new BigDecimal("1"), new BigDecimal("65.00"))),
        new CreateSaleRequest.PaymentRequest("CASH", new BigDecimal("65.00"))
    );

    IllegalStateException ex = assertThrows(IllegalStateException.class, () -> saleService.processSale(req));
    assertTrue(ex.getMessage().contains("Only cashiers are permitted to operate tills"));
  }

  @Test
  void testOfflineClientSaleIdReplayIsIdempotent() {
    UUID clientSaleId = UUID.randomUUID();
    Sale existingSale = new Sale(UUID.randomUUID(), UUID.randomUUID(), tillId, cashierId, new BigDecimal("65.00"), "COMPLETED", Instant.now());
    existingSale.setClientSaleId(clientSaleId);

    SaleLine line = new SaleLine(UUID.randomUUID(), existingSale, product, new BigDecimal("1"), new BigDecimal("65.00"), "A", new BigDecimal("8.97"), new BigDecimal("65.00"), BigDecimal.ZERO);
    Payment payment = new Payment(UUID.randomUUID(), existingSale.getId(), "CASH", new BigDecimal("65.00"), "CASH-OFFLINE", "PAID");

    when(sales.findByClientSaleId(clientSaleId)).thenReturn(Optional.of(existingSale));
    when(saleLines.findBySaleId(existingSale.getId())).thenReturn(List.of(line));
    when(payments.findBySaleId(existingSale.getId())).thenReturn(List.of(payment));

    CreateSaleRequest replayReq = new CreateSaleRequest(
        existingSale.getShopId(),
        tillId,
        cashierId,
        List.of(new CreateSaleRequest.SaleItemRequest(productId, new BigDecimal("1"), new BigDecimal("65.00"))),
        new CreateSaleRequest.PaymentRequest("CASH", new BigDecimal("65.00")),
        clientSaleId
    );

    ReceiptDto receipt = saleService.processSale(replayReq);

    assertEquals(existingSale.getId(), receipt.saleId());
    assertEquals(new BigDecimal("65.00"), receipt.totalKes());
    // Must not double-deduct stock or insert another sale
    verify(sales, never()).save(any(Sale.class));
    verify(products, never()).save(any(Product.class));
  }

  @Test
  void testBankReferenceRecordedOnBankSale() {
    CreateSaleRequest req = new CreateSaleRequest(
        UUID.randomUUID(),
        tillId,
        cashierId,
        List.of(new CreateSaleRequest.SaleItemRequest(productId, new BigDecimal("1"), new BigDecimal("65.00"))),
        new CreateSaleRequest.PaymentRequest("BANK", new BigDecimal("65.00"), null, null, "PDQ-998877")
    );

    ReceiptDto receipt = saleService.processSale(req);

    assertNotNull(receipt);
    assertEquals("BANK", receipt.paymentMethod());
    assertEquals("PDQ:PDQ-998877", receipt.paymentReference());
  }

  @Test
  void testSplitTenderCashAndMpesaSale() {
    CreateSaleRequest req = new CreateSaleRequest(
        UUID.randomUUID(),
        tillId,
        cashierId,
        List.of(new CreateSaleRequest.SaleItemRequest(productId, new BigDecimal("2"), new BigDecimal("65.00"))),
        null,
        null,
        List.of(
            new CreateSaleRequest.PaymentRequest("CASH", new BigDecimal("50.00")),
            new CreateSaleRequest.PaymentRequest("MPESA", new BigDecimal("80.00"), "0712345678", null)
        ),
        null
    );

    ReceiptDto receipt = saleService.processSale(req);

    assertNotNull(receipt);
    assertEquals("SPLIT", receipt.paymentMethod());
    assertEquals(new BigDecimal("130.00"), receipt.totalKes());
    verify(payments, times(2)).save(any(Payment.class));
  }

  @Test
  void testLineDiscountWithSupervisorOverrideReducesTotal() {
    UUID supervisorId = UUID.randomUUID();
    // Product price is 65.00 in catalog; discount 20.00 -> net payable 45.00 (ignores tampered unitPriceKes 100.00)
    UUID tokenId = UUID.randomUUID();
    SupervisorOverrideTokenRepository overrideRepo = mock(SupervisorOverrideTokenRepository.class);
    SupervisorOverrideToken token = new SupervisorOverrideToken(
        tokenId, supervisorId, cashierId, "Approved discount", Instant.now().plusSeconds(300)
    );
    when(overrideRepo.findByIdAndConsumedAtIsNull(tokenId)).thenReturn(Optional.of(token));

    SaleService hardenedService = new SaleService(
        sales, saleLines, payments, outboxRepository,
        products, cashiers, shifts, customers,
        pricingService, paymentProvider, objectMapper, null, null, overrideRepo
    );

    CreateSaleRequest req = new CreateSaleRequest(
        UUID.randomUUID(),
        tillId,
        cashierId,
        List.of(new CreateSaleRequest.SaleItemRequest(
            productId, new BigDecimal("1"), new BigDecimal("65.00"), new BigDecimal("20.00")
        )),
        new CreateSaleRequest.PaymentRequest("CASH", new BigDecimal("45.00")),
        null,
        null,
        supervisorId,
        tokenId
    );

    ReceiptDto receipt = hardenedService.processSale(req);

    assertEquals(new BigDecimal("45.00"), receipt.totalKes());
    assertNotNull(token.getConsumedAt());
    verify(overrideRepo).save(token);
    verify(sales).save(argThat(s ->
        new BigDecimal("20.00").compareTo(s.getDiscountKes()) == 0 && supervisorId.equals(s.getOverrideBy())
    ));
  }

  @Test
  void testDiscountOrPriceTamperWithoutValidOverrideTokenIsRejected() {
    SupervisorOverrideTokenRepository overrideRepo = mock(SupervisorOverrideTokenRepository.class);
    SaleService hardenedService = new SaleService(
        sales, saleLines, payments, outboxRepository,
        products, cashiers, shifts, customers,
        pricingService, paymentProvider, objectMapper, null, null, overrideRepo
    );

    // 1. Discount without override token
    CreateSaleRequest unauthDiscount = new CreateSaleRequest(
        UUID.randomUUID(),
        tillId,
        cashierId,
        List.of(new CreateSaleRequest.SaleItemRequest(
            productId, new BigDecimal("1"), new BigDecimal("65.00"), new BigDecimal("15.00")
        )),
        new CreateSaleRequest.PaymentRequest("CASH", new BigDecimal("50.00"))
    );
    assertThrows(IllegalArgumentException.class, () -> hardenedService.processSale(unauthDiscount));

    // 2. Client attempting to tamper unitPriceKes down to 1.00 KES without override token
    CreateSaleRequest tamperedPrice = new CreateSaleRequest(
        UUID.randomUUID(),
        tillId,
        cashierId,
        List.of(new CreateSaleRequest.SaleItemRequest(
            productId, new BigDecimal("1"), new BigDecimal("1.00")
        )),
        new CreateSaleRequest.PaymentRequest("CASH", new BigDecimal("1.00"))
    );
    assertThrows(IllegalArgumentException.class, () -> hardenedService.processSale(tamperedPrice));
  }
}
