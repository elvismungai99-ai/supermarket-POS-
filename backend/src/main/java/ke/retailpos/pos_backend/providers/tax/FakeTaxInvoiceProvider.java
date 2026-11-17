package ke.retailpos.pos_backend.providers.tax;

import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FakeTaxInvoiceProvider implements TaxInvoiceProvider {

  public enum Mode {
    ALWAYS_SUCCESS,
    ALWAYS_FAIL
  }

  private volatile Mode mode = Mode.ALWAYS_SUCCESS;

  public void setMode(Mode mode) {
    this.mode = mode;
  }

  @Override
  public TaxInvoiceResponse submitInvoice(TaxInvoiceRequest request) {
    if (mode == Mode.ALWAYS_FAIL) {
      return new TaxInvoiceResponse(
          false,
          null,
          null,
          null,
          "KRA eTIMS Gateway connection refused (SIMULATED_RETRY_NEEDED)"
      );
    }

    String cuNumber = "KRA-VSCU-" + String.format("%08d", Math.abs(request.saleId().hashCode() % 100000000));
    String qrCode = "https://etims.kra.go.ke/verify?cu=" + cuNumber + "&rc=" + request.receiptNumber();
    String internalData = "KRA" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();

    return new TaxInvoiceResponse(
        true,
        cuNumber,
        internalData,
        qrCode,
        null
    );
  }
}
