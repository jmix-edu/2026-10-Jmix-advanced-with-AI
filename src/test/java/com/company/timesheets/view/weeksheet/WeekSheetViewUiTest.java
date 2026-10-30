package com.company.timesheets.view.weeksheet;

import com.company.timesheets.TimesheetsApplication;
import com.company.timesheets.component.WeekSheet;
import com.company.timesheets.component.WeekSheetModel;
import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.ContactInformation;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectParticipant;
import com.company.timesheets.entity.ProjectRole;
import com.company.timesheets.entity.ProjectRoleType;
import com.company.timesheets.entity.Task;
import com.company.timesheets.entity.TimeEntry;
import com.company.timesheets.entity.User;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.internal.JacksonUtils;
import com.vaadin.flow.internal.nodefeature.ElementListenerMap;
import com.vaadin.flow.router.QueryParameters;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Серверная половина недельной таблицы: экран открывается с <ts:weekSheet> в дескрипторе, передаёт компоненту
 * модель и сохраняет то, что прислал браузер. Сам браузер имитируется: DOM-событие компонента выстреливает
 * на сервере с теми же данными, что шлёт JS-модуль (specs/02_ui_development/05-week-sheet.spec.adoc).
 */
@UiTest
@SpringBootTest(classes = {TimesheetsApplication.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class WeekSheetViewUiTest {

    private static final String ADMIN = "admin";
    /** Среда: экран должен показать её неделю с понедельника 2001-01-01. */
    private static final LocalDate LINKED_DAY = LocalDate.of(2001, 1, 3);
    private static final LocalDate MONDAY = LocalDate.of(2001, 1, 1);

    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private DataManager dataManager;
    @Autowired
    private SystemAuthenticator systemAuthenticator;
    @Autowired
    private DataSource dataSource;

    private final String prefix = "wsui-" + UUID.randomUUID().toString().substring(0, 8) + "-";
    private Task task;
    private UUID clientId;
    private UUID projectRoleId;

    @BeforeEach
    void createData() {
        systemAuthenticator.runWithSystem(() -> {
            User admin = dataManager.load(User.class)
                    .query("select u from ts_User u where u.username = :username")
                    .parameter("username", ADMIN)
                    .one();
            ProjectRole member = dataManager.create(ProjectRole.class);
            member.setName(prefix + "member");
            member.setType(ProjectRoleType.MEMBER);
            projectRoleId = dataManager.save(member).getId();

            Client client = dataManager.create(Client.class);
            client.setName(prefix + "client");
            client.setContactInformation(dataManager.create(ContactInformation.class));
            clientId = dataManager.save(client).getId();

            Project project = dataManager.create(Project.class);
            project.setName(prefix + "project");
            project.setClient(client);
            project = dataManager.save(project);

            ProjectParticipant participant = dataManager.create(ProjectParticipant.class);
            participant.setProject(project);
            participant.setUser(admin);
            participant.setProjectRole(member);
            dataManager.saveWithoutReload(participant);

            Task created = dataManager.create(Task.class);
            created.setName(prefix + "task");
            created.setProject(project);
            task = dataManager.save(created);
        });
    }

    @AfterEach
    void removeData() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String projects = "select ID from TS_PROJECT where CLIENT_ID = ?";
        String tasks = "select ID from TS_TASK where PROJECT_ID in (" + projects + ")";
        jdbc.update("delete from TS_TIME_ENTRY where TASK_ID in (" + tasks + ")", clientId);
        jdbc.update("delete from TS_TASK where PROJECT_ID in (" + projects + ")", clientId);
        jdbc.update("delete from TS_PROJECT_PARTICIPANT where PROJECT_ID in (" + projects + ")", clientId);
        jdbc.update("delete from TS_PROJECT where CLIENT_ID = ?", clientId);
        jdbc.update("delete from TS_CLIENT where ID = ?", clientId);
        jdbc.update("delete from TS_PROJECT_ROLE where ID = ?", projectRoleId);
    }

    @Test
    void aLinkOpensItsWeekWithTheTaskRow() {
        systemAuthenticator.runWithUser(ADMIN, () -> {
            WeekSheetView view = open();

            WeekSheetModel model = sheet(view).getModel();
            assertThat(model).isNotNull();
            assertThat(model.days()).hasSize(7);
            assertThat(model.days().get(0).key()).isEqualTo(MONDAY.toString());
            assertThat(model.rows()).anySatisfy(row -> {
                assertThat(row.key()).isEqualTo(task.getId().toString());
                assertThat(row.cells()).allSatisfy(cell -> assertThat(cell.editable()).isTrue());
            });
            assertThat(saveButton(view).isEnabled()).isFalse();
        });
    }

    @Test
    void changesFromTheBrowserEnableSaveAndTypingTheOldValueBackIsNoChange() {
        systemAuthenticator.runWithUser(ADMIN, () -> {
            WeekSheetView view = open();
            WeekSheet sheet = sheet(view);

            // Неизвестная строка игнорируется: данные приходят из браузера.
            browserChanges(sheet, change(task.getId().toString(), "1:30"), change(UUID.randomUUID().toString(), "5"));
            assertThat(sheet.getChanges()).singleElement()
                    .satisfies(c -> assertThat(c.text()).isEqualTo("1:30"));
            assertThat(saveButton(view).isEnabled()).isTrue();

            browserChanges(sheet, change(task.getId().toString(), ""));
            assertThat(sheet.hasChanges()).isFalse();
            assertThat(saveButton(view).isEnabled()).isFalse();
        });
    }

    @Test
    void saveStoresTheEntryAndShowsTheSavedWeek() {
        systemAuthenticator.runWithUser(ADMIN, () -> {
            WeekSheetView view = open();
            WeekSheet sheet = sheet(view);

            browserChanges(sheet, change(task.getId().toString(), "1:30"));
            saveButton(view).click();

            assertThat(sheet.hasChanges()).isFalse();
            WeekSheetModel.Row row = sheet.getModel().rows().stream()
                    .filter(r -> r.key().equals(task.getId().toString()))
                    .findFirst().orElseThrow();
            assertThat(row.cells().get(0).text()).isEqualTo("1:30");
        });
        long saved = systemAuthenticator.withSystem(() -> dataManager.load(TimeEntry.class)
                .query("select e from ts_TimeEntry e where e.task.id = :task and e.date = :day")
                .parameter("task", task.getId())
                .parameter("day", MONDAY)
                .list().size());
        assertThat(saved).isEqualTo(1);
    }

    private WeekSheetView open() {
        viewNavigators.view(UiTestUtils.getCurrentView(), WeekSheetView.class)
                .withQueryParameters(QueryParameters.of(WeekSheetView.WEEK_PARAMETER, LINKED_DAY.toString()))
                .navigate();
        return UiTestUtils.getCurrentView();
    }

    private static WeekSheet sheet(WeekSheetView view) {
        return UiTestUtils.getComponent(view, "weekSheet");
    }

    private static JmixButton saveButton(WeekSheetView view) {
        return UiTestUtils.getComponent(view, "saveButton");
    }

    /** Одна изменённая ячейка в понедельник в том виде, в каком её шлёт week-sheet.ts: {row, day, text}. */
    private static ObjectNode change(String rowKey, String text) {
        ObjectNode change = JacksonUtils.createObjectNode();
        change.put("row", rowKey);
        change.put("day", MONDAY.toString());
        change.put("text", text);
        return change;
    }

    /**
     * Выстреливает week-sheet-change на сервере, как будто его прислал браузер: в данных события лежит значение
     * выражения, которое запросил компонент.
     */
    private static void browserChanges(WeekSheet sheet, ObjectNode... changes) {
        ArrayNode list = JacksonUtils.createArrayNode();
        for (ObjectNode change : changes) {
            list.add(change);
        }
        ObjectNode data = JacksonUtils.createObjectNode();
        data.set("event.detail.changes", list);
        sheet.getElement().getNode().getFeature(ElementListenerMap.class)
                .fireEvent(new DomEvent(sheet.getElement(), "week-sheet-change", data));
    }
}
