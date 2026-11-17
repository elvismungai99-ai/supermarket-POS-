package ke.retailpos.pos_backend.reports;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxPaymentRepository extends JpaRepository<TaxPayment, UUID> {
  List<TaxPayment> findAllByOrderByPaymentDateDescCreatedAtDesc();
  List<TaxPayment> findByTaxPeriodOrderByPaymentDateDescCreatedAtDesc(String taxPeriod);
}
