package ke.retailpos.pos_backend.providers.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "pos.mpesa")
public class DarajaProperties {
  private boolean enabled = false;
  private String baseUrl = "https://sandbox.safaricom.co.ke";
  private String consumerKey = "";
  private String consumerSecret = "";
  private String shortCode = "174379";
  private String passkey = "";
  private String callbackUrl = "https://example.com/api/payments/mpesa-callback";
  private String transactionType = "CustomerPayBillOnline";
  private String allowedCallbackIps = "";

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }

  public String getBaseUrl() { return baseUrl; }
  public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

  public String getConsumerKey() { return consumerKey; }
  public void setConsumerKey(String consumerKey) { this.consumerKey = consumerKey; }

  public String getConsumerSecret() { return consumerSecret; }
  public void setConsumerSecret(String consumerSecret) { this.consumerSecret = consumerSecret; }

  public String getShortCode() { return shortCode; }
  public void setShortCode(String shortCode) { this.shortCode = shortCode; }

  public String getPasskey() { return passkey; }
  public void setPasskey(String passkey) { this.passkey = passkey; }

  public String getCallbackUrl() { return callbackUrl; }
  public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }

  public String getTransactionType() { return transactionType; }
  public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

  public String getAllowedCallbackIps() { return allowedCallbackIps; }
  public void setAllowedCallbackIps(String allowedCallbackIps) { this.allowedCallbackIps = allowedCallbackIps; }
}
