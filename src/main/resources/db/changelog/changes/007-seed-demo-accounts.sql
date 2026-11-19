--liquibase formatted sql

--changeset ctin:007-seed-demo-accounts splitStatements:false
--comment: Seed tai khoan demo nhan vien va admin BTC

INSERT INTO employees (employee_code, email, full_name, password, role, approval_status, is_email_verified, unit_id, created_at, updated_at)
VALUES (
    'C30050',
    'anh2t@ctin.vn',
    'Trần Tuấn Anh',
    '$2a$10$W5VeKO/NO/d.KRITurIi1OY90sif0J/hAqvYpqYabKkxt/h9l9I4K',
    'USER',
    'ACCEPTED',
    TRUE,
    (SELECT id FROM units WHERE code = 'TT_TK' LIMIT 1),
    NOW(),
    NOW()
)
ON CONFLICT (email) DO UPDATE 
SET password = EXCLUDED.password,
    approval_status = 'ACCEPTED',
    is_email_verified = TRUE;

INSERT INTO employees (employee_code, email, full_name, password, role, approval_status, is_email_verified, unit_id, created_at, updated_at)
VALUES (
    'ADMIN001',
    'admin@ctin.vn',
    'Quản Trị Viên BTC',
    '$2a$10$W5VeKO/NO/d.KRITurIi1OY90sif0J/hAqvYpqYabKkxt/h9l9I4K',
    'ADMIN',
    'ACCEPTED',
    TRUE,
    (SELECT id FROM units WHERE code = 'PB_BTC' LIMIT 1),
    NOW(),
    NOW()
)
ON CONFLICT (email) DO UPDATE 
SET password = EXCLUDED.password,
    role = 'ADMIN',
    approval_status = 'ACCEPTED',
    is_email_verified = TRUE;

--rollback DELETE FROM employees WHERE email IN ('anh2t@ctin.vn', 'admin@ctin.vn');
