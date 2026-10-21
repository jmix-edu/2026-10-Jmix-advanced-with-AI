package com.company.timesheets.component;

import io.jmix.flowui.kit.meta.StudioComponent;
import io.jmix.flowui.kit.meta.StudioPropertiesBinding;
import io.jmix.flowui.kit.meta.StudioPropertyGroups;
import io.jmix.flowui.kit.meta.StudioUiKit;

/**
 * Describes the application's own components for Jmix Studio: palette, XML element and inspector properties.
 * Read by Studio only; the application never calls it. Same shape as Jmix's own StudioComponents.
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
}
