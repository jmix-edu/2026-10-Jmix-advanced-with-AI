package com.company.timesheets.component;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dependency.NpmPackage;
import com.vaadin.flow.dom.DomEvent;
import com.vaadin.flow.shared.Registration;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Недельная таблица в духе электронной таблицы поверх JS-библиотеки RevoGrid: строки, по колонке на день, текстовое
 * значение в каждой ячейке. Пользователь вводит, вставляет диапазоны из Excel и перетаскивает значения по ячейкам;
 * компонент копит эти изменения на сервере, пока экран их не сохранит или не отменит.
 * <p>
 * Как части связаны между собой (подробный разбор — в docs/revogrid-integration.adoc):
 * <ul>
 *     <li>{@link Tag} — пользовательский элемент (custom element), который браузер создаёт для этого компонента. Он
 *     определён в JS-модуле ниже, а не в RevoGrid: наш элемент оборачивает {@code <revo-grid>}.</li>
 *     <li>{@link NpmPackage} — Vaadin добавляет библиотеку во фронтенд-сборку (package.json, бандл Vite).
 *     Версия точная: RevoGrid выпускает релизы каждую неделю. Второй пакет — рантайм Stencil, который нужен RevoGrid,
 *     но не объявлен в нём.</li>
 *     <li>{@link JsModule} — наш TypeScript-модуль в {@code src/main/frontend}; он регистрирует
 *     {@code <ts-week-sheet>} и превращает модель в колонки и строки RevoGrid.</li>
 * </ul>
 * С сервера в браузер: {@link #setModel} кладёт всю модель в свойство элемента {@code model}.
 * Из браузера на сервер: элемент шлёт {@code week-sheet-change} с изменёнными ячейками, одно событие на правку,
 * вставку или перетаскивание, а не на каждую ячейку.
 */
@Tag("ts-week-sheet")
@NpmPackage(value = "@revolist/revogrid", version = "4.29.1")
// Standalone-сборка RevoGrid импортирует рантайм Stencil (@stencil/core/internal/client), но не объявляет его
// зависимостью: без этой строки Vite падает с "Rollup failed to resolve import". 4.43.5 — версия, с которой собран
// RevoGrid 4.29.1 (его devDependencies).
@NpmPackage(value = "@stencil/core", version = "4.43.5")
@JsModule("./src/components/week-sheet.ts")
public class WeekSheet extends Component implements HasSize, HasStyle {

    /**
     * DOM-событие, которое шлёт JS-модуль; имя должно совпадать с именем в week-sheet.ts.
     */
    private static final String CHANGE_EVENT = "week-sheet-change";
    /**
     * Выражение, которое браузер вычисляет при срабатывании события; его значение приходит на сервер вместе с событием.
     */
    private static final String CHANGES_DATA = "event.detail.changes";

    @Nullable
    private WeekSheetModel model;
    /**
     * Ещё не сохранённые изменения по ячейкам. У ячейки, изменённой дважды, остаётся только последний текст; ячейка,
     * которой вернули исходный текст, вовсе не считается изменением.
     */
    private final Map<CellKey, String> changes = new LinkedHashMap<>();

    public WeekSheet() {
        // Без addEventData сервер получил бы событие без данных: Vaadin шлёт только то, что запрошено.
        getElement().addEventListener(CHANGE_EVENT, this::onClientChange)
                .addEventData(CHANGES_DATA);
    }

    /**
     * Показывает новую модель и забывает несохранённые изменения и ошибки ячеек. Нужен, чтобы загрузить неделю, после
     * сохранения (значения сервера заменяют введённое) и чтобы отменить изменения.
     */
    public void setModel(WeekSheetModel model) {
        this.model = model;
        changes.clear();
        // Vaadin сериализует record'ы через Jackson; браузер получает простой объект в свойстве "model".
        getElement().setPropertyBean("model", model);
        showErrors(List.of());
        fireEvent(new ChangeEvent(this, false));
    }

    @Nullable
    public WeekSheetModel getModel() {
        return model;
    }

    /**
     * Менял ли пользователь ячейки после последнего {@link #setModel}.
     */
    public boolean hasChanges() {
        return !changes.isEmpty();
    }

    /**
     * Несохранённые изменения в том порядке, в котором они впервые сделаны.
     */
    public List<CellChange> getChanges() {
        List<CellChange> result = new ArrayList<>();
        changes.forEach((key, text) -> result.add(new CellChange(key.rowKey(), key.dayKey(), text)));
        return result;
    }

    /**
     * Отмечает ячейки как ошибочные, например значение, которое не время, с сообщением в подсказке. Текст пользователя
     * остаётся в ячейке, чтобы его можно было исправить. Пустой список снимает все отметки.
     */
    public void showErrors(List<CellError> errors) {
        getElement().setPropertyList("errors", errors);
    }

    /**
     * Таблица только для чтения: ничего нельзя ввести, вставить или перетащить.
     */
    public void setReadOnly(boolean readOnly) {
        getElement().setProperty("readOnly", readOnly);
    }

    public boolean isReadOnly() {
        return getElement().getProperty("readOnly", false);
    }

    /**
     * Срабатывает после каждой пачки изменений из браузера и после {@link #setModel}, чтобы экран мог включать
     * и выключать свои кнопки Save и Discard.
     */
    public Registration addChangeListener(ComponentEventListener<ChangeEvent> listener) {
        return addListener(ChangeEvent.class, listener);
    }

    /**
     * Браузер шлёт {@code [{row, day, text}, ...]}. Ячейки, которых нет в модели, игнорируются: данные приходят
     * из браузера и могут быть какими угодно. Можно ли на самом деле менять ячейку, сервис ещё раз проверяет при
     * сохранении — компонент только собирает изменения.
     */
    private void onClientChange(DomEvent event) {
        if (model == null) {
            return;
        }
        JsonNode list = event.getEventData().get(CHANGES_DATA);
        if (list == null || !list.isArray()) {
            return;
        }
        for (JsonNode item : list.values()) {
            String rowKey = item.path("row").asString("");
            String dayKey = item.path("day").asString("");
            String text = item.path("text").asString("").strip();
            originalText(rowKey, dayKey).ifPresent(original -> {
                CellKey key = new CellKey(rowKey, dayKey);
                if (original.equals(text)) {
                    changes.remove(key);
                } else {
                    changes.put(key, text);
                }
            });
        }
        fireEvent(new ChangeEvent(this, true));
    }

    /**
     * Текст, который был у ячейки в модели, или пусто, если такой ячейки в модели нет.
     */
    private Optional<String> originalText(String rowKey, String dayKey) {
        if (model == null) {
            return Optional.empty();
        }
        int dayIndex = -1;
        for (int i = 0; i < model.days().size(); i++) {
            if (model.days().get(i).key().equals(dayKey)) {
                dayIndex = i;
                break;
            }
        }
        if (dayIndex < 0) {
            return Optional.empty();
        }
        for (WeekSheetModel.Row row : model.rows()) {
            if (row.key().equals(rowKey)) {
                return Optional.of(row.cells().get(dayIndex).text());
            }
        }
        return Optional.empty();
    }

    private record CellKey(String rowKey, String dayKey) {
    }

    /**
     * Изменённая ячейка: ключи строки и дня из модели и введённый текст без пробелов по краям; пусто, если ячейку очистили.
     */
    public record CellChange(String rowKey, String dayKey, String text) {
    }

    /**
     * Ошибка, которую нужно показать на ячейке; см. {@link #showErrors}.
     */
    public record CellError(String rowKey, String dayKey, String message) {
    }

    /**
     * Несохранённые изменения появились, изменились или сброшены.
     */
    public static class ChangeEvent extends ComponentEvent<WeekSheet> {

        /**
         * @param fromClient пришло ли изменение от пользователя в браузере, а не из {@link #setModel}
         */
        public ChangeEvent(WeekSheet source, boolean fromClient) {
            super(source, fromClient);
        }

        public boolean hasChanges() {
            return getSource().hasChanges();
        }
    }
}
