package ke.retailpos.pos_backend.returns;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import ke.retailpos.pos_backend.auth.Cashier;
import ke.retailpos.pos_backend.auth.CashierRepository;
import ke.retailpos.pos_backend.product.Product;
import ke.retailpos.pos_backend.product.ProductRepository;
import ke.retailpos.pos_backend.sales.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ReturnServiceTest {

  private SaleReturnRepository returnRepository;
  private SaleReturnLineRepository returnLineRepository;
  private SaleRepository saleRepository;
  private SaleLineRepository saleLineRepository;
  private ProductRepository productRepository;
  private CashierRepository cashierRepository;
  private OutboxRepository outboxRepository;
  private ObjectMapper objectMapper;

  private ReturnService returnService;

  @BeforeEach
  void setUp() {
    returnRepository = mock(SaleReturnRepository.class);
    returnLineRepository = mock(SaleReturnLineRepository.class);
    saleRepository = mock(SaleRepository.class);
    saleLineRepository = mock(SaleLineRepository.class);
    productRepository = mock(ProductRepository.class);
    cashierRepository = mock(CashierRepository.class);
    outboxRepository = mock(OutboxRepository.class);
    objectMapper = new ObjectMapper();

    returnService = new ReturnService(
        returnRepository,
        returnLineRepository,
        saleRepository,
        saleLineRepository,
        productRepository,
        cashierRepository,
        outboxRepository,
        objectMapper
    );
  }

  @Test
  void testProcessReturnRestocksProductAndQueuesCreditNote() {
    UUID saleId = UUID.randomUUID();
    UUID cashierId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();

    Sale sale = new Sale(saleId, UUID.randomUUID(), UUID.randomUUID(), cashierId, new BigDecimal("130.00"), "COMPLETED", Instant.now());
    Cashier cashier = new Cashier(cashierId, "test_manager", "Test Manager", "hash", "SUPERVISOR", true);
    Product product = new Product(productId, "Brookside Whole Milk 500ml", new BigDecimal("65.00"), "A", true, new BigDecimal("10.000"), new BigDecimal("5.000"));
    SaleLine line = new SaleLine(UUID.randomUUID(), sale, product, new BigDecimal("2"), new BigDecimal("65.00"), "A", new BigDecimal("17.93"), new BigDecimal("130.00"));

    when(saleRepository.findById(saleId)).thenReturn(Optional.of(sale));
    when(cashierRepository.findById(cashierId)).thenReturn(Optional.of(cashier));
    when(saleLineRepository.findBySaleId(saleId)).thenReturn(List.of(line));

    ReturnService.ProcessReturnRequest req = new ReturnService.ProcessReturnRequest(
        saleId,
        "REC-20261112-001",
        cashierId,
        "Damaged carton",
        List.of(new ReturnService.ProcessReturnRequest.ReturnItemRequest(productId, new BigDecimal("1")))
    );

    ReturnService.ReturnResponseDto resp = returnService.processReturn(req);

    assertNotNull(resp);
    assertEquals(new BigDecimal("65.00"), resp.totalRefundKes());
    assertEquals("test_manager", cashier.getUsername());

    // Verify stock returned (10 + 1 = 11)
    assertEquals(new BigDecimal("11.000"), product.getStockQuantity());
    verify(productRepository).save(product);

    // Verify return and outbox saved
    verify(returnRepository).save(any(SaleReturn.class));
    verify(outboxRepository, times(2)).save(any(Outbox.class));
  }
}
