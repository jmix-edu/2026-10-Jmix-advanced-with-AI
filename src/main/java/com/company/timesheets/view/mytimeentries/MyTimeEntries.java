package com.company.timesheets.view.mytimeentries;


import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;

@Route(value = "my-time-entries", layout = MainView.class)
@ViewController(id = "TimeEntries.my")
@ViewDescriptor(path = "my-time-entries.xml")
public class MyTimeEntries extends StandardView {
}