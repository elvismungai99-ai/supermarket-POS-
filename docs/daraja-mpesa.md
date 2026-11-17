# Safaricom Daraja M-Pesa STK Push Integration

## 1. Overview
The Point of Sale (POS) system enables frictionless mobile money settlement via Safaricom Lipa na M-Pesa Online (STK Push).

Instead of requiring customers to manually enter Paybill/Till numbers and account numbers into their SIM toolkit, the cashier inputs the customer's mobile number (`07XXXXXXXX` or `2547XXXXXXXX`). An instant STK push prompt is triggered directly to the customer's phone requesting their M-Pesa PIN.

---

## 2. Protocol & Endpoints
- **Sandbox Base URL:** `https://sandbox.safaricom.co.ke`
- **Production Base URL:** `https://api.safaricom.co.ke`
- **Auth Endpoint:** `/oauth/v1/generate?grant_type=client_credentials`
- **STK Push Request:** `/mpesa/stkpush/v1/processrequest`
- **Query Transaction Status:** `/mpesa/stkpushquery/v1/query`

### STK Push Payload
```json
{
  "BusinessShortCode": "174379",
  "Password": "<Base64(BusinessShortCode + Passkey + Timestamp)>",
  "Timestamp": "20261112120000",
  "TransactionType": "CustomerPayBillOnline",
  "Amount": 130,
  "PartyA": "254712345678",
  "PartyB": "174379",
  "PhoneNumber": "254712345678",
  "CallBackURL": "https://retailpos.ke/api/payments/mpesa-callback",
  "AccountReference": "TILL-01",
  "TransactionDesc": "Supermarket Purchase"
}
```

---

## 3. Simulation & Development Provider (`FakePaymentProvider`)
For local development, integration testing, and offline trials, the system includes `FakePaymentProvider`:
- `ALWAYS_SUCCESS`: Returns instant simulated M-Pesa transaction reference (e.g. `WS8F2A109C`).
- `ALWAYS_FAIL`: Simulates customer cancellation, invalid PIN, or insufficient funds.
- `DELAYED`: Simulates pending status waiting for customer prompt interaction.
- `DUPLICATE`: Detects duplicate callbacks and guarantees idempotency.
