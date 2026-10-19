--liquibase formatted sql

--changeset ctin:003-units-sort-order
--comment: Thứ tự hiển thị phòng ban (dropdown đăng ký, trang admin).

ALTER TABLE units ADD COLUMN IF NOT EXISTS sort_order INTEGER;

--rollback ALTER TABLE units DROP COLUMN IF EXISTS sort_order;
