--liquibase formatted sql

--changeset ctin:004-seed-units-ctin splitStatements:false
--comment: Danh sách 12 phòng ban CTIN. Quân số (total_personnel) mặc định = 1 → admin cập nhật số thật ở trang Admin › Phòng ban.

-- 1) Đổi tên các phòng ban cũ tương ứng (giữ nguyên id → nhân sự đã đăng ký tự chuyển sang phòng ban mới)
UPDATE units SET code = 'BAN_KD', name = 'Ban Kinh doanh'
 WHERE code = 'PB_KD' AND NOT EXISTS (SELECT 1 FROM units WHERE code = 'BAN_KD');
UPDATE units SET code = 'BAN_TC', name = 'Ban Tài chính'
 WHERE code = 'PB_KT' AND NOT EXISTS (SELECT 1 FROM units WHERE code = 'BAN_TC');
UPDATE units SET code = 'BAN_NL', name = 'Ban Nhân lực'
 WHERE code = 'PB_HR' AND NOT EXISTS (SELECT 1 FROM units WHERE code = 'BAN_NL');

-- 2) Thêm / cập nhật 12 phòng ban (không ghi đè quân số đã có)
INSERT INTO units (code, name, total_personnel, sort_order, created_at, updated_at) VALUES
    ('BAN_LD',      'Ban Lãnh đạo',                1,  1, NOW(), NOW()),
    ('BAN_KHTH',    'Ban Kế hoạch tổng hợp',       1,  2, NOW(), NOW()),
    ('BAN_KD',      'Ban Kinh doanh',              1,  3, NOW(), NOW()),
    ('BAN_TC',      'Ban Tài chính',               1,  4, NOW(), NOW()),
    ('BAN_NL',      'Ban Nhân lực',                1,  5, NOW(), NOW()),
    ('TT_TK',       'Trung tâm Triển khai',        1,  6, NOW(), NOW()),
    ('TT_TVGP',     'Trung tâm Tư vấn giải pháp',  1,  7, NOW(), NOW()),
    ('BAN_QLDA',    'Ban Quản lý dự án',           1,  8, NOW(), NOW()),
    ('VP_HN',       'Văn phòng Hà Nội',            1,  9, NOW(), NOW()),
    ('VP_DN',       'Văn phòng Đà Nẵng',           1, 10, NOW(), NOW()),
    ('VP_HCM',      'Văn phòng Hồ Chí Minh',       1, 11, NOW(), NOW()),
    ('BU_VIETLOTT', 'BU Vietlott',                 1, 12, NOW(), NOW())
ON CONFLICT (code) DO UPDATE
    SET name = EXCLUDED.name,
        sort_order = EXCLUDED.sort_order,
        updated_at = NOW();

-- 3) Đơn vị Ban Tổ Chức (tài khoản admin) — không hiện trong form đăng ký
INSERT INTO units (code, name, total_personnel, sort_order, created_at, updated_at)
VALUES ('PB_BTC', 'Ban Tổ Chức (BTC)', 5, 0, NOW(), NOW())
ON CONFLICT (code) DO UPDATE SET sort_order = 0;

-- 4) Xóa các phòng ban cũ / tự sinh không còn dùng và chưa có nhân sự nào
DELETE FROM units u
 WHERE u.code NOT IN ('PB_BTC', 'BAN_LD', 'BAN_KHTH', 'BAN_KD', 'BAN_TC', 'BAN_NL', 'TT_TK',
                      'TT_TVGP', 'BAN_QLDA', 'VP_HN', 'VP_DN', 'VP_HCM', 'BU_VIETLOTT')
   AND NOT EXISTS (SELECT 1 FROM employees e WHERE e.unit_id = u.id);

--rollback -- dữ liệu: không rollback tự động
