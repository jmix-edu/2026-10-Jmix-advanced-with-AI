package com.company.timesheets.datatype;

import java.io.Serializable;

/**
 * Затраченное время в целых минутах. В UI показывается как {@code hours:minutes}, например {@code 2:30}.
 * Serializable, потому что сущности с этим полем хранятся в сессии Vaadin.
 */
public record SpentTime(long minutes) implements Serializable {

    public SpentTime {
        if (minutes < 0) {
            throw new IllegalArgumentException("Spent time cannot be negative: " + minutes);
        }
    }
}
