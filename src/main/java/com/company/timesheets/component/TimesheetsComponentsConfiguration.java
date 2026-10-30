package com.company.timesheets.component;

import io.jmix.flowui.sys.registration.ComponentRegistration;
import io.jmix.flowui.sys.registration.ComponentRegistrationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Регистрирует собственные UI-компоненты приложения, чтобы их можно было использовать в дескрипторах экранов.
 * Загрузчик находится по локальному имени элемента; пространство имён нужно для XSD и Studio.
 */
@Configuration
public class TimesheetsComponentsConfiguration {

    @Bean
    public ComponentRegistration spentTimeField() {
        return ComponentRegistrationBuilder.create(SpentTimeField.class)
                .withComponentLoader("spentTimeField", SpentTimeFieldLoader.class)
                .build();
    }

    // <ts:weekSheet>: недельная таблица на основе RevoGrid (specs/02_ui_development/05-week-sheet.spec.adoc).
    @Bean
    public ComponentRegistration weekSheet() {
        return ComponentRegistrationBuilder.create(WeekSheet.class)
                .withComponentLoader("weekSheet", WeekSheetLoader.class)
                .build();
    }
}
