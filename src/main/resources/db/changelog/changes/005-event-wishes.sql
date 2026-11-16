--liquibase formatted sql

--changeset ctin:005-event-wishes
--comment: Bảng lưu trữ lời chúc sự kiện trực tiếp (Live Wishing Wall - Kỷ niệm 25 năm CTIN)

CREATE TABLE IF NOT EXISTS event_wishes (
    id          BIGSERIAL PRIMARY KEY,
    sender_name VARCHAR(100) NOT NULL,
    unit_name   VARCHAR(150) NOT NULL,
    content     VARCHAR(500) NOT NULL,
    is_hidden   BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_event_wishes_live
    ON event_wishes (is_hidden, created_at DESC);

--rollback DROP TABLE IF EXISTS event_wishes;
