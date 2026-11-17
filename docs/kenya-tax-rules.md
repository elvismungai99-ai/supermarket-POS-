# Kenyan Tax Rules & KRA eTIMS Specification

## 1. Overview of VAT Categories in Kenya
Under the Kenya Revenue Authority (KRA) Value Added Tax Act and updated gazetted schedules, retail supermarket inventory is classified into three principal categories:

| Tax Category | Name | VAT Rate | Input Tax Claimable | Examples |
|---|---|---|---|---|
| **Category A** | Standard Rate | 16% | Yes | Confectionery, electronics, detergents, packaged soda, sugar |
| **Category B** | Zero-Rated | 0% | Yes (Merchant can claim back input VAT) | Maize flour, wheat flour, ordinary bread, unflavoured milk, LPG gas |
| **Category C** | Special Rate | 8% | Yes | Select petroleum products / historic rate |
| **Category E** | VAT Exempt | 0% | No (No input tax refund) | Human & veterinary medicaments (e.g. Panadol), raw unprocessed vegetables |

> **Critical Rule:** In Kenyan retail, shelf prices are always **VAT-inclusive**. The POS extracts the taxable component and the VAT amount using the formula:
> $$\text{Taxable Amount} = \text{round}\left(\frac{\text{Gross Total}}{1 + R}, 2\right)$$
> $$\text{VAT Amount} = \text{Gross Total} - \text{Taxable Amount}$$
> This guarantees that the sum of line totals equals the customer's tendered total with zero penny discrepancy.

---

## 2. KRA eTIMS VSCU Integration
Electronic Tax Invoice Management System (eTIMS) requires all retail businesses to transmit an electronic tax invoice for every sale.

### Technical Flow:
1. **At Checkout:** The sale is committed to PostgreSQL along with an `etims.invoice.queued` row inside the same database transaction (`Transactional Outbox Pattern`).
2. **Offline-First Resilience:** If the supermarket network or KRA VSCU service is temporarily offline, the sale completes locally without delaying the cashier or customer.
3. **Outbox Processor:** A background daemon reads pending outbox rows, formats the payload according to KRA VSCU schema, and posts to the KRA endpoint.
4. **KRA Control Unit Receipt:** On confirmation, KRA returns a unique Control Unit Number (`KRA-VSCU-XXXXXXXX`) and QR Code URL.

---

## 3. Credit Notes & Sale Returns
When a customer returns a purchased item:
- Inventory is immediately restocked (`stock_quantity + returned_qty`).
- A `sale_return` entry records the refund and the authorizing cashier.
- An `etims.credit_note.queued` event is recorded in the outbox to adjust the shop's tax liability with KRA.
