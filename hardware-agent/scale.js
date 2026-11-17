// Serial Scale Driver (RS-232 / USB-to-Serial with automatic emulator fallback)
const SCALE_PORT = process.env.SCALE_PORT || '';
const SCALE_BAUD = Number(process.env.SCALE_BAUD || 9600);

class ScaleDriver {
  constructor() {
    this.connected = false;
    this.portPath = SCALE_PORT;
    this.simulatedWeight = 1.250; // 1.250 kg default emulator reading
    this.latestSerialWeight = null;
    this.initSerialIfConfigured();
  }

  initSerialIfConfigured() {
    if (!this.portPath) return;
    try {
      // Optional native serialport dependency when running on physical till hardware
      const { SerialPort } = require('serialport');
      const port = new SerialPort({ path: this.portPath, baudRate: SCALE_BAUD });
      let buffer = '';
      port.on('open', () => {
        this.connected = true;
        console.log(`Serial scale connected on ${this.portPath} @ ${SCALE_BAUD}`);
      });
      port.on('data', (chunk) => {
        buffer += chunk.toString('utf8');
        const lines = buffer.split(/\r?\n/);
        buffer = lines.pop() || '';
        for (const line of lines) {
          const match = line.match(/([0-9]+(?:\.[0-9]+)?)/);
          if (match) {
            this.latestSerialWeight = parseFloat(match[1]);
          }
        }
      });
      port.on('error', (err) => {
        this.connected = false;
        console.warn(`Serial scale error (${this.portPath}), falling back to emulator:`, err.message);
      });
    } catch {
      console.log(`SCALE_PORT=${this.portPath} set, but 'serialport' module not installed; using emulator.`);
      this.connected = false;
    }
  }

  readWeight() {
    const activeWeight =
      this.connected && this.latestSerialWeight !== null
        ? this.latestSerialWeight
        : this.simulatedWeight;
    return {
      weightKg: Number(Number(activeWeight).toFixed(3)),
      stable: true,
      unit: 'kg',
      timestamp: new Date().toISOString(),
      source: this.connected ? 'serial-scale' : 'scale-emulator',
      port: this.portPath || null,
    };
  }

  setSimulatedWeight(kg) {
    this.simulatedWeight = Number(kg);
  }
}

module.exports = { ScaleDriver };
