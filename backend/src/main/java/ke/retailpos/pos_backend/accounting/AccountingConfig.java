package ke.retailpos.pos_backend.accounting;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounting_config")
public class AccountingConfig {

  @Id
  private UUID id;

  @Column(name = "company_name", nullable = false)
  private String companyName = "Supermarket POS Ltd";

  @Column(name = "erpnext_base_url", nullable = false)
  private String erpnextBaseUrl = "http://localhost:8000";

  @Column(name = "erpnext_api_key")
  private String erpnextApiKey = "";

  @Column(name = "erpnext_api_secret")
  private String erpnextApiSecret = "";

  @Column(name = "auto_post_on_eod", nullable = false)
  private boolean autoPostOnEod = false;

  @Column(name = "submit_on_post", nullable = false)
  private boolean submitOnPost = true;

  @Column(name = "account_cash_on_hand", nullable = false)
  private String accountCashOnHand = "Cash On Hand - SP";

  @Column(name = "account_mpesa_clearing", nullable = false)
  private String accountMpesaClearing = "M-Pesa Till Clearing - SP";

  @Column(name = "account_bank_clearing", nullable = false)
  private String accountBankClearing = "Bank Clearing - SP";

  @Column(name = "account_sales_standard", nullable = false)
  private String accountSalesStandard = "Sales - Standard Rated 16% - SP";

  @Column(name = "account_sales_zero_rated", nullable = false)
  private String accountSalesZeroRated = "Sales - Zero Rated - SP";

  @Column(name = "account_sales_exempt", nullable = false)
  private String accountSalesExempt = "Sales - Exempt - SP";

  @Column(name = "account_output_vat", nullable = false)
  private String accountOutputVat = "Output VAT 16% - SP";

  @Column(name = "account_sales_returns", nullable = false)
  private String accountSalesReturns = "Sales Returns and Refunds - SP";

  @Column(name = "account_cash_over_short", nullable = false)
  private String accountCashOverShort = "Cash Over and Short - SP";

  @Column(name = "account_cogs", nullable = false)
  private String accountCogs = "Cost of Goods Sold - SP";

  @Column(name = "account_inventory", nullable = false)
  private String accountInventory = "Stock in Hand - SP";

  @Column(name = "account_cost_center", nullable = false)
  private String accountCostCenter = "Main - SP";

  @Column(name = "account_sales_special", nullable = false)
  private String accountSalesSpecial = "Sales - Special Rated 8% - SP";

  @Column(name = "account_loyalty_discount", nullable = false)
  private String accountLoyaltyDiscount = "Loyalty Points Redeemed - SP";

  @Column(name = "account_input_vat", nullable = false)
  private String accountInputVat = "Input VAT 16% - SP";

  @Column(name = "account_accounts_payable", nullable = false)
  private String accountAccountsPayable = "Creditors - SP";

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  public AccountingConfig() {}

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }

  public String getCompanyName() { return companyName; }
  public void setCompanyName(String companyName) { this.companyName = companyName; }

  public String getErpnextBaseUrl() { return erpnextBaseUrl; }
  public void setErpnextBaseUrl(String erpnextBaseUrl) { this.erpnextBaseUrl = erpnextBaseUrl; }

  public String getErpnextApiKey() { return erpnextApiKey; }
  public void setErpnextApiKey(String erpnextApiKey) { this.erpnextApiKey = erpnextApiKey; }

  public String getErpnextApiSecret() { return erpnextApiSecret; }
  public void setErpnextApiSecret(String erpnextApiSecret) { this.erpnextApiSecret = erpnextApiSecret; }

  public boolean isAutoPostOnEod() { return autoPostOnEod; }
  public void setAutoPostOnEod(boolean autoPostOnEod) { this.autoPostOnEod = autoPostOnEod; }

  public String getAccountCashOnHand() { return accountCashOnHand; }
  public void setAccountCashOnHand(String accountCashOnHand) { this.accountCashOnHand = accountCashOnHand; }

  public String getAccountMpesaClearing() { return accountMpesaClearing; }
  public void setAccountMpesaClearing(String accountMpesaClearing) { this.accountMpesaClearing = accountMpesaClearing; }

  public String getAccountBankClearing() { return accountBankClearing; }
  public void setAccountBankClearing(String accountBankClearing) { this.accountBankClearing = accountBankClearing; }

  public String getAccountSalesStandard() { return accountSalesStandard; }
  public void setAccountSalesStandard(String accountSalesStandard) { this.accountSalesStandard = accountSalesStandard; }

  public String getAccountSalesZeroRated() { return accountSalesZeroRated; }
  public void setAccountSalesZeroRated(String accountSalesZeroRated) { this.accountSalesZeroRated = accountSalesZeroRated; }

  public String getAccountSalesExempt() { return accountSalesExempt; }
  public void setAccountSalesExempt(String accountSalesExempt) { this.accountSalesExempt = accountSalesExempt; }

  public String getAccountOutputVat() { return accountOutputVat; }
  public void setAccountOutputVat(String accountOutputVat) { this.accountOutputVat = accountOutputVat; }

  public String getAccountSalesReturns() { return accountSalesReturns; }
  public void setAccountSalesReturns(String accountSalesReturns) { this.accountSalesReturns = accountSalesReturns; }

  public String getAccountCashOverShort() { return accountCashOverShort; }
  public void setAccountCashOverShort(String accountCashOverShort) { this.accountCashOverShort = accountCashOverShort; }

  public String getAccountCogs() { return accountCogs; }
  public void setAccountCogs(String accountCogs) { this.accountCogs = accountCogs; }

  public String getAccountInventory() { return accountInventory; }
  public void setAccountInventory(String accountInventory) { this.accountInventory = accountInventory; }

  public String getAccountCostCenter() { return accountCostCenter; }
  public void setAccountCostCenter(String accountCostCenter) { this.accountCostCenter = accountCostCenter; }

  public Instant getUpdatedAt() { return updatedAt; }
  public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

  public boolean isSubmitOnPost() { return submitOnPost; }
  public void setSubmitOnPost(boolean submitOnPost) { this.submitOnPost = submitOnPost; }

  public String getAccountSalesSpecial() { return accountSalesSpecial; }
  public void setAccountSalesSpecial(String accountSalesSpecial) { this.accountSalesSpecial = accountSalesSpecial; }

  public String getAccountLoyaltyDiscount() { return accountLoyaltyDiscount; }
  public void setAccountLoyaltyDiscount(String accountLoyaltyDiscount) { this.accountLoyaltyDiscount = accountLoyaltyDiscount; }

  public String getAccountInputVat() { return accountInputVat; }
  public void setAccountInputVat(String accountInputVat) { this.accountInputVat = accountInputVat; }

  public String getAccountAccountsPayable() { return accountAccountsPayable; }
  public void setAccountAccountsPayable(String accountAccountsPayable) { this.accountAccountsPayable = accountAccountsPayable; }
}
