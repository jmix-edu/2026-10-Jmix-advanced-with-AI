package com.company.timesheets.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.JmixId;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import io.jmix.core.metamodel.annotation.JmixProperty;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One point of a public dashboard chart: a label and hours. Calculated by {@code PublicDashboardService},
 * never stored. Hours are a plain number because charts need one.
 */
@JmixEntity(name = "ts_DashboardValue", annotatedPropertiesOnly = true)
public class DashboardValue {

    @JmixId
    @JmixGeneratedValue
    @JmixProperty(mandatory = true)
    private UUID id;

    @InstanceName
    @JmixProperty
    private String label;

    @JmixProperty
    private BigDecimal hours;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public BigDecimal getHours() {
        return hours;
    }

    public void setHours(BigDecimal hours) {
        this.hours = hours;
    }
}
