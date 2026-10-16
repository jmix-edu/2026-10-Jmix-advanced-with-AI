package com.company.timesheets.view.pessimisticlocklist;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.DefaultMainViewParent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.pessimisticlockflowui.view.pessimisticlock.PessimisticLockListView;

@Route(value = "pslock/pessimistic-locks", layout = DefaultMainViewParent.class)
@ViewController(id = "pslock_LockInfo.list")
@ViewDescriptor(path = "ts-pessimistic-lock-list-view.xml")
public class TsPessimisticLockListView extends PessimisticLockListView {
}