package com.landing.page.dto.response;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LiveWishesWallResponse {

    private long totalCount;
    private List<EventWishResponse> wishes;
}
