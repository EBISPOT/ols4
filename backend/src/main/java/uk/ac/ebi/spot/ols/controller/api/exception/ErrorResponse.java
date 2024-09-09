package uk.ac.ebi.spot.ols.controller.api.exception;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ErrorResponse {
    @JsonProperty("status")
    private int http_status;

    @JsonProperty("message")
    private String message;

    public ErrorResponse(int http_status, String message) {
        this.http_status = http_status;
        this.message = message;
    }
}
