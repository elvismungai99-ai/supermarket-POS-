package ke.retailpos.pos_backend.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import ke.retailpos.pos_backend.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductControllerIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM product_barcode");
    jdbcTemplate.update("DELETE FROM sale_line");
    jdbcTemplate.update("DELETE FROM product");
  }

  @Test
  void shouldReturnProductWhenBarcodeExists() throws Exception {
    UUID productId = UUID.randomUUID();
    String barcode = "6161101234567";

    jdbcTemplate.update(
        "INSERT INTO product (id, name, price_kes, tax_category, active) VALUES (?, ?, ?, ?, ?)",
        productId, "Ungwa Maize Flour 2kg", new BigDecimal("180.00"), "A", true
    );
    jdbcTemplate.update(
        "INSERT INTO product_barcode (barcode, product_id) VALUES (?, ?)",
        barcode, productId
    );

    mockMvc.perform(get("/api/products/by-barcode/{barcode}", barcode))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(productId.toString()))
        .andExpect(jsonPath("$.name").value("Ungwa Maize Flour 2kg"))
        .andExpect(jsonPath("$.priceKes").value(180.00));
  }

  @Test
  void shouldReturn404WhenBarcodeNotFound() throws Exception {
    mockMvc.perform(get("/api/products/by-barcode/{barcode}", "non-existent-barcode"))
        .andExpect(status().isNotFound());
  }
}
