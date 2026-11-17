package ke.retailpos.pos_backend.product;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

class ProductControllerTest {

  private ProductRepository productRepository;
  private JdbcTemplate jdbcTemplate;
  private ProductController productController;

  @BeforeEach
  void setUp() {
    productRepository = mock(ProductRepository.class);
    jdbcTemplate = mock(JdbcTemplate.class);
    productController = new ProductController(productRepository, jdbcTemplate);
  }

  @Test
  void testSearchProductsByName() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    Product p1 = new Product(id1, "Brookside Whole Milk 500ml", new BigDecimal("65.00"), "A", true, new BigDecimal("40.000"), new BigDecimal("10.000"));
    Product p2 = new Product(id2, "KCC Fresh Milk 500ml", new BigDecimal("60.00"), "A", true, new BigDecimal("25.000"), new BigDecimal("10.000"));

    when(productRepository.searchByName("milk")).thenReturn(List.of(p1, p2));
    when(productRepository.findBarcodeByProductId(id1)).thenReturn(Optional.of("6161100001234"));
    when(productRepository.findBarcodeByProductId(id2)).thenReturn(Optional.of("6161100009999"));

    List<ProductDto> results = productController.searchProducts("milk");

    assertEquals(2, results.size());
    assertEquals("Brookside Whole Milk 500ml", results.get(0).name());
    assertEquals("6161100001234", results.get(0).barcode());
    assertEquals("KCC Fresh Milk 500ml", results.get(1).name());
  }

  @Test
  void testSearchProductsBlankReturnsEmptyList() {
    List<ProductDto> results = productController.searchProducts("   ");
    assertTrue(results.isEmpty());
    verifyNoInteractions(productRepository);
  }

  @Test
  void testByBarcodeFound() {
    UUID id = UUID.randomUUID();
    Product p = new Product(id, "White Bread 400g", new BigDecimal("65.00"), "B", true, new BigDecimal("15.000"), new BigDecimal("5.000"));
    when(productRepository.findByBarcode("6161100003456")).thenReturn(Optional.of(p));

    ResponseEntity<ProductDto> resp = productController.byBarcode("6161100003456");

    assertEquals(HttpStatus.OK, resp.getStatusCode());
    assertNotNull(resp.getBody());
    assertEquals("White Bread 400g", resp.getBody().name());
    assertEquals(new BigDecimal("65.00"), resp.getBody().priceKes());
  }
}
