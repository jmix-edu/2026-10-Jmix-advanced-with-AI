package com.company.timesheets.exception;

import com.company.timesheets.entity.Client;
import com.company.timesheets.entity.ContactInformation;
import com.company.timesheets.entity.Project;
import com.company.timesheets.test_support.AuthenticatedAsAdmin;
import io.jmix.core.DataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Two managers save the same project, from specs/01_data_manipulation/08-queries-locking.spec.adoc: the second
 * save is rejected and the message names who saved first.
 */
@SpringBootTest
@ExtendWith(AuthenticatedAsAdmin.class)
@ActiveProfiles("test")
class ProjectOptimisticLockTest {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private OptimisticLockUiHandler handler;
    @Autowired
    private DataSource dataSource;

    private UUID clientId;

    @Test
    void secondSaveOfAStaleProjectIsRejectedAndNamesWhoChangedIt() {
        Project project = project();
        Project managerA = reload(project);
        Project managerB = reload(project);

        managerA.setDescription("Saved by A");
        dataManager.saveWithoutReload(managerA);
        managerB.setDescription("Saved by B");
        Throwable thrown = catchThrowable(() -> dataManager.saveWithoutReload(managerB));

        assertThat(causes(thrown)).anyMatch(cause -> cause instanceof jakarta.persistence.OptimisticLockException
                || cause instanceof org.eclipse.persistence.exceptions.OptimisticLockException);
        assertThat(reload(project).getDescription()).isEqualTo("Saved by A");
        assertThat(handler.describe(thrown)).hasValueSatisfying(text -> assertThat(text)
                .contains(project.getName())
                .contains("admin"));
    }

    private static List<Throwable> causes(Throwable thrown) {
        List<Throwable> causes = new ArrayList<>();
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            causes.add(cause);
        }
        return causes;
    }

    private Project reload(Project project) {
        return dataManager.load(Project.class).id(project.getId()).one();
    }

    private Project project() {
        Client client = dataManager.create(Client.class);
        client.setName("Client " + UUID.randomUUID());
        client.setContactInformation(dataManager.create(ContactInformation.class));
        client = dataManager.save(client);
        clientId = client.getId();

        Project project = dataManager.create(Project.class);
        project.setName("Project " + UUID.randomUUID());
        project.setClient(client);
        return dataManager.save(project);
    }

    @AfterEach
    void tearDown() {
        // Plain SQL: soft delete would leave rows in the file-based test database.
        if (clientId != null) {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("delete from TS_PROJECT where CLIENT_ID = ?", clientId);
            jdbc.update("delete from TS_CLIENT where ID = ?", clientId);
        }
    }
}
