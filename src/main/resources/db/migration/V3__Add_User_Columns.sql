-- Add 'status' column with ENUM type
ALTER TABLE users
ADD COLUMN status VARCHAR(20) DEFAULT 'ACTIVE',
ADD COLUMN first_name VARCHAR(50),
ADD COLUMN last_name VARCHAR(50);

-- Replace the names below as needed
UPDATE users
SET 
    status = 'ACTIVE',
    first_name = CASE user_id
        WHEN 'ADMIN001' THEN 'Alice'
        WHEN 'USER0001' THEN 'Bob'
        WHEN 'USER0002' THEN 'Charlie'
        WHEN 'USER0003' THEN 'Diana'
        WHEN 'USER0004' THEN 'Ethan'
        WHEN 'USER0005' THEN 'Fiona'
        WHEN 'USER0006' THEN 'George'
        WHEN 'USER0007' THEN 'Hannah'
        WHEN 'USER0008' THEN 'Ian'
        WHEN 'USER0009' THEN 'Julia'
        ELSE 'Test'
    END,
    last_name = CASE user_id
        WHEN 'ADMIN001' THEN 'Smith'
        WHEN 'USER0001' THEN 'Johnson'
        WHEN 'USER0002' THEN 'Williams'
        WHEN 'USER0003' THEN 'Brown'
        WHEN 'USER0004' THEN 'Jones'
        WHEN 'USER0005' THEN 'Garcia'
        WHEN 'USER0006' THEN 'Miller'
        WHEN 'USER0007' THEN 'Davis'
        WHEN 'USER0008' THEN 'Martinez'
        WHEN 'USER0009' THEN 'Anderson'
        ELSE 'User'
    END;