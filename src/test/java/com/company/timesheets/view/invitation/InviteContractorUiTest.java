package com.company.timesheets.view.invitation;

import com.company.timesheets.TimesheetsApplication;
import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.ContactInformation;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectRole;
import com.company.timesheets.entity.ProjectRoleType;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.testassist.FlowuiTestAssistConfiguration;
import io.jmix.flowui.testassist.UiTest;
import io.jmix.flowui.testassist.UiTestUtils;
import io.jmix.flowui.view.DialogWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The invitation dialog loads and preselects a member role: specs/03_access_control/05-contractor-invitation.
 */
@UiTest
@SpringBootTest(classes = {TimesheetsApplication.class, FlowuiTestAssistConfiguration.class})
@ActiveProfiles("test")
class InviteContractorUiTest {

    @Autowired
    private DialogWindows dialogWindows;
    @Autowired
    private UnconstrainedDataManager dataManager;
    @Autowired
    private DataSource dataSource;

    private UUID clientId;
    private UUID roleId;

    @Test
    void theDialogOpensWithAMemberRoleSelected() {
        String prefix = "dlg-" + UUID.randomUUID().toString().substring(0, 8) + "-";
        Client client = dataManager.create(Client.class);
        client.setName(prefix + "client");
        client.setContactInformation(dataManager.create(ContactInformation.class));
        client = dataManager.save(client);
        clientId = client.getId();
        Project project = dataManager.create(Project.class);
        project.setName(prefix + "project");
        project.setClient(client);
        project = dataManager.save(project);
        ProjectRole member = dataManager.create(ProjectRole.class);
        member.setName("AAA " + prefix + "member");
        member.setType(ProjectRoleType.MEMBER);
        member = dataManager.save(member);
        roleId = member.getId();

        DialogWindow<InviteContractorView> dialog =
                dialogWindows.view(UiTestUtils.getCurrentView(), InviteContractorView.class).build();
        dialog.getView().setProject(project);
        dialog.open();

        EntityComboBox<ProjectRole> roleField = UiTestUtils.getComponent(dialog.getView(), "projectRoleField");
        assertThat(roleField.getValue()).isNotNull();
        assertThat(roleField.getValue().getType()).isEqualTo(ProjectRoleType.MEMBER);
    }

    @AfterEach
    void tearDown() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        if (clientId != null) {
            jdbc.update("delete from TS_PROJECT where CLIENT_ID = ?", clientId);
            jdbc.update("delete from TS_CLIENT where ID = ?", clientId);
        }
        if (roleId != null) {
            jdbc.update("delete from TS_PROJECT_ROLE where ID = ?", roleId);
        }
    }
}
