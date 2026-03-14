package com.mercury.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic paginated result wrapper.
 * Contains the list of items along with pagination metadata.
 *
 * @param <T> the type of items in the result
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    /** Items for the current page */
    private List<T> items;
    /** Total number of matching records */
    private long total;
    /** Current page number (1-based) */
    private int page;
    /** Page size */
    private int size;
}
