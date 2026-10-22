package com.company.timesheets.service;

/**
 * An invitation refused for a reason the inviting manager should read.
 */
public class InvitationException extends RuntimeException {

    public InvitationException(String message) {
        super(message);
    }
}
