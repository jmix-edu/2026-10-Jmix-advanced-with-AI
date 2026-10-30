package com.company.timesheets.component;

import io.jmix.flowui.xml.layout.loader.AbstractComponentLoader;

/**
 * Загружает {@code <ts:weekSheet>} из дескриптора экрана: размер, имена классов, inline css и {@code readOnly}.
 * Модель — не XML-атрибут: экран задаёт её из кода, когда данные загружены.
 */
public class WeekSheetLoader extends AbstractComponentLoader<WeekSheet> {

    @Override
    protected WeekSheet createComponent() {
        // Через UiComponents, как Jmix создаёт свои компоненты: экземпляр получает autowiring и bean post-processing.
        return factory.create(WeekSheet.class);
    }

    @Override
    public void loadComponent() {
        // id и visible загружает базовый класс.
        componentLoader().loadSizeAttributes(resultComponent, element);
        componentLoader().loadClassNames(resultComponent, element);
        componentLoader().loadCss(resultComponent, element);
        loaderSupport.loadBoolean(element, "readOnly", resultComponent::setReadOnly);
    }
}
