package ke.retailpos.pos_backend.returns;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import ke.retailpos.pos_backend.auth.Cashier;
import ke.retailpos.pos_backend.auth.CashierRepository;
import ke.retailpos.pos_backend.product.Product;
import ke.retailpos.pos_backend.product.ProductRepository;
import ke.retailpos.pos_backend.sales.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class ReturnService {

  private final SaleReturnRepository returnRepository;
  private final SaleReturnLineRepository returnLineRepository;
  private final SaleRepository saleRepository;
  private final SaleLineRepository saleLineRepository;
  private final ProductRepository productRepository;
  private final CashierRepository cashierRepository;
  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  public ReturnService(
      SaleReturnRepository returnRepository,
      SaleReturnLineRepository returnLineRepository,
      SaleRepository saleRepository,
      SaleLineRepository saleLineRepository,
      ProductRepository productRepository,
      CashierRepository cashierRepository,
      OutboxRepository outboxRepository,
      ObjectMapper objectMapper
  ) {
    this.returnRepository = returnRepository;
    this.returnLineRepository = returnLineRepository;
    this.saleRepository = saleRepository;
    this.saleLineRepository = saleLineRepository;
    this.productRepository = productRepository;
    this.cashierRepository = cashierRepository;
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
  }

  public record ProcessReturnRequest(
      UUID saleId,
      String receiptNumber,
      UUID cashierId,
      String reason,
      List<ReturnItemRequest> items,
      String refundMethod
  ) {
    public ProcessReturnRequest(UUID saleId, String receiptNumber, UUID cashierId,
        String reason, List<ReturnItemRequest> items) {
      this(saleId, receiptNumber, cashierId, reason, items, "CASH");
    }

    public record ReturnItemRequest(
        UUID productId,
        BigDecimal quantity
    ) {}
  }

  public record ReturnResponseDto(
      UUID returnId,
      UUID originalSaleId,
      String receiptNumber,
      UUID cashierId,
      String cashierName,
      BigDecimal totalRefundKes,
      String reason,
      Instant createdAt,
      List<ReturnLineDto> lines
  ) {
    public record ReturnLineDto(
        UUID productId,
        String productName,
        BigDecimal quantity,
        BigDecimal unitPriceKes,
        BigDecimal refundTotalKes
    ) {}
  }

  @Transactional
  public ReturnResponseDto processReturn(ProcessReturnRequest req) {
    Objects.requireNonNull(req, "request must not be null");
    if (req.items() == null || req.items().isEmpty()) {
      throw new IllegalArgumentException("Return must contain at least one item");
    }

    Sale sale = null;
    if (req.saleId() != null) {
      sale = saleRepository.findById(req.saleId())
          .orElseThrow(() -> new IllegalArgumentException("Original sale not found for id: " + req.saleId()));
    } else {
      throw new IllegalArgumentException("Original sale id is required");
    }

    Cashier cashier = cashierRepository.findById(req.cashierId())
        .orElseThrow(() -> new IllegalArgumentException("Cashier not found: " + req.cashierId()));

    List<SaleLine> originalLines = saleLineRepository.findBySaleId(sale.getId());
    Map<UUID, SaleLine> originalLinesByProduct = new HashMap<>();
    for (SaleLine line : originalLines) {
      originalLinesByProduct.put(line.getProduct().getId(), line);
    }

    UUID returnId = UUID.randomUUID();
    Instant now = Instant.now();
    BigDecimal totalRefund = BigDecimal.ZERO;
    List<ReturnResponseDto.ReturnLineDto> lineDtos = new ArrayList<>();
    List<SaleReturnLine> linesToSave = new ArrayList<>();

    SaleReturn saleReturn = new SaleReturn(
        returnId,
        sale,
        req.receiptNumber() != null ? req.receiptNumber() : "RET-" + sale.getId().toString().substring(0, 8),
        cashier,
        BigDecimal.ZERO,
        req.reason() != null ? req.reason() : "Customer Return",
        now
    );
    saleReturn.setRefundMethod(req.refundMethod());

    for (ProcessReturnRequest.ReturnItemRequest itemReq : req.items()) {
      SaleLine origLine = originalLinesByProduct.get(itemReq.productId());
      if (origLine == null) {
        throw new IllegalArgumentException("Product " + itemReq.productId() + " was not part of original sale");
      }

      Product product = origLine.getProduct();
      BigDecimal unitPrice = origLine.getUnitPriceKes();
      BigDecimal lineRefund = unitPrice.multiply(itemReq.quantity()).setScale(2, java.math.RoundingMode.HALF_UP);
      totalRefund = totalRefund.add(lineRefund);

      // Restock inventory
      product.setStockQuantity(product.getStockQuantity().add(itemReq.quantity()));
      productRepository.save(product);

      SaleReturnLine returnLine = new SaleReturnLine(
          UUID.randomUUID(),
          saleReturn,
          product,
          itemReq.quantity(),
          unitPrice,
          lineRefund
      );
      linesToSave.add(returnLine);

      lineDtos.add(new ReturnResponseDto.ReturnLineDto(
          product.getId(),
          product.getName(),
          itemReq.quantity(),
          unitPrice,
          lineRefund
      ));
    }

    saleReturn.setRefundAmountKes(totalRefund);
    returnRepository.save(saleReturn);
    returnLineRepository.saveAll(linesToSave);

    // Queue Outbox events for KRA eTIMS credit note & sales rollback
    try {
      Map<String, Object> returnPayload = new HashMap<>();
      returnPayload.put("returnId", returnId.toString());
      returnPayload.put("originalSaleId", sale.getId().toString());
      returnPayload.put("receiptNumber", saleReturn.getReceiptNumber());
      returnPayload.put("cashierId", cashier.getId().toString());
      returnPayload.put("refundAmountKes", totalRefund.doubleValue());
      returnPayload.put("reason", saleReturn.getReason());
      returnPayload.put("createdAt", now.toString());

      outboxRepository.save(new Outbox(
          UUID.randomUUID(),
          "sales.returned",
          objectMapper.writeValueAsString(returnPayload),
          now,
          null
      ));

      // KRA eTIMS credit note
      Map<String, Object> creditNotePayload = new HashMap<>();
      creditNotePayload.put("returnId", returnId.toString());
      creditNotePayload.put("originalReceiptNumber", saleReturn.getReceiptNumber());
      creditNotePayload.put("refundAmount", totalRefund.doubleValue());
      creditNotePayload.put("creditNoteQueuedAt", now.toString());

      outboxRepository.save(new Outbox(
          UUID.randomUUID(),
          "etims.credit_note.queued",
          objectMapper.writeValueAsString(creditNotePayload),
          now,
          null
      ));
    } catch (JacksonException e) {
      throw new IllegalStateException("Failed to queue return outbox event", e);
    }

    return new ReturnResponseDto(
        returnId,
        sale.getId(),
        saleReturn.getReceiptNumber(),
        cashier.getId(),
        cashier.getName(),
        totalRefund,
        saleReturn.getReason(),
        now,
        lineDtos
    );
  }

  public List<ReturnResponseDto> getAllReturns() {
    return returnRepository.findAllWithDetails().stream().map(ret -> {
      List<SaleReturnLine> lines = returnLineRepository.findBySaleReturnId(ret.getId());
      List<ReturnResponseDto.ReturnLineDto> lineDtos = lines.stream().map(l ->
          new ReturnResponseDto.ReturnLineDto(
              l.getProduct().getId(),
              l.getProduct().getName(),
              l.getQuantity(),
              l.getUnitPriceKes(),
              l.getRefundTotalKes()
          )
      ).toList();

      return new ReturnResponseDto(
          ret.getId(),
          ret.getOriginalSale().getId(),
          ret.getReceiptNumber(),
          ret.getCashier().getId(),
          ret.getCashier().getName(),
          ret.getRefundAmountKes(),
          ret.getReason(),
          ret.getCreatedAt(),
          lineDtos
      );
    }).toList();
  }
}
