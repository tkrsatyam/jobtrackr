package com.jobtrackr.user_service.exception;

public class AccountAlreadyLinkedException extends RuntimeException {
    public AccountAlreadyLinkedException(String message) {
        super(message);
    }
}
