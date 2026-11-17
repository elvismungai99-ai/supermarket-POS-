-- Migration V5: Remove all hardcoded users, demo seed accounts, and demo customers

-- 1. Remove return lines and returns linked to hardcoded users
DELETE FROM sale_return_line
WHERE sale_return_id IN (
    SELECT id FROM sale_return
    WHERE cashier_id IN (SELECT id FROM cashier WHERE username IN ('cashier1', 'supervisor1', 'admin1'))
       OR original_sale_id IN (SELECT id FROM sale WHERE cashier_id IN (SELECT id FROM cashier WHERE username IN ('cashier1', 'supervisor1', 'admin1')))
);

DELETE FROM sale_return
WHERE cashier_id IN (SELECT id FROM cashier WHERE username IN ('cashier1', 'supervisor1', 'admin1'))
   OR original_sale_id IN (SELECT id FROM sale WHERE cashier_id IN (SELECT id FROM cashier WHERE username IN ('cashier1', 'supervisor1', 'admin1')));

-- 2. Remove payments and sale lines for sales made by hardcoded users
DELETE FROM payment
WHERE sale_id IN (
    SELECT id FROM sale
    WHERE cashier_id IN (SELECT id FROM cashier WHERE username IN ('cashier1', 'supervisor1', 'admin1'))
);

DELETE FROM sale_line
WHERE sale_id IN (
    SELECT id FROM sale
    WHERE cashier_id IN (SELECT id FROM cashier WHERE username IN ('cashier1', 'supervisor1', 'admin1'))
);

-- 3. Remove sales made by hardcoded users
DELETE FROM sale
WHERE cashier_id IN (
    SELECT id FROM cashier
    WHERE username IN ('cashier1', 'supervisor1', 'admin1')
);

-- 4. Remove shifts opened by hardcoded users
DELETE FROM shift
WHERE cashier_id IN (
    SELECT id FROM cashier
    WHERE username IN ('cashier1', 'supervisor1', 'admin1')
);

-- 5. Delete hardcoded user accounts
DELETE FROM cashier
WHERE username IN ('cashier1', 'supervisor1', 'admin1');

-- 6. Remove demo seeded customers
DELETE FROM customer
WHERE phone_number IN ('0712345678', '0722000000');
