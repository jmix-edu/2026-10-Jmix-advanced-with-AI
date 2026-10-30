package com.company.timesheets.component;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Что показывает {@link WeekSheet}: колонки дней, строки и ячейку каждой строки на каждый день.
 * <p>
 * Простое дерево record'ов, намеренно без сущностей: Vaadin превращает его в JSON как есть
 * ({@code Element.setPropertyBean}), а браузерная часть читает его как простой объект. Компонент ничего не знает
 * о списаниях, модель из них строит экран (specs/02_ui_development/05-week-sheet.spec.adoc).
 *
 * @param rowHeader   заголовок первой колонки, той, где подписи строк
 * @param totalHeader заголовок последней колонки с итогом строки и строки итогов внизу
 * @param invalidText подсказка ячейки, текст которой не время; браузер проверяет формат, пока пользователь вводит
 * @param days        колонки дней слева направо
 * @param rows        строки сверху вниз
 */
public record WeekSheetModel(String rowHeader, String totalHeader, String invalidText, List<Day> days,
                             List<Row> rows) {

    /**
     * Колонка дня.
     *
     * @param key     стабильный идентификатор, который браузер возвращает вместе с изменением, например {@code 2026-10-26}
     * @param caption заголовок колонки, например {@code Mon 26.10}
     */
    public record Day(String key, String caption) {
    }

    /**
     * Строка таблицы.
     *
     * @param key     стабильный идентификатор, который браузер возвращает вместе с изменением, например id задачи
     * @param caption текст первой колонки
     * @param cells   по ячейке на день, в порядке {@link WeekSheetModel#days()}
     */
    public record Row(String key, String caption, List<Cell> cells) {
    }

    /**
     * Ячейка: что она показывает и может ли пользователь её менять.
     *
     * @param text     значение, как его показывают и редактируют, например {@code 1:30}; пусто, если значения нет
     * @param editable даёт ли браузер пользователю вводить текст в ячейку
     * @param hint     подсказка ячейки, например почему она только для чтения; {@code null}, если подсказки нет
     */
    public record Cell(String text, boolean editable, @Nullable String hint) {
    }
}
