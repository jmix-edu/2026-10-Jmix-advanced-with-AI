package com.company.timesheets.view.client;

import com.company.timesheets.entity.Client;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.image.JmixImage;
import io.jmix.flowui.component.upload.FileUploadField;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.*;


@Route(value = "clients/:id", layout = MainView.class)
@ViewController(id = "ts_Client.detail")
@ViewDescriptor(path = "client-detail-view.xml")
@EditedEntityContainer("clientDc")
public class ClientDetailView extends StandardDetailView<Client> {

    @ViewComponent
    private HorizontalLayout uploadWrapper;
    @ViewComponent
    private FileUploadField imageUpload;
    @ViewComponent
    private JmixImage<byte[]> image;

    @Subscribe
    public void onReady(final ReadyEvent event) {
        updateImage(getEditedEntity().getImage());
        uploadWrapper.setVisible(!imageUpload.isReadOnly());
    }

    @Subscribe(id = "clientDc", target = Target.DATA_CONTAINER)
    public void onClientDcItemPropertyChange(final InstanceContainer.ItemPropertyChangeEvent<Client> event) {
        if ("image".equals(event.getProperty())) {
            updateImage(getEditedEntity().getImage());
        }
    }

    private void updateImage(byte[] value) {
        if (value == null) {
            image.setSrc("public/images/add-image-placeholder.png");
        }
    }

    @Subscribe(id = "uploadClearBtn", subject = "clickListener")
    public void onUploadClearBtnClick(final ClickEvent<JmixButton> event) {
        getEditedEntity().setImage(null);
    }


}