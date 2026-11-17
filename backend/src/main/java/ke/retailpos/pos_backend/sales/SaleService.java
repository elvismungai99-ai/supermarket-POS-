package ke.retailpos.pos_backend.sales;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import ke.retailpos.pos_backend.audit.AuditLogService;
import ke.retailpos.pos_backend.auth.Cashier;
import ke.retailpos.pos_backend.auth.CashierRepository;
import ke.retailpos.pos_backend.auth.SupervisorOverrideToken;
import ke.retailpos.pos_backend.auth.SupervisorOverrideTokenRepository;
import ke.retailpos.pos_backend.customer.Customer;
import ke.retailpos.pos_backend.customer.CustomerRepository;
import ke.retailpos.pos_backend.inventory.ProductBatch;
import ke.retailpos.pos_backend.inventory.ProductBatchRepository;
import ke.retailpos.pos_backend.pricing.PricingService;
import ke.retailpos.pos_backend.product.Product;
import ke.retailpos.pos_backend.product.ProductRepository;
import ke.retailpos.pos_backend.providers.payment.PaymentProvider;
import ke.retailpos.pos_backend.shift.ShiftRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class SaleService {

  private final SaleRepository sales;
  private final SaleLineRepository saleLines;
  private final PaymentRepository payments;
  private final OutboxRepository outboxRepository;
  private final ProductRepository products;
  private final CashierRepository cashiers;
  private final ShiftRepository shifts;
  private final CustomerRepository customers;
  private final PricingService pricingService;
  private final PaymentProvider paymentProvider;
  private final ObjectMapper objectMapper;
  private final ProductBatchRepository batchRepository;
  private final AuditLogService auditLogService;
  private final SupervisorOverrideTokenRepository overrideTokenRepository;

  private static final DateTimeFormatter RECEIPT_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
      .withZone(ZoneId.systemDefault());

  public SaleService(
      SaleRepository sales,
      SaleLineRepository saleLines,
      PaymentRepository payments,
      OutboxRepository outboxRepository,
      ProductRepository products,
      CashierRepository cashiers,
      ShiftRepository shifts,
      CustomerRepository customers,
      PricingService pricingService,
      PaymentProvider paymentProvider,
      ObjectMapper objectMapper
  ) {
    this(
        sales, saleLines, payments, outboxRepository, products, cashiers,
        shifts, customers, pricingService, paymentProvider, objectMapper, null, null, null
    );
  }

  public SaleService(
      SaleRepository sales,
      SaleLineRepository saleLines,
      PaymentRepository payments,
      OutboxRepository outboxRepository,
      ProductRepository products,
      CashierRepository cashiers,
      ShiftRepository shifts,
      CustomerRepository customers,
      PricingService pricingService,
      PaymentProvider paymentProvider,
      ObjectMapper objectMapper,
      ProductBatchRepository batchRepository,
      AuditLogService auditLogService
  ) {
    this(
        sales, saleLines, payments, outboxRepository, products, cashiers,
        shifts, customers, pricingService, paymentProvider, objectMapper,
        batchRepository, auditLogService, null
    );
  }

  @Autowired
  public SaleService(
      SaleRepository sales,
      SaleLineRepository saleLines,
      PaymentRepository payments,
      OutboxRepository outboxRepository,
      ProductRepository products,
      CashierRepository cashiers,
      ShiftRepository shifts,
      CustomerRepository customers,
      PricingService pricingService,
      PaymentProvider paymentProvider,
      ObjectMapper objectMapper,
      ProductBatchRepository batchRepository,
      AuditLogService auditLogService,
      SupervisorOverrideTokenRepository overrideTokenRepository
  ) {
    this.sales = sales;
    this.saleLines = saleLines;
    this.payments = payments;
    this.outboxRepository = outboxRepository;
    this.products = products;
    this.cashiers = cashiers;
    this.shifts = shifts;
    this.customers = customers;
    this.pricingService = pricingService;
    this.paymentProvider = paymentProvider;
    this.objectMapper = objectMapper;
    this.batchRepository = batchRepository;
    this.auditLogService = auditLogService;
    this.overrideTokenRepository = overrideTokenRepository;
  }

  @Transactional
  public ReceiptDto processCashSale(CreateSaleRequest req) {
    return processSale(req);
  }

  @Transactional
  public ReceiptDto processSale(CreateSaleRequest req) {
    Objects.requireNonNull(req, "request must not be null");
    if (req.items() == null || req.items().isEmpty()) {
      throw new IllegalArgumentException("Cart cannot be empty for a sale");
    }
    List<CreateSaleRequest.PaymentRequest> requestedPayments =
        (req.payments() != null && !req.payments().isEmpty())
            ? req.payments()
            : (req.payment() != null ? List.of(req.payment()) : List.of());

    if (requestedPayments.isEmpty() || requestedPayments.get(0).method() == null) {
      throw new IllegalArgumentException("Payment method is required");
    }

    // Offline / retry idempotency: if clientSaleId was already processed, return the existing receipt
    if (req.clientSaleId() != null) {
      var existingSaleOpt = sales.findByClientSaleId(req.clientSaleId());
      if (existingSaleOpt.isPresent()) {
        return buildReceiptFromExistingSale(existingSaleOpt.get());
      }
    }

    CreateSaleRequest.PaymentRequest primaryPayReq = requestedPayments.get(0);
    String paymentMethod = requestedPayments.size() > 1
        ? "SPLIT"
        : primaryPayReq.method().toUpperCase();

    // Resolve Cashier
    Cashier cashier = cashiers.findById(req.cashierId())
        .orElseThrow(() -> new IllegalArgumentException("Cashier not found: " + req.cashierId()));

    if (!"CASHIER".equalsIgnoreCase(cashier.getRole())) {
      throw new IllegalStateException("Only cashiers are permitted to operate tills and complete sales. Managers and Admins have oversight roles.");
    }

    // Verify Shift
    shifts.findFirstByTillIdAndStatusOrderByOpenedAtDesc(req.tillId(), "OPEN")
        .orElseThrow(() -> new IllegalStateException("Till " + req.tillId() + " does not have an active open shift"));

    // Resolve products and build pricing request (strict server catalog pricing + verified discount overrides)
    List<PricingService.LinePricingRequest> pricingRequests = new ArrayList<>();
    Map<UUID, Product> productMap = new HashMap<>();
    List<BigDecimal> lineDiscounts = new ArrayList<>();
    BigDecimal totalSaleDiscount = BigDecimal.ZERO;
    boolean needsOverrideAuth = false;

    for (CreateSaleRequest.SaleItemRequest itemReq : req.items()) {
      if (itemReq.quantity() == null || itemReq.quantity().compareTo(BigDecimal.ZERO) <= 0) {
        throw new IllegalArgumentException("Item quantity must be greater than zero");
      }
      Product product = products.findById(itemReq.productId())
          .orElseThrow(() -> new IllegalArgumentException("Product not found: " + itemReq.productId()));
      productMap.put(product.getId(), product);

      BigDecimal catalogPrice = product.getPriceKes();
      BigDecimal disc = itemReq.discountKes() != null ? itemReq.discountKes() : BigDecimal.ZERO;
      if (disc.compareTo(BigDecimal.ZERO) < 0) {
        throw new IllegalArgumentException("Discount cannot be negative");
      }
      if (disc.compareTo(BigDecimal.ZERO) > 0) {
        needsOverrideAuth = true;
      }
      if (itemReq.unitPriceKes() != null && itemReq.unitPriceKes().compareTo(catalogPrice) != 0) {
        // Ignore untrusted arbitrary price edits unless an authorized supervisor override token is supplied
        needsOverrideAuth = true;
      }

      BigDecimal effectiveUnitPrice = catalogPrice;
      if (disc.compareTo(BigDecimal.ZERO) > 0) {
        BigDecimal baseGross = catalogPrice.multiply(itemReq.quantity());
        if (disc.compareTo(baseGross) > 0) {
          throw new IllegalArgumentException("Discount cannot exceed line total for " + product.getName());
        }
        BigDecimal discountedGross = baseGross.subtract(disc).max(BigDecimal.ZERO);
        effectiveUnitPrice = discountedGross.divide(itemReq.quantity(), 2, RoundingMode.HALF_UP);
        totalSaleDiscount = totalSaleDiscount.add(disc);
      }
      lineDiscounts.add(disc);

      pricingRequests.add(new PricingService.LinePricingRequest(
          product.getId(),
          product.getName(),
          itemReq.quantity(),
          effectiveUnitPrice,
          product.getTaxCategory()
      ));
    }

    UUID verifiedOverrideBy = req.overrideBy();
    if (needsOverrideAuth) {
      if (overrideTokenRepository != null) {
        if (req.overrideToken() == null) {
          throw new IllegalArgumentException("Line discount or price override requires a valid Manager/Super Admin authorization token.");
        }
        SupervisorOverrideToken overrideToken = overrideTokenRepository
            .findByIdAndConsumedAtIsNull(req.overrideToken())
            .orElseThrow(() -> new IllegalArgumentException("Supervisor override token is invalid or has already been used."));
        if (overrideToken.getExpiresAt().isBefore(Instant.now())) {
          throw new IllegalArgumentException("Supervisor override token has expired. Request fresh manager authorization.");
        }
        verifiedOverrideBy = overrideToken.getSupervisorId();
        overrideToken.setConsumedAt(Instant.now());
        overrideTokenRepository.save(overrideToken);
      } else if (req.overrideBy() == null && req.overrideToken() == null) {
        throw new IllegalArgumentException("Line discount or price override requires manager authorization.");
      }
    }

    // Calculate cart pricing and tax
    PricingService.CartPricingSummary cartSummary = pricingService.calculateCart(pricingRequests);
    BigDecimal payableTotal = cartSummary.totalKes();

    // Loyalty points deduction (check across requested payment entries)
    Customer customer = null;
    BigDecimal requestedPointsToRedeem = BigDecimal.ZERO;
    for (CreateSaleRequest.PaymentRequest pr : requestedPayments) {
      if (customer == null && pr.customerPhone() != null && !pr.customerPhone().isBlank()) {
        customer = customers.findByPhoneNumber(pr.customerPhone().trim()).orElse(null);
      }
      if (pr.pointsToRedeem() != null && pr.pointsToRedeem().compareTo(BigDecimal.ZERO) > 0) {
        requestedPointsToRedeem = requestedPointsToRedeem.add(pr.pointsToRedeem());
      }
    }

    BigDecimal pointsRedeemed = BigDecimal.ZERO;
    if (customer != null && requestedPointsToRedeem.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal availablePoints = customer.getPointsBalance();
      pointsRedeemed = requestedPointsToRedeem.min(availablePoints).min(payableTotal);
      payableTotal = payableTotal.subtract(pointsRedeemed).setScale(2, RoundingMode.HALF_UP);
      customer.setPointsBalance(customer.getPointsBalance().subtract(pointsRedeemed));
    }

    UUID saleId = UUID.randomUUID();
    Instant now = Instant.now();
    String receiptNumber = "REC-" + RECEIPT_TS.format(now) + "-" + saleId.toString().substring(0, 8).toUpperCase();

    BigDecimal amountTendered = BigDecimal.ZERO;
    BigDecimal change = BigDecimal.ZERO;
    String paymentReference = "";
    List<Payment> paymentsToSave = new ArrayList<>();

    if (requestedPayments.size() == 1) {
      CreateSaleRequest.PaymentRequest single = requestedPayments.get(0);
      String singleMethod = single.method().toUpperCase();
      String singleState = "PAID";

      if ("CASH".equals(singleMethod)) {
        if (single.amountTenderedKes() == null) {
          throw new IllegalArgumentException("Cash payment details with amount tendered are required");
        }
        PricingService.CashPaymentResult paymentCalc = pricingService.calculateCashPayment(
            payableTotal,
            single.amountTenderedKes()
        );
        amountTendered = paymentCalc.amountTenderedKes();
        change = paymentCalc.changeKes();
        paymentReference = "TENDERED:" + amountTendered.toPlainString() + ";CHANGE:" + change.toPlainString();
        if (pointsRedeemed.compareTo(BigDecimal.ZERO) > 0) {
          paymentReference += ";POINTS_REDEEMED:" + pointsRedeemed.toPlainString();
        }
      } else if ("MPESA".equals(singleMethod)) {
        String phone = single.customerPhone();
        if (phone == null || phone.isBlank()) {
          throw new IllegalArgumentException("Customer phone number is required for M-Pesa payment");
        }
        PaymentProvider.PaymentResult result = paymentProvider.initiatePayment(new PaymentProvider.PaymentRequest(
            saleId,
            PaymentProvider.PaymentType.MPESA,
            payableTotal,
            phone,
            receiptNumber
        ));
        if (result.status() == PaymentProvider.PaymentStatus.FAILED) {
          throw new IllegalArgumentException("M-Pesa transaction failed: " + result.message());
        }
        if (result.status() == PaymentProvider.PaymentStatus.PENDING) {
          singleState = "PENDING_STK";
        }
        amountTendered = payableTotal;
        change = BigDecimal.ZERO;
        paymentReference = result.transactionReference();
      } else if ("BANK".equals(singleMethod)) {
        String customRef = (single.bankReference() != null && !single.bankReference().isBlank())
            ? single.bankReference().trim()
            : receiptNumber;
        PaymentProvider.PaymentResult result = paymentProvider.initiatePayment(new PaymentProvider.PaymentRequest(
            saleId,
            PaymentProvider.PaymentType.BANK,
            payableTotal,
            single.customerPhone(),
            customRef
        ));
        amountTendered = payableTotal;
        change = BigDecimal.ZERO;
        paymentReference = (single.bankReference() != null && !single.bankReference().isBlank())
            ? "PDQ:" + single.bankReference().trim()
            : result.transactionReference();
      } else {
        throw new IllegalArgumentException("Unsupported payment method: " + singleMethod);
      }

      paymentsToSave.add(new Payment(
          UUID.randomUUID(), saleId, singleMethod, payableTotal, paymentReference, singleState
      ));
    } else {
      // Split-tender settlement across multiple payment legs
      BigDecimal remaining = payableTotal;
      List<String> refs = new ArrayList<>();
      for (CreateSaleRequest.PaymentRequest leg : requestedPayments) {
        String legMethod = leg.method().toUpperCase();
        BigDecimal legTendered = leg.amountTenderedKes() != null ? leg.amountTenderedKes() : BigDecimal.ZERO;
        if (legTendered.compareTo(BigDecimal.ZERO) <= 0) continue;

        BigDecimal legApplied = legTendered.min(remaining);
        String legRef;
        String legState = "PAID";

        if ("CASH".equals(legMethod)) {
          BigDecimal legChange = legTendered.subtract(legApplied).max(BigDecimal.ZERO);
          change = change.add(legChange);
          legRef = "SPLIT-CASH:" + legApplied.toPlainString();
        } else if ("MPESA".equals(legMethod)) {
          String phone = leg.customerPhone();
          if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Customer phone number is required for M-Pesa split leg");
          }
          PaymentProvider.PaymentResult result = paymentProvider.initiatePayment(new PaymentProvider.PaymentRequest(
              saleId, PaymentProvider.PaymentType.MPESA, legApplied, phone, receiptNumber
          ));
          if (result.status() == PaymentProvider.PaymentStatus.FAILED) {
            throw new IllegalArgumentException("M-Pesa split leg failed: " + result.message());
          }
          if (result.status() == PaymentProvider.PaymentStatus.PENDING) {
            legState = "PENDING_STK";
          }
          legRef = result.transactionReference();
        } else if ("BANK".equals(legMethod)) {
          String customRef = (leg.bankReference() != null && !leg.bankReference().isBlank())
              ? leg.bankReference().trim()
              : receiptNumber;
          legRef = "PDQ:" + customRef;
        } else {
          throw new IllegalArgumentException("Unsupported split payment method: " + legMethod);
        }

        amountTendered = amountTendered.add(legTendered);
        remaining = remaining.subtract(legApplied);
        refs.add(legMethod + ":" + legApplied.toPlainString());
        paymentsToSave.add(new Payment(
            UUID.randomUUID(), saleId, legMethod, legApplied, legRef, legState
        ));
      }

      if (remaining.compareTo(new BigDecimal("0.01")) > 0) {
        throw new IllegalArgumentException("Split tender payments are short by KES " + remaining.toPlainString());
      }
      paymentReference = String.join(" | ", refs);
    }

    // Deduct stock inventory + FEFO product batches
    for (PricingService.LinePricingResult line : cartSummary.lines()) {
      Product prod = productMap.get(line.productId());
      if (prod != null) {
        BigDecimal updatedStock = prod.getStockQuantity().subtract(line.quantity());
        prod.setStockQuantity(updatedStock);
        products.save(prod);
        deductBatchesFefo(prod.getId(), line.quantity());
      }
    }

    // Award loyalty points: 1 token per 100 KES spent
    BigDecimal pointsEarned = BigDecimal.ZERO;
    if (customer != null) {
      pointsEarned = cartSummary.totalKes().divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
      customer.setPointsBalance(customer.getPointsBalance().add(pointsEarned));
      customers.save(customer);
    }

    // 1. Save Sale
    Sale sale = new Sale(
        saleId,
        req.shopId() != null ? req.shopId() : UUID.fromString("00000000-0000-0000-0000-000000000001"),
        req.tillId(),
        cashier.getId(),
        cartSummary.totalKes(),
        "COMPLETED",
        now
    );
    sale.setClientSaleId(req.clientSaleId());
    sale.setLoyaltyRedeemedKes(pointsRedeemed);
    sale.setDiscountKes(totalSaleDiscount);
    sale.setOverrideBy(verifiedOverrideBy);
    sales.save(sale);

    // 2. Save Sale Lines
    List<ReceiptDto.ReceiptLineDto> receiptLines = new ArrayList<>();
    for (int idx = 0; idx < cartSummary.lines().size(); idx++) {
      PricingService.LinePricingResult line = cartSummary.lines().get(idx);
      Product prod = productMap.get(line.productId());
      BigDecimal unitCost = prod != null && prod.getCostPriceKes() != null ? prod.getCostPriceKes() : BigDecimal.ZERO;
      BigDecimal lineCost = unitCost.multiply(line.quantity()).setScale(2, java.math.RoundingMode.HALF_UP);
      SaleLine saleLine = new SaleLine(
          UUID.randomUUID(),
          sale,
          prod,
          line.quantity(),
          line.unitPriceKes(),
          line.taxCategory(),
          line.taxAmountKes(),
          line.lineTotalKes(),
          lineCost
      );
      saleLine.setDiscountKes(lineDiscounts.get(idx));
      saleLines.save(saleLine);

      receiptLines.add(new ReceiptDto.ReceiptLineDto(
          line.productId(),
          line.name(),
          line.quantity(),
          line.unitPriceKes(),
          line.lineTotalKes(),
          line.taxCategory(),
          line.taxRate(),
          line.taxAmountKes()
      ));
    }

    // 3. Save Payments
    for (Payment p : paymentsToSave) {
      payments.save(p);
    }

    if (auditLogService != null && req.overrideBy() != null) {
      auditLogService.record(
          "SALE_WITH_SUPERVISOR_OVERRIDE",
          "sale",
          saleId.toString(),
          "Completed sale " + receiptNumber + " with overrideBy=" + req.overrideBy() + ", discountKes=" + totalSaleDiscount
      );
    }

    // 4. Save Outbox Rows (Transactionally queued)
    try {
      // Outbox Row 1: sales.completed
      Map<String, Object> salePayload = new HashMap<>();
      salePayload.put("saleId", saleId.toString());
      salePayload.put("receiptNumber", receiptNumber);
      salePayload.put("shopId", sale.getShopId().toString());
      salePayload.put("tillId", sale.getTillId().toString());
      salePayload.put("cashierId", cashier.getId().toString());
      salePayload.put("totalKes", cartSummary.totalKes().doubleValue());
      salePayload.put("subtotalKes", cartSummary.subtotalKes().doubleValue());
      salePayload.put("totalTaxKes", cartSummary.totalTaxKes().doubleValue());
      salePayload.put("createdAt", now.toString());
      salePayload.put("paymentMethod", paymentMethod);
      salePayload.put("paymentReference", paymentReference);
      salePayload.put("itemCount", cartSummary.lines().size());

      Outbox saleOutbox = new Outbox(
          UUID.randomUUID(),
          "sales.completed",
          objectMapper.writeValueAsString(salePayload),
          now,
          null
      );
      outboxRepository.save(saleOutbox);

      // Outbox Row 2: etims.invoice.queued (KRA offline-first tax compliance outbox)
      Map<String, Object> etimsPayload = new HashMap<>();
      etimsPayload.put("saleId", saleId.toString());
      etimsPayload.put("receiptNumber", receiptNumber);
      etimsPayload.put("totalAmount", cartSummary.totalKes().doubleValue());
      etimsPayload.put("taxableAmount", cartSummary.subtotalKes().doubleValue());
      etimsPayload.put("taxAmount", cartSummary.totalTaxKes().doubleValue());
      etimsPayload.put("taxBreakdowns", cartSummary.taxBreakdowns());
      etimsPayload.put("offlineQueuedAt", now.toString());
      etimsPayload.put("status", "PENDING_SYNC");

      Outbox etimsOutbox = new Outbox(
          UUID.randomUUID(),
          "etims.invoice.queued",
          objectMapper.writeValueAsString(etimsPayload),
          now,
          null
      );
      outboxRepository.save(etimsOutbox);
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to serialize outbox event payload", e);
    }

    // Tax breakdowns for receipt
    List<ReceiptDto.TaxBreakdownDto> receiptBreakdowns = cartSummary.taxBreakdowns().stream()
        .map(b -> new ReceiptDto.TaxBreakdownDto(b.category(), b.rate(), b.taxableKes(), b.taxKes(), b.grossKes()))
        .toList();

    return new ReceiptDto(
        saleId,
        receiptNumber,
        sale.getShopId(),
        sale.getTillId(),
        cashier.getId(),
        cashier.getName(),
        now,
        receiptLines,
        cartSummary.subtotalKes(),
        cartSummary.totalTaxKes(),
        cartSummary.totalKes(),
        receiptBreakdowns,
        amountTendered,
        change,
        paymentMethod,
        "QUEUED_IN_OUTBOX",
        paymentReference,
        pointsEarned,
        customer != null ? customer.getPointsBalance() : null
    );
  }

  private ReceiptDto buildReceiptFromExistingSale(Sale sale) {
    Cashier cashier = cashiers.findById(sale.getCashierId()).orElse(null);
    List<SaleLine> lines = saleLines.findBySaleId(sale.getId());
    List<Payment> salePayments = payments.findBySaleId(sale.getId());
    Payment payment = salePayments.isEmpty() ? null : salePayments.get(0);

    List<PricingService.LinePricingRequest> pricingReqs = lines.stream()
        .map(l -> new PricingService.LinePricingRequest(
            l.getProduct().getId(),
            l.getProduct().getName(),
            l.getQuantity(),
            l.getUnitPriceKes(),
            l.getTaxCategory()
        ))
        .toList();
    PricingService.CartPricingSummary summary = pricingService.calculateCart(pricingReqs);

    List<ReceiptDto.ReceiptLineDto> receiptLines = summary.lines().stream()
        .map(l -> new ReceiptDto.ReceiptLineDto(
            l.productId(), l.name(), l.quantity(), l.unitPriceKes(),
            l.lineTotalKes(), l.taxCategory(), l.taxRate(), l.taxAmountKes()
        ))
        .toList();
    List<ReceiptDto.TaxBreakdownDto> breakdowns = summary.taxBreakdowns().stream()
        .map(b -> new ReceiptDto.TaxBreakdownDto(b.category(), b.rate(), b.taxableKes(), b.taxKes(), b.grossKes()))
        .toList();

    String receiptNo = "REC-" + RECEIPT_TS.format(sale.getCreatedAt()) + "-" + sale.getId().toString().substring(0, 8).toUpperCase();
    BigDecimal tendered = payment != null ? payment.getAmountKes() : sale.getTotalKes();

    return new ReceiptDto(
        sale.getId(),
        receiptNo,
        sale.getShopId(),
        sale.getTillId(),
        sale.getCashierId(),
        cashier != null ? cashier.getName() : "Cashier",
        sale.getCreatedAt(),
        receiptLines,
        summary.subtotalKes(),
        summary.totalTaxKes(),
        sale.getTotalKes(),
        breakdowns,
        tendered,
        BigDecimal.ZERO,
        payment != null ? payment.getMethod() : "CASH",
        "QUEUED_IN_OUTBOX",
        payment != null ? payment.getReference() : null,
        BigDecimal.ZERO,
        null
    );
  }

  private void deductBatchesFefo(UUID productId, BigDecimal qtyToDeduct) {
    if (batchRepository == null || qtyToDeduct == null || qtyToDeduct.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }
    BigDecimal remaining = qtyToDeduct;
    List<ProductBatch> batches = batchRepository.findAvailableBatchesFefo(productId);
    for (ProductBatch batch : batches) {
      if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
      BigDecimal available = batch.getQuantityRemaining();
      BigDecimal take = available.min(remaining);
      batch.setQuantityRemaining(available.subtract(take));
      batchRepository.save(batch);
      remaining = remaining.subtract(take);
    }
  }
}
