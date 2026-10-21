package com.company.timesheets.component;

import io.jmix.flowui.sys.registration.ComponentRegistration;
import io.jmix.flowui.sys.registration.ComponentRegistrationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the application's own UI components so view descriptors can use them.
 * The loader is found by the element's local name; the namespace is for the XSD and Studio.
 */
@Configuration
public class TimesheetsComponentsConfiguration {

    @Bean
    public ComponentRegistration spentTimeField() {
        return ComponentRegistrationBuilder.create(SpentTimeField.class)
                .withComponentLoader("spentTimeField", SpentTimeFieldLoader.class)
                .build();
    }
}
