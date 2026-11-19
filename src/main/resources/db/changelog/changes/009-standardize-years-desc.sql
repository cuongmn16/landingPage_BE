--liquibase formatted sql

--changeset ctin:009-standardize-years-desc splitStatements:false
--comment: Chuan hoa years_desc: bo tu Hon va quy doi X nam 12 thang thanh (X+1) nam

-- 1. Chuan hoa cac truong hop 12 thang thanh tron them 1 nam
UPDATE gratitude_letters SET years_desc = REPLACE(years_desc, '24 năm 12 tháng', '25 năm') WHERE years_desc LIKE '%24 năm 12 tháng%';
UPDATE gratitude_letters SET years_desc = REPLACE(years_desc, '28 năm 12 tháng', '29 năm') WHERE years_desc LIKE '%28 năm 12 tháng%';
UPDATE gratitude_letters SET years_desc = REPLACE(years_desc, '25 năm 12 tháng', '26 năm') WHERE years_desc LIKE '%25 năm 12 tháng%';
UPDATE gratitude_letters SET years_desc = REPLACE(years_desc, '26 năm 12 tháng', '27 năm') WHERE years_desc LIKE '%26 năm 12 tháng%';
UPDATE gratitude_letters SET years_desc = REPLACE(years_desc, '4 năm 12 tháng', '5 năm') WHERE years_desc LIKE '%4 năm 12 tháng%';

-- 2. Bo tu 'Hon ' o dau chuoi mo ta
UPDATE gratitude_letters 
SET years_desc = REGEXP_REPLACE(years_desc, '^Hơn\s+', '', 'i')
WHERE years_desc ILIKE 'Hơn %';
