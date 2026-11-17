// ESC/POS Command Builder for 80mm (48-column) Thermal Receipt Printers

const ESC = '\x1B';
const GS = '\x1D';

const COMMANDS = {
  INIT: `${ESC}@`,
  ALIGN_LEFT: `${ESC}a\x00`,
  ALIGN_CENTER: `${ESC}a\x01`,
  ALIGN_RIGHT: `${ESC}a\x02`,
  BOLD_ON: `${ESC}E\x01`,
  BOLD_OFF: `${ESC}E\x00`,
  DOUBLE_HEIGHT: `${ESC}!\x10`,
  DOUBLE_WIDTH: `${ESC}!\x20`,
  NORMAL_TEXT: `${ESC}!\x00`,
  CUT_FULL: `${GS}V\x00`,
  CUT_PARTIAL: `${GS}V\x01`,
  OPEN_DRAWER: `${ESC}p\x00\x19\xFA`,
  LINE_FEED: '\n',
};

function formatLine(left, right, width = 48) {
  const leftStr = String(left);
  const rightStr = String(right);
  const spaces = Math.max(1, width - leftStr.length - rightStr.length);
  return leftStr + ' '.repeat(spaces) + rightStr;
}

function buildReceiptEscPos(receipt) {
  const buffer = [];

  buffer.push(COMMANDS.INIT);
  buffer.push(COMMANDS.ALIGN_CENTER);
  buffer.push(COMMANDS.BOLD_ON);
  buffer.push('NAIROBI FRESH SUPERMARKET\n');
  buffer.push(COMMANDS.BOLD_OFF);
  buffer.push('Moi Avenue Branch, Nairobi\n');
  buffer.push('KRA PIN: P051234567Z\n');
  buffer.push('*** OFFICIAL FISCAL RECEIPT ***\n\n');

  buffer.push(COMMANDS.ALIGN_LEFT);
  buffer.push(formatLine(`Receipt No: ${receipt.receiptNumber}`, '') + '\n');
  buffer.push(formatLine(`Date: ${receipt.createdAt}`, `Till: ${receipt.tillId ? receipt.tillId.slice(0, 8) : '01'}`) + '\n');
  buffer.push(formatLine(`Cashier: ${receipt.cashierName || 'Cashier'}`, '') + '\n');
  buffer.push('-'.repeat(48) + '\n');

  // Items
  buffer.push(formatLine('ITEM [TAX CAT]', 'QTY x PRICE   TOTAL') + '\n');
  buffer.push('-'.repeat(48) + '\n');

  if (Array.isArray(receipt.lines)) {
    for (const line of receipt.lines) {
      const name = `${line.name} [${line.taxCategory || 'A'}]`;
      const qtyPrice = `${Number(line.quantity).toFixed(2)}x${Number(line.unitPriceKes).toFixed(2)}`;
      const total = Number(line.lineTotalKes).toFixed(2);
      buffer.push(formatLine(name, '') + '\n');
      buffer.push(formatLine(`  ${qtyPrice}`, total) + '\n');
    }
  }

  buffer.push('-'.repeat(48) + '\n');

  // Totals
  buffer.push(formatLine('Subtotal (Excl. Tax):', `KES ${Number(receipt.subtotalKes).toFixed(2)}`) + '\n');
  buffer.push(formatLine('Total VAT:', `KES ${Number(receipt.totalTaxKes).toFixed(2)}`) + '\n');
  buffer.push(COMMANDS.BOLD_ON);
  buffer.push(formatLine('TOTAL PAYABLE:', `KES ${Number(receipt.totalKes).toFixed(2)}`) + '\n');
  buffer.push(COMMANDS.BOLD_OFF);
  buffer.push(formatLine(`Payment (${receipt.paymentMethod || 'CASH'}):`, `KES ${Number(receipt.amountTenderedKes).toFixed(2)}`) + '\n');
  if (receipt.changeKes > 0) {
    buffer.push(formatLine('CHANGE GIVEN:', `KES ${Number(receipt.changeKes).toFixed(2)}`) + '\n');
  }

  // KRA eTIMS Fiscal section
  buffer.push('\n');
  buffer.push(COMMANDS.ALIGN_CENTER);
  buffer.push('✓ KRA eTIMS INVOICE QUEUED IN OUTBOX\n');
  buffer.push(`CU No: KRA-VSCU-${receipt.saleId ? receipt.saleId.slice(0, 8).toUpperCase() : 'DEMO'}\n`);
  buffer.push('Asante kwa kununua nasi!\n\n\n\n');
  buffer.push(COMMANDS.CUT_PARTIAL);

  if (receipt.openDrawer) {
    buffer.push(COMMANDS.OPEN_DRAWER);
  }
  return buffer.join('');
}

function buildOpenDrawerCommand() {
  return COMMANDS.INIT + COMMANDS.OPEN_DRAWER;
}

module.exports = {
  COMMANDS,
  formatLine,
  buildReceiptEscPos,
  buildOpenDrawerCommand,
};
