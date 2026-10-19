package com.landing.page.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitResponse {
    private Long id;
    private String code;
    private String name;
    /** Tổng quân số của đơn vị (mẫu số khi tính tỷ lệ tham gia). */
    private Integer totalPersonnel;
    private Integer sortOrder;
    /** Số CBNV đã tham gia = đã đăng ký và được BTC duyệt (không tính admin). */
    private Long participantCount;
    /** Số CBNV đã đăng ký, đang chờ duyệt. */
    private Long pendingCount;
    /** Tổng tài khoản thuộc đơn vị (mọi trạng thái, kể cả admin) — dùng để chặn xóa. */
    private Long registeredCount;
    /** participantCount / totalPersonnel × 100, làm tròn 1 chữ số. */
    private Double participationRate;
    private LocalDateTime updatedAt;
}
