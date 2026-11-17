# Retail POS Hardware Agent

A lightweight local micro-service designed to bridge web-based till interfaces with physical retail peripherals:
1. **80mm ESC/POS Thermal Receipt Printers**: Generates formatted ESC/POS byte streams with KRA fiscal headers and paper cut commands.
2. **Electronic Weighing Scales**: Continuous serial communication (RS-232 / USB) supporting CAS, Mettler Toledo, and Avery Berkel protocols.

## Running the Hardware Agent
```bash
cd hardware-agent
node index.js
```
The agent starts on `http://localhost:9001`.

## Endpoints
- `GET /health`: Peripheral health diagnostics
- `GET /scale/weight`: Current scale weight reading
- `POST /scale/simulate`: Set simulated tare/weight for testing
- `POST /print`: Stream formatted ESC/POS receipt to thermal printer
