package com.jobtrackr.application_service.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PaginationUtils {

    private PaginationUtils() {}

    public static Pageable buildPageable(int page, int size, String sortBy, String sortDir) {

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        String resolvedSortBy = (sortBy == null || sortBy.isBlank()) ? "createdAt" : sortBy;

        return PageRequest.of(page, size, Sort.by(direction, resolvedSortBy));
    }
}
