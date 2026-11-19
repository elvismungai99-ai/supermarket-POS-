package ke.retailpos.pos_backend.accounting;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ErpNextClientTest {

  @Test
  void testNormalizeBaseUrlHandlesCloudAndLocalUrls() {
    assertEquals("https://mystore.frappe.cloud",
        ErpNextClient.normalizeBaseUrl("https://mystore.frappe.cloud/app/home"));
    assertEquals("https://mystore.frappe.cloud",
        ErpNextClient.normalizeBaseUrl("https://mystore.frappe.cloud/app/accounting/"));
    assertEquals("https://mystore.erpnext.com",
        ErpNextClient.normalizeBaseUrl("mystore.erpnext.com/"));
    assertEquals("https://erp.myshop.co.ke",
        ErpNextClient.normalizeBaseUrl("  https://erp.myshop.co.ke/desk#List/Account  "));
    assertEquals("http://localhost:8000",
        ErpNextClient.normalizeBaseUrl("localhost:8000/"));
    assertEquals("http://127.0.0.1:8000",
        ErpNextClient.normalizeBaseUrl("http://127.0.0.1:8000/api/method/ping?x=1"));
    assertEquals("", ErpNextClient.normalizeBaseUrl("   "));
    assertEquals("", ErpNextClient.normalizeBaseUrl(null));
  }

  @Test
  void testExtractErpNextErrorParsesServerMessages() {
    ErpNextClient client = new ErpNextClient(new ObjectMapper());
    String body = """
        {"exception":"frappe.exceptions.LinkValidationError: Could not find Supplier: brookside dairy",
         "_server_messages":"[\\"{\\\\\\"message\\\\\\":\\\\\\"Could not find Supplier: brookside dairy\\\\\\"}\\"]"}
        """;
    String msg = client.extractErpNextError(417, body);
    assertEquals("ERPNext HTTP 417: Could not find Supplier: brookside dairy", msg);
  }
}
