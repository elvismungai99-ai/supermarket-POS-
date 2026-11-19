const http = require('http');
const net = require('net');
const fs = require('fs');
const { buildReceiptEscPos, buildOpenDrawerCommand } = require('./escpos');
const { ScaleDriver } = require('./scale');

const HOST = process.env.HARDWARE_HOST || '127.0.0.1';
const PORT = process.env.HARDWARE_PORT || 9001;
const PRINTER_HOST = process.env.PRINTER_HOST || '';
const PRINTER_PORT = Number(process.env.PRINTER_PORT || 9100);
const PRINTER_DEVICE = process.env.PRINTER_DEVICE || '';
const HARDWARE_TOKEN = process.env.HARDWARE_TOKEN || '';
const IS_PRODUCTION = process.env.NODE_ENV === 'production';
const ALLOW_SCALE_SIMULATE = process.env.ALLOW_SCALE_SIMULATE
  ? process.env.ALLOW_SCALE_SIMULATE === 'true'
  : !IS_PRODUCTION;
const MAX_BODY_BYTES = 64 * 1024; // 64 KB cap against local memory exhaustion
const ALLOWED_ORIGINS = new Set(
  (process.env.ALLOWED_ORIGINS || 'http://localhost:5173,http://127.0.0.1:5173,http://localhost:8080,http://localhost')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
);

if (IS_PRODUCTION && !HARDWARE_TOKEN) {
  console.error('FATAL: HARDWARE_TOKEN must be set when NODE_ENV=production');
  process.exit(1);
}

function readJsonBody(req, res, onParsed) {
  let body = '';
  let byteLen = 0;
  let aborted = false;
  req.on('data', (chunk) => {
    if (aborted) return;
    byteLen += Buffer.byteLength(chunk);
    if (byteLen > MAX_BODY_BYTES) {
      aborted = true;
      sendJson(req, res, 413, { error: 'Payload too large (max 64 KB)' });
      req.destroy();
      return;
    }
    body += chunk;
  });
  req.on('end', () => {
    if (aborted) return;
    try {
      onParsed(JSON.parse(body || '{}'));
    } catch {
      sendJson(req, res, 400, { error: 'Invalid JSON payload' });
    }
  });
}

const scale = new ScaleDriver();

function sendCorsHeaders(req, res) {
  const origin = req && req.headers ? req.headers.origin : '';
  if (origin && ALLOWED_ORIGINS.has(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Vary', 'Origin');
  }
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, X-Hardware-Token');
}

function sendJson(req, res, status, data) {
  sendCorsHeaders(req, res);
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(data));
}

function dispatchToPrinter(rawEscPos) {
  return new Promise((resolve) => {
    const payload = Buffer.from(rawEscPos, 'binary');
    if (PRINTER_HOST) {
      const socket = new net.Socket();
      socket.setTimeout(3000);
      socket.connect(PRINTER_PORT, PRINTER_HOST, () => {
        socket.write(payload, () => {
          socket.destroy();
          resolve({ mode: 'network-tcp', target: `${PRINTER_HOST}:${PRINTER_PORT}` });
        });
      });
      socket.on('error', (err) => {
        console.warn(`TCP printer unreachable (${PRINTER_HOST}:${PRINTER_PORT}):`, err.message);
        resolve({ mode: 'console-fallback', error: err.message });
      });
      socket.on('timeout', () => {
        socket.destroy();
        resolve({ mode: 'console-fallback', error: 'TCP printer timeout' });
      });
      return;
    }

    if (PRINTER_DEVICE) {
      try {
        fs.writeFileSync(PRINTER_DEVICE, payload);
        resolve({ mode: 'local-device', target: PRINTER_DEVICE });
        return;
      } catch (err) {
        console.warn(`Device printer error (${PRINTER_DEVICE}):`, err.message);
        resolve({ mode: 'console-fallback', error: err.message });
        return;
      }
    }

    resolve({ mode: 'console-preview', target: 'stdout' });
  });
}

const server = http.createServer((req, res) => {
  sendCorsHeaders(req, res);
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const url = new URL(req.url, `http://${HOST}:${PORT}`);

  if (req.method === 'GET' && url.pathname === '/health') {
    return sendJson(req, res, 200, {
      status: 'UP',
      service: 'pos-hardware-agent',
      port: PORT,
      printerMode: PRINTER_HOST ? `tcp://${PRINTER_HOST}:${PRINTER_PORT}` : PRINTER_DEVICE || 'console-preview',
      scaleSource: scale.connected ? 'serial-scale' : 'scale-emulator',
      timestamp: new Date().toISOString(),
    });
  }

  if (HARDWARE_TOKEN && req.headers['x-hardware-token'] !== HARDWARE_TOKEN) {
    return sendJson(req, res, 401, { error: 'Invalid or missing X-Hardware-Token' });
  }

  if (req.method === 'GET' && url.pathname === '/scale/weight') {
    return sendJson(req, res, 200, scale.readWeight());
  }

  if (req.method === 'POST' && url.pathname === '/scale/simulate') {
    if (!ALLOW_SCALE_SIMULATE) {
      return sendJson(req, res, 403, { error: 'Scale simulation is disabled in production' });
    }
    readJsonBody(req, res, (data) => {
      scale.setSimulatedWeight(data.weightKg);
      sendJson(req, res, 200, { ok: true, weight: scale.readWeight() });
    });
    return;
  }

  if (req.method === 'POST' && url.pathname === '/drawer/open') {
    dispatchToPrinter(buildOpenDrawerCommand()).then((dispatch) => {
      console.log(`💵 Cash drawer kick dispatched (${dispatch.mode})`);
      sendJson(req, res, 200, { status: 'DRAWER_KICKED', transport: dispatch });
    });
    return;
  }

  if (req.method === 'POST' && url.pathname === '/print') {
    readJsonBody(req, res, async (receipt) => {
      try {
        const escposBytes = buildReceiptEscPos(receipt);
        const dispatch = await dispatchToPrinter(escposBytes);

        console.log(`\n============== THERMAL ESC/POS PRINT (${dispatch.mode}) ==============`);
        console.log(escposBytes);
        console.log('====================================================================\n');

        sendJson(req, res, 200, {
          status: 'PRINTED',
          bytesLength: escposBytes.length,
          receiptNumber: receipt.receiptNumber,
          transport: dispatch,
          preview: escposBytes.replace(/[\x00-\x1F]/g, ''),
        });
      } catch (e) {
        sendJson(req, res, 400, { error: 'Failed to build ESC/POS receipt: ' + e.message });
      }
    });
    return;
  }

  sendJson(req, res, 404, { error: 'Not Found' });
});

server.listen(PORT, HOST, () => {
  console.log(`POS Local Hardware Agent listening on http://${HOST}:${PORT}`);
  console.log(`- ESC/POS Thermal Printer: POST http://${HOST}:${PORT}/print`);
  console.log(`- Cash Drawer Kick:        POST http://${HOST}:${PORT}/drawer/open`);
  console.log(`- Weighing Scale Stream:   GET  http://${HOST}:${PORT}/scale/weight`);
});
