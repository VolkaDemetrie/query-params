package io.github.volkayun.queryparams.exception;

public class CaseConvertException extends RuntimeException {
    public CaseConvertException(String message) {
        super(message);
    }

    public CaseConvertException(String message, Throwable cause) {
        super(message, cause);
    }

    public CaseConvertException(Throwable cause) {
        super(cause);
    }

    public CaseConvertException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
