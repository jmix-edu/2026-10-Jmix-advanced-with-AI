package com.company.timesheets.view.projectcards;

import com.company.timesheets.datatype.SpentTimeDatatype;
import com.company.timesheets.entity.Project;
import com.company.timesheets.entity.ProjectStatus;
import com.company.timesheets.entity.ProjectSummary;
import com.company.timesheets.service.HoursReportService;
import com.company.timesheets.view.main.MainView;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.card.CardVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import io.jmix.core.LoadContext;
import io.jmix.core.MetadataTools;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * Проекты в виде карточек: клиент, статус, часы за текущий месяц, участники.
 * См. specs/02_ui_development/03-project-cards.spec.adoc.
 */
@Route(value = "project-cards", layout = MainView.class)
@ViewController(id = "Projects.cards")
@ViewDescriptor(path = "project-cards-view.xml")
public class ProjectCardsView extends StandardView {

    private static final SpentTimeDatatype SPENT_TIME_FORMAT = new SpentTimeDatatype();

    @ViewComponent
    private Div cardsContainer;
    @ViewComponent
    private CollectionContainer<ProjectSummary> projectSummariesDc;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private HoursReportService hoursReportService;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private MetadataTools metadataTools;

    @Install(to = "projectSummariesDl", target = Target.DATA_LOADER)
    private List<ProjectSummary> projectSummariesDlLoadDelegate(final LoadContext<ProjectSummary> loadContext) {
        return hoursReportService.loadProjectSummaries();
    }

    @Subscribe(id = "projectSummariesDc", target = Target.DATA_CONTAINER)
    public void onProjectSummariesDcCollectionChange(
            final CollectionContainer.CollectionChangeEvent<ProjectSummary> event) {
        cardsContainer.removeAll();
        projectSummariesDc.getItems().forEach(summary -> cardsContainer.add(createCard(summary)));
    }

    private Card createCard(ProjectSummary summary) {
        Card card = uiComponents.create(Card.class);
        card.addClassName("project-card");
        card.addThemeVariants(CardVariant.OUTLINED);
        card.setTitle(summary.getProject().getName());
        card.setSubtitle(summary.getClient() == null ? "" : metadataTools.getInstanceName(summary.getClient()));
        card.setHeaderSuffix(statusBadge(summary.getStatus()));
        // Строки кладём в один блок: карточка не располагает содержимое столбиком, и inline-span слиплись бы.
        Div details = uiComponents.create(Div.class);
        details.addClassName("project-card-details");
        details.add(line(messageBundle.formatMessage("monthHours",
                        SPENT_TIME_FORMAT.format(summary.getCurrentMonthTime()))),
                line(messageBundle.formatMessage("participants", summary.getParticipantCount())));
        card.add(details);

        // Карточка — не кнопка: делаем её фокусируемой и открываем проект по клику и по Enter.
        Project project = summary.getProject();
        card.getElement().setAttribute("tabindex", "0");
        card.getElement().addEventListener("click", event -> openProject(project));
        card.getElement().addEventListener("keydown", event -> openProject(project))
                .setFilter("event.key === 'Enter'");
        return card;
    }

    private Span statusBadge(ProjectStatus status) {
        Span badge = uiComponents.create(Span.class);
        badge.getElement().getThemeList().add("badge");
        badge.addClassName(status == ProjectStatus.OPEN ? "project-status-open" : "project-status-closed");
        badge.setText(metadataTools.format(status));
        return badge;
    }

    private Span line(String text) {
        Span span = uiComponents.create(Span.class);
        span.setText(text);
        return span;
    }

    private void openProject(Project project) {
        viewNavigators.detailView(this, Project.class)
                .editEntity(project)
                .withBackwardNavigation(true)
                .navigate();
    }
}
