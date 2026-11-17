package ke.retailpos.pos_backend.providers.payment;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DarajaPaymentProviderTest {

  @Test
  void testNormalizeKenyanMsisdnFormats() {
    assertEquals("254712345678", DarajaPaymentProvider.normalizeKenyanMsisdn("0712345678"));
    assertEquals("254712345678", DarajaPaymentProvider.normalizeKenyanMsisdn("+254 712 345 678"));
    assertEquals("254112345678", DarajaPaymentProvider.normalizeKenyanMsisdn("112345678"));
    assertEquals("254712345678", DarajaPaymentProvider.normalizeKenyanMsisdn("254712345678"));

    assertThrows(IllegalArgumentException.class, () -> DarajaPaymentProvider.normalizeKenyanMsisdn("12345"));
    assertThrows(IllegalArgumentException.class, () -> DarajaPaymentProvider.normalizeKenyanMsisdn(null));
  }

  @Test
  void testCashAndBankHandledLocallyWithoutNetworkCall() {
    DarajaProperties props = new DarajaProperties();
    DarajaPaymentProvider provider = new DarajaPaymentProvider(props, new ObjectMapper());

    PaymentProvider.PaymentResult cashRes = provider.initiatePayment(
        new PaymentProvider.PaymentRequest(UUID.randomUUID(), PaymentProvider.PaymentType.CASH, new BigDecimal("500.00"), null, "POS-1")
    );
    assertEquals(PaymentProvider.PaymentStatus.SUCCESS, cashRes.status());
    assertTrue(cashRes.transactionReference().startsWith("CASH-"));

    PaymentProvider.PaymentResult bankRes = provider.initiatePayment(
        new PaymentProvider.PaymentRequest(UUID.randomUUID(), PaymentProvider.PaymentType.BANK, new BigDecimal("1500.00"), null, "PDQ-AUTH-77")
    );
    assertEquals(PaymentProvider.PaymentStatus.SUCCESS, bankRes.status());
    assertEquals("PDQ-AUTH-77", bankRes.transactionReference());
  }
}
