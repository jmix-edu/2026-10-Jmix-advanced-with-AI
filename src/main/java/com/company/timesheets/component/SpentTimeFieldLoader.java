package com.company.timesheets.component;

import io.jmix.flowui.xml.layout.loader.AbstractComponentLoader;
import io.jmix.flowui.xml.layout.support.DataLoaderSupport;

/**
 * Загружает {@code <ts:spentTimeField>}: те же атрибуты, что у стандартных полей, а {@code property} и
 * {@code dataContainer} берутся из элемента или из его {@code formLayout}.
 */
public class SpentTimeFieldLoader extends AbstractComponentLoader<SpentTimeField> {

    protected DataLoaderSupport dataLoaderSupport;

    @Override
    protected SpentTimeField createComponent() {
        return factory.create(SpentTimeField.class);
    }

    @Override
    public void loadComponent() {
        getDataLoaderSupport().loadData(resultComponent, element);

        componentLoader().loadLabel(resultComponent, element);
        componentLoader().loadEnabled(resultComponent, element);
        componentLoader().loadClassNames(resultComponent, element);
        componentLoader().loadHelperText(resultComponent, element);
        componentLoader().loadSizeAttributes(resultComponent, element);
        componentLoader().loadRequired(resultComponent, element, context);
        componentLoader().loadValueAndElementAttributes(resultComponent, element);
        componentLoader().loadValidationAttributes(resultComponent, element, context);
    }

    protected DataLoaderSupport getDataLoaderSupport() {
        if (dataLoaderSupport == null) {
            dataLoaderSupport = applicationContext.getBean(DataLoaderSupport.class, context);
        }
        return dataLoaderSupport;
    }
}
