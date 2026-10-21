package com.company.timesheets.component;

import com.company.timesheets.datatype.SpentTime;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.customfield.CustomField;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.shared.Registration;
import io.jmix.core.Messages;
import io.jmix.flowui.component.HasRequired;
import io.jmix.flowui.component.SupportsStatusChangeHandler;
import io.jmix.flowui.component.SupportsValidation;
import io.jmix.flowui.component.delegate.FieldDelegate;
import io.jmix.flowui.component.validation.Validator;
import io.jmix.flowui.data.SupportsValueSource;
import io.jmix.flowui.data.ValueSource;
import io.jmix.flowui.exception.ValidationException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.function.Consumer;

/**
 * Field for a {@link SpentTime} attribute: hours, minutes and quick «+0:15», «+1:00» buttons.
 * Bound, validated and marked required the way standard Jmix fields are: through {@link FieldDelegate}.
 * See specs/02_ui_development/04-spent-time-field.spec.adoc.
 */
public class SpentTimeField extends CustomField<SpentTime> implements SupportsValueSource<SpentTime>,
        SupportsValidation<SpentTime>, SupportsStatusChangeHandler<SpentTimeField>, HasRequired,
        ApplicationContextAware, InitializingBean {

    static final int MAX_HOURS = 24;
    static final int MAX_MINUTES = 59;
    static final int MINUTES_STEP = 15;

    protected final IntegerField hoursField = new IntegerField();
    protected final IntegerField minutesField = new IntegerField();
    protected final Button add15Button = new Button("+0:15");
    protected final Button add60Button = new Button("+1:00");

    protected ApplicationContext applicationContext;
    protected Messages messages;
    protected FieldDelegate<SpentTimeField, SpentTime, SpentTime> fieldDelegate;

    /**
     * The typed hours or minutes are out of range: the model keeps its last valid value and the field shows an error.
     */
    protected boolean partsInvalid;

    public SpentTimeField() {
        // The parts are validated here, as one value; their own validation would also flag minutes off the step.
        hoursField.setMin(0);
        hoursField.setMax(MAX_HOURS);
        hoursField.setStepButtonsVisible(true);
        hoursField.setManualValidation(true);

        minutesField.setMin(0);
        minutesField.setMax(MAX_MINUTES);
        minutesField.setStep(MINUTES_STEP);
        minutesField.setStepButtonsVisible(true);
        minutesField.setManualValidation(true);

        add15Button.addThemeVariants(ButtonVariant.TERTIARY);
        add60Button.addThemeVariants(ButtonVariant.TERTIARY);
        add15Button.addClickListener(event -> setValue(plus(getValue(), MINUTES_STEP)));
        add60Button.addClickListener(event -> setValue(plus(getValue(), 60)));

        Div parts = new Div(hoursField, minutesField, add15Button, add60Button);
        parts.addClassName("spent-time-field-parts");
        add(parts);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterPropertiesSet() {
        initComponent();
    }

    @SuppressWarnings("unchecked")
    protected void initComponent() {
        messages = applicationContext.getBean(Messages.class);
        fieldDelegate = applicationContext.getBean(FieldDelegate.class, this);

        hoursField.setSuffixComponent(new Span(messages.getMessage(getClass(), "spentTimeField.hours")));
        minutesField.setSuffixComponent(new Span(messages.getMessage(getClass(), "spentTimeField.minutes")));
        add15Button.setAriaLabel(messages.getMessage(getClass(), "spentTimeField.add15.ariaLabel"));
        add60Button.setAriaLabel(messages.getMessage(getClass(), "spentTimeField.add60.ariaLabel"));

        fieldDelegate.addValidator(value -> {
            if (partsInvalid) {
                throw new ValidationException(messages.getMessage(getClass(), "spentTimeField.invalidParts"));
            }
        });
        addValueChangeListener(event -> fieldDelegate.updateInvalidState());
    }

    @Override
    protected void updateValue() {
        try {
            toSpentTime(hoursField.getValue(), minutesField.getValue());
            partsInvalid = false;
            super.updateValue();
        } catch (IllegalArgumentException e) {
            partsInvalid = true;
        }
        fieldDelegate.updateInvalidState();
    }

    @Override
    @Nullable
    protected SpentTime generateModelValue() {
        try {
            return toSpentTime(hoursField.getValue(), minutesField.getValue());
        } catch (IllegalArgumentException e) {
            return getValue();
        }
    }

    @Override
    protected void setPresentationValue(@Nullable SpentTime value) {
        Parts parts = toParts(value);
        hoursField.setValue(parts.hours());
        minutesField.setValue(parts.minutes());
        partsInvalid = false;
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        super.setReadOnly(readOnly);
        hoursField.setReadOnly(readOnly);
        minutesField.setReadOnly(readOnly);
        add15Button.setVisible(!readOnly);
        add60Button.setVisible(!readOnly);
    }

    // --- value conversion, see SpentTimeFieldTest ---

    /**
     * Hours and minutes typed in the field. Both empty is no value, one empty counts as zero.
     *
     * @throws IllegalArgumentException when hours are not within 0..24 or minutes within 0..59
     */
    @Nullable
    static SpentTime toSpentTime(@Nullable Integer hours, @Nullable Integer minutes) {
        if (hours == null && minutes == null) {
            return null;
        }
        int h = hours == null ? 0 : hours;
        int m = minutes == null ? 0 : minutes;
        if (h < 0 || h > MAX_HOURS || m < 0 || m > MAX_MINUTES) {
            throw new IllegalArgumentException("Out of range: " + h + "h " + m + "min");
        }
        return new SpentTime(h * 60L + m);
    }

    static Parts toParts(@Nullable SpentTime value) {
        if (value == null) {
            return new Parts(null, null);
        }
        return new Parts((int) (value.minutes() / 60), (int) (value.minutes() % 60));
    }

    /**
     * Adds minutes for a quick button; no value counts as {@code 0:00}.
     */
    static SpentTime plus(@Nullable SpentTime value, long minutes) {
        return new SpentTime((value == null ? 0 : value.minutes()) + minutes);
    }

    record Parts(@Nullable Integer hours, @Nullable Integer minutes) {
    }

    // --- Jmix field contract, delegated as in the standard fields ---

    @Nullable
    @Override
    public ValueSource<SpentTime> getValueSource() {
        return fieldDelegate.getValueSource();
    }

    @Override
    public void setValueSource(@Nullable ValueSource<SpentTime> valueSource) {
        fieldDelegate.setValueSource(valueSource);
    }

    @Override
    public Registration addValidator(Validator<? super SpentTime> validator) {
        return fieldDelegate.addValidator(validator);
    }

    @Override
    public void executeValidators() throws ValidationException {
        fieldDelegate.executeValidators();
    }

    @Override
    public boolean isInvalid() {
        return fieldDelegate.isInvalid();
    }

    @Override
    public void setInvalid(boolean invalid) {
        // Called from the constructor, before the delegate exists
        if (fieldDelegate != null) {
            fieldDelegate.setInvalid(invalid);
        } else {
            super.setInvalid(invalid);
        }
    }

    @Nullable
    @Override
    public String getErrorMessage() {
        return fieldDelegate.getErrorMessage();
    }

    @Override
    public void setErrorMessage(@Nullable String errorMessage) {
        // Called from the constructor, before the delegate exists
        if (fieldDelegate != null) {
            fieldDelegate.setErrorMessage(errorMessage);
        } else {
            super.setErrorMessage(errorMessage);
        }
    }

    @Override
    public void setStatusChangeHandler(@Nullable Consumer<StatusContext<SpentTimeField>> handler) {
        fieldDelegate.setStatusChangeHandler(handler);
    }

    @Override
    public void setRequired(boolean required) {
        HasRequired.super.setRequired(required);
        fieldDelegate.updateRequiredState();
    }

    @Override
    public void setRequiredIndicatorVisible(boolean requiredIndicatorVisible) {
        super.setRequiredIndicatorVisible(requiredIndicatorVisible);
        if (fieldDelegate != null) {
            fieldDelegate.updateRequiredState();
        }
    }

    @Nullable
    @Override
    public String getRequiredMessage() {
        return fieldDelegate.getRequiredMessage();
    }

    @Override
    public void setRequiredMessage(@Nullable String requiredMessage) {
        fieldDelegate.setRequiredMessage(requiredMessage);
    }
}
