package uk.ac.ebi.spot.ols.controller.api.v2.responses;

import org.springframework.data.domain.Page;

import java.util.List;

public class V2PagedResponse<T> {

    public V2PagedResponse(Page<T> page) {
        this.elements = page.getContent();
        this.page = page.getNumber();
        this.numElements = page.getNumberOfElements();
        this.totalPages = page.getTotalPages();
        this.totalElements = page.getTotalElements();
    }

    public long page;
    public long numElements;
    public long totalPages;
    public long totalElements;
    public List<T> elements;

}
