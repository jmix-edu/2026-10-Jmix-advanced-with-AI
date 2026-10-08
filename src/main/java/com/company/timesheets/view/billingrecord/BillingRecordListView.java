package com.company.timesheets.view.billingrecord;

import com.company.timesheets.entity.BillingRecord;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;


@Route(value = "billing-records", layout = MainView.class)
@ViewController(id = "ts_BillingRecord.list")
@ViewDescriptor(path = "billing-record-list-view.xml")
@LookupComponent("billingRecordsDataGrid")
@DialogMode(width = "64em")
public class BillingRecordListView extends StandardListView<BillingRecord> {

}