package com.company.timesheets.service;

/**
 * Активация отклонена: ссылка больше не действует или пароль не подходит. Сообщение показывается пользователю.
 */
public class ActivationException extends RuntimeException {

    public ActivationException(String message) {
        super(message);
    }
}
