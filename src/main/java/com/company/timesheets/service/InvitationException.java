package com.company.timesheets.service;

/**
 * Приглашение отклонено по причине, которую должен увидеть приглашающий менеджер.
 */
public class InvitationException extends RuntimeException {

    public InvitationException(String message) {
        super(message);
    }
}
