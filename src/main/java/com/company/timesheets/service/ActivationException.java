package com.company.timesheets.service;

/**
 * Activation refused: the link is not valid any more or the password does not fit. The message is for the user.
 */
public class ActivationException extends RuntimeException {

    public ActivationException(String message) {
        super(message);
    }
}
