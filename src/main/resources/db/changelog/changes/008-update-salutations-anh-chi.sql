--liquibase formatted sql

--changeset ctin:008-update-salutations-anh-chi splitStatements:false
--comment: Chuyen doi danh xung Ong thanh Anh va Ba thanh Chi de phu hop van hoa tre trung, gan gui cua CTIN

UPDATE gratitude_letters SET salutation = 'Anh' WHERE salutation = 'Ông';
UPDATE gratitude_letters SET salutation = 'Chị' WHERE salutation = 'Bà';
