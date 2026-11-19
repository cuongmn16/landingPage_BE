--liquibase formatted sql

--changeset ctin:009-cleanup-demo-accounts splitStatements:false
--comment: Don dep 4 tai khoan demo khoi bang employees sau khi hoan tat kiem thu

DELETE FROM employees WHERE email IN ('lanmh@ctin.vn', 'nangpn@ctin.vn', 'taihd@ctin.vn', 'anhlp@ctin.vn');
