--liquibase formatted sql

--changeset ctin:007-gratitude-letters-published splitStatements:false
--comment: Bo sung cot is_published cho bang gratitude_letters de quan ly phat hanh rieng tung thu

ALTER TABLE gratitude_letters ADD COLUMN IF NOT EXISTS is_published BOOLEAN DEFAULT FALSE;
