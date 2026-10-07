package com.company.timesheets.datatype;

import java.io.Serializable;

/**
 * Spent time in whole minutes. Displayed in the UI as {@code hours:minutes}, e.g. {@code 2:30}.
 * Serializable because entities holding it are stored in the Vaadin session.
 */
public record SpentTime(long minutes) implements Serializable {

    public SpentTime {
        if (minutes < 0) {
            throw new IllegalArgumentException("Spent time cannot be negative: " + minutes);
        }
    }
}
