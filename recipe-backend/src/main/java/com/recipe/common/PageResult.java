package com.recipe.common;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private List<T> content;
    private long page;
    private long size;
    private long total;
    private long totalPages;

    public static <T> PageResult<T> of(List<T> content, long page, long size, long total) {
        PageResult<T> r = new PageResult<>();
        r.content = content;
        r.page = page;
        r.size = size;
        r.total = total;
        r.totalPages = size == 0 ? 0 : (total + size - 1) / size;
        return r;
    }
}
