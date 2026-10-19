package com.company.timesheets.view.contactinformationfragment;

import com.company.timesheets.entity.ContactInformation;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.jmix.flowui.component.textarea.JmixTextArea;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.data.value.ContainerValueSource;
import io.jmix.flowui.fragment.Fragment;
import io.jmix.flowui.fragment.FragmentDescriptor;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.ViewComponent;

@FragmentDescriptor("contact-information-fragment.xml")
public class ContactInformationFragment extends Fragment<VerticalLayout> {

//    @ViewComponent
//    private TypedTextField<Object> emailField;
//    @ViewComponent
//    private TypedTextField<Object> urlField;
//    @ViewComponent
//    private TypedTextField<Object> phoneField;
//    @ViewComponent
//    private JmixTextArea addressField;

//    public void setContactInformationValues(InstanceContainer<ContactInformation> instanceContainer) {
//        emailField.setValueSource(new ContainerValueSource<>(instanceContainer,  "email"));
//        urlField.setValueSource(new ContainerValueSource<>(instanceContainer,  "url"));
//        phoneField.setValueSource(new ContainerValueSource<>(instanceContainer,  "phone"));
//        addressField.setValueSource(new ContainerValueSource<>(instanceContainer,  "address"));
//
//    }

}