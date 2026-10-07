package com.company.timesheets.view.client;

import com.company.timesheets.entity.Client;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;


@Route(value = "clients/:id", layout = MainView.class)
@ViewController(id = "ts_Client.detail")
@ViewDescriptor(path = "client-detail-view.xml")
@EditedEntityContainer("clientDc")
public class ClientDetailView extends StandardDetailView<Client> {
}