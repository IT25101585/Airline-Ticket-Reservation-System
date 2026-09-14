package com.skylanka.ticketmanagement.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standardised API response wrapper for all endpoints.
 *
 * @param <T> the type of the response data payload
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private String errorCode;
    private T data;

    public ApiResponse() {}

    public ApiResponse(boolean success, String message, String errorCode, T data) {
        this.success   = success;
        this.message   = message;
        this.errorCode = errorCode;
        this.data      = data;
    }

    // ---- factory helpers ----

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, null, data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, null, data);
    }

    public static ApiResponse<Void> error(String message, String errorCode) {
        return new ApiResponse<>(false, message, errorCode, null);
    }

    // ---- getters / setters ----

    public boolean isSuccess()       { return success; }
    public void setSuccess(boolean s){ this.success = s; }

    public String getMessage()         { return message; }
    public void setMessage(String m)   { this.message = m; }

    public String getErrorCode()         { return errorCode; }
    public void setErrorCode(String ec)  { this.errorCode = ec; }

    public T getData()          { return data; }
    public void setData(T data) { this.data = data; }
}
