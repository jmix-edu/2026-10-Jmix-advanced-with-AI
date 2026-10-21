package com.company.timesheets.component;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

public class ColorComponent extends Composite<HorizontalLayout> {

    private final ColorPicker colorPicker;

    public ColorComponent() {
        colorPicker = new ColorPicker();
        Span valueLabel = new Span(colorPicker.getValue());

        colorPicker.addValueChangeListener(event ->
                valueLabel.setText(event.getValue()));

        getContent().add(colorPicker, valueLabel);
    }

    @Override
    protected HorizontalLayout initContent() {
        HorizontalLayout content = super.initContent();
        content.setAlignItems(FlexComponent.Alignment.CENTER);

        return content;
    }

    public String getValue() {
        return colorPicker.getValue();
    }

    public void setValue(String value) {
        colorPicker.setValue(value);
    }
}
