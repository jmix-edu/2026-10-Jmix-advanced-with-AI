package com.company.timesheets.component;

import io.jmix.flowui.kit.meta.StudioComponent;
import io.jmix.flowui.kit.meta.StudioPropertiesBinding;
import io.jmix.flowui.kit.meta.StudioProperty;
import io.jmix.flowui.kit.meta.StudioPropertyGroups;
import io.jmix.flowui.kit.meta.StudioPropertyType;
import io.jmix.flowui.kit.meta.StudioUiKit;

/**
 * Описывает собственные компоненты приложения для Jmix Studio: палитру, XML-элемент и свойства в инспекторе.
 * Его читает только Studio, приложение его не вызывает. Та же форма, что у собственных StudioComponents в Jmix.
 */
@StudioUiKit
interface TimesheetsStudioComponents {

    @StudioComponent(
            name = "SpentTimeField",
            classFqn = "com.company.timesheets.component.SpentTimeField",
            category = "Components",
            xmlElement = "spentTimeField",
            xmlns = "http://company.com/timesheets/schema/components",
            xmlnsAlias = "ts",
            propertyGroups = {
                    StudioPropertyGroups.ValidatableBaseFieldComponent.class,
                    StudioPropertyGroups.Property.class,
                    StudioPropertyGroups.RequiredAttributes.class
            },
            propertiesBindings = {
                    @StudioPropertiesBinding(
                            source = "dataContainer",
                            item = "property"
                    )
            }
    )
    SpentTimeField spentTimeField();

    // Без привязки к данным: экран передаёт таблице модель из кода.
    @StudioComponent(
            name = "WeekSheet",
            classFqn = "com.company.timesheets.component.WeekSheet",
            category = "Components",
            xmlElement = "weekSheet",
            xmlns = "http://company.com/timesheets/schema/components",
            xmlnsAlias = "ts",
            propertyGroups = {
                    StudioPropertyGroups.BaseSizedComponentWithClassNames.class
            },
            properties = {
                    @StudioProperty(xmlAttribute = "readOnly", type = StudioPropertyType.BOOLEAN,
                            defaultValue = "false")
            }
    )
    WeekSheet weekSheet();
}
