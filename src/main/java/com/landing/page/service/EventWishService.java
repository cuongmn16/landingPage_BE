package com.landing.page.service;

import com.landing.page.dto.request.EventWishRequest;
import com.landing.page.dto.response.EventWishResponse;
import com.landing.page.dto.response.LiveWishesWallResponse;
import com.landing.page.entity.EventWish;
import com.landing.page.exception.AppException;
import com.landing.page.repository.EventWishRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class EventWishService {

    private final EventWishRepository eventWishRepository;

    private static final List<String> BLACKLIST_PATTERNS = List.of(
            "dm", "dmm", "dkm", "vcl", "vl", "cc", "cl", "dit me", "du ma",
            "fuck", "shit", "bitch", "asshole", "ngu ngoc", "lua dao", "pha san"
    );

    @Transactional
    public EventWishResponse createWish(EventWishRequest request) {
        String senderName = sanitize(request.getSenderName());
        String unitName = sanitize(request.getUnitName());
        String content = sanitize(request.getContent());

        if (senderName.isEmpty()) {
            throw new AppException("Vui lòng nhập họ và tên người gửi.");
        }
        if (unitName.isEmpty()) {
            throw new AppException("Vui lòng chọn đơn vị / phòng ban.");
        }
        if (content.isEmpty()) {
            throw new AppException("Vui lòng nhập nội dung lời chúc.");
        }
        if (senderName.length() > 100) {
            throw new AppException("Họ và tên không vượt quá 100 ký tự.");
        }
        if (unitName.length() > 150) {
            throw new AppException("Tên đơn vị không vượt quá 150 ký tự.");
        }
        if (content.length() > 300) {
            throw new AppException("Nội dung lời chúc không vượt quá 300 ký tự.");
        }

        if (containsBannedWords(senderName) || containsBannedWords(content)) {
            throw new AppException("Lời chúc chứa từ ngữ chưa phù hợp với không khí sự kiện. Vui lòng điều chỉnh lại nhé!");
        }

        EventWish saved = eventWishRepository.save(EventWish.builder()
                .senderName(senderName)
                .unitName(unitName)
                .content(content)
                .isHidden(false)
                .build());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public LiveWishesWallResponse getLiveWishes() {
        long totalCount = eventWishRepository.countByIsHiddenFalse();
        List<EventWishResponse> wishes = eventWishRepository.findTop200ByIsHiddenFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();

        return LiveWishesWallResponse.builder()
                .totalCount(totalCount)
                .wishes(wishes)
                .build();
    }

    @Transactional(readOnly = true)
    public List<EventWishResponse> getAllWishesForAdmin() {
        return eventWishRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public EventWishResponse toggleHideWish(Long id, boolean isHidden) {
        EventWish wish = eventWishRepository.findById(id)
                .orElseThrow(() -> new AppException("Không tìm thấy lời chúc #" + id, HttpStatus.NOT_FOUND));
        wish.setIsHidden(isHidden);
        return toResponse(eventWishRepository.save(wish));
    }

    @Transactional
    public void deleteWish(Long id) {
        if (!eventWishRepository.existsById(id)) {
            throw new AppException("Không tìm thấy lời chúc #" + id, HttpStatus.NOT_FOUND);
        }
        eventWishRepository.deleteById(id);
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.trim().replaceAll("\\s+", " ");
    }

    private boolean containsBannedWords(String text) {
        String normalized = removeAccents(text).toLowerCase(Locale.ROOT);
        for (String banned : BLACKLIST_PATTERNS) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(banned) + "\\b", Pattern.CASE_INSENSITIVE);
            if (p.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    private String removeAccents(String text) {
        if (text == null) return "";
        String temp = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(temp).replaceAll("").replace('đ', 'd').replace('Đ', 'D');
    }

    private EventWishResponse toResponse(EventWish w) {
        return EventWishResponse.builder()
                .id(w.getId())
                .senderName(w.getSenderName())
                .unitName(w.getUnitName())
                .content(w.getContent())
                .isHidden(w.getIsHidden())
                .createdAt(w.getCreatedAt())
                .build();
    }
}
