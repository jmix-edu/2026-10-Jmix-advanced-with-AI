package com.company.timesheets.view.client;

import com.company.timesheets.entity.Client;
import com.company.timesheets.repository.ClientRepository;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.core.repository.JmixDataRepositoryContext;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;


@Route(value = "clients-repo", layout = MainView.class)
@ViewController(id = "ts_Client.list-repo")
@ViewDescriptor(path = "client-list-view-repo.xml")
@LookupComponent("clientsDataGrid")
@DialogMode(width = "64em")
public class ClientListViewRepo extends StandardListView<Client> {

    @Autowired
    private ClientRepository repository;


    @Install(to = "clientsDl", target = Target.DATA_LOADER, subject = "loadFromRepositoryDelegate")
    private List<Client> loadDelegate(Pageable pageable, JmixDataRepositoryContext context) {
        return repository.findAllSlice(pageable, context).getContent();
    }

    @Install(to = "pagination", subject = "totalCountByRepositoryDelegate")
    private Long paginationTotalCountByRepositoryDelegate(final JmixDataRepositoryContext context) {
        return repository.count(context);
    }

    @Install(to = "clientsDataGrid.removeAction", subject = "delegate")
    private void clientsDataGridRemoveDelegate(final Collection<Client> collection) {
        repository.deleteAll(collection);
    }

}