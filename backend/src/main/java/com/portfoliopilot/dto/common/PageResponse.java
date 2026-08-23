package com.portfoliopilot.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A stable, minimal page envelope.
 *
 * <p>Spring's {@code Page} serialises to a large, unstable JSON shape (it leaks
 * {@code pageable}, {@code sort.unsorted}, etc. and Boot warns about it), so the
 * API exposes this instead.
 *
 * @param content       the mapped items for this page
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total matching documents
 * @param totalPages    total pages available
 * @param first         true when this is the first page
 * @param last          true when this is the last page
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    /** Maps a {@code Page<E>} of documents into a {@code PageResponse<T>} of DTOs. */
    public static <E, T> PageResponse<T> from(Page<E> source, Function<E, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages(),
                source.isFirst(),
                source.isLast()
        );
    }

    /** For results paginated manually (e.g. an aggregation) rather than by Spring Data. */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(
                content, page, size, totalElements, totalPages,
                page == 0,
                page >= totalPages - 1
        );
    }
}
