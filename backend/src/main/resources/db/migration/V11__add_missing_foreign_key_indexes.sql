-- Migration V11: Index all remaining foreign key columns across core, batch, return, shift, and tax tables

create index if not exists idx_product_barcode_product_id on product_barcode (product_id);
create index if not exists idx_sale_line_sale_id on sale_line (sale_id);
create index if not exists idx_sale_line_product_id on sale_line (product_id);
create index if not exists idx_payment_sale_id on payment (sale_id);
create index if not exists idx_shift_cashier_id on shift (cashier_id);
create index if not exists idx_sale_return_original_sale_id on sale_return (original_sale_id);
create index if not exists idx_sale_return_cashier_id on sale_return (cashier_id);
create index if not exists idx_sale_return_line_sale_return_id on sale_return_line (sale_return_id);
create index if not exists idx_sale_return_line_product_id on sale_return_line (product_id);
create index if not exists idx_stock_receipt_product_id on stock_receipt (product_id);
create index if not exists idx_stock_receipt_received_by on stock_receipt (received_by);
create index if not exists idx_till_shop_id on till (shop_id);
create index if not exists idx_product_batch_stock_receipt_id on product_batch (stock_receipt_id);
create index if not exists idx_sale_override_by on sale (override_by);
create index if not exists idx_supervisor_override_token_supervisor_id on supervisor_override_token (supervisor_id);
create index if not exists idx_tax_payment_recorded_by on tax_payment (recorded_by);
