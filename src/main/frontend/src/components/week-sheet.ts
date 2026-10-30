/*
 * <ts-week-sheet>: браузерная половина com.company.timesheets.component.WeekSheet.
 *
 * Данные хранит Java-компонент; этот элемент только показывает их в RevoGrid и сообщает, что изменил пользователь.
 *   сервер → браузер: свойства `model`, `errors` и `readOnly`, их выставляет Vaadin из Java;
 *   браузер → сервер: DOM-событие `week-sheet-change` с изменёнными ячейками, его слушает Java.
 *
 * Обычный пользовательский элемент (custom element), не Lit: всё рисует RevoGrid, своего шаблона у нас нет.
 * Подробный разбор — в docs/revogrid-integration.adoc.
 */
// Регистрирует <revo-grid> и элементы, из которых он состоит. Standalone-сборка рассчитана на бандлеры
// вроде Vite: в бандл попадает только то, что импортировано, без ленивой загрузки с CDN.
import { defineCustomElement as defineRevoGrid } from '@revolist/revogrid/standalone/revo-grid.js';
// Только типы: при сборке они стираются и лишь дают редактору проверить API RevoGrid.
import type {
  BeforeRangeSaveDataDetails,
  BeforeSaveDataDetails,
  CellProps,
  ColumnDataSchemaModel,
  ColumnRegular,
  DataType,
} from '@revolist/revogrid';

defineRevoGrid();

// --- Модель в том виде, в каком её шлёт Java: WeekSheetModel, сериализованный Vaadin (компоненты record становятся полями). ---

interface Day {
  key: string;
  caption: string;
}

interface Cell {
  text: string;
  editable: boolean;
  hint: string | null;
}

interface Row {
  key: string;
  caption: string;
  cells: Cell[];
}

interface Model {
  rowHeader: string;
  totalHeader: string;
  /** Подсказка ячейки, в которой текст — не время. */
  invalidText: string;
  days: Day[];
  rows: Row[];
}

/** WeekSheet.CellError. */
interface CellError {
  rowKey: string;
  dayKey: string;
  message: string;
}

/** Что уходит на сервер по одной ячейке; читается в WeekSheet.onClientChange. */
interface CellChange {
  row: string;
  day: string;
  text: string;
}

/** Имя события, которое слушает WeekSheet; держать в синхроне с WeekSheet.CHANGE_EVENT. */
const CHANGE_EVENT = 'week-sheet-change';
/** Колонка с подписями строк. Колонки дней используют ключи дней как свои props. */
const CAPTION_PROP = '__caption';
/** Обычные строки данных; строка итогов закреплена внизу и имеет другой тип. */
const DATA_ROWS = 'rgRow';

/** Тот же формат, что у SpentTimeDatatype на сервере: часы, затем необязательно ":" и минуты до 59. */
const TIME_PATTERN = /^(\d+)(?::(\d{1,2}))?$/;

/** Минуты из текста ячейки для итогов или null, если текст пуст или не время. По-настоящему разбирает сервер. */
function minutesOf(text: unknown): number | null {
  const match = TIME_PATTERN.exec(String(text ?? '').trim());
  if (!match) {
    return null;
  }
  const minutes = match[2] === undefined ? 0 : Number(match[2]);
  return minutes > 59 ? null : Number(match[1]) * 60 + minutes;
}

function formatMinutes(minutes: number): string {
  return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}`;
}

/**
 * Во что превращается введённый или вставленный текст в ячейке: время в формате сервера (`1` -> `1:00`, `0:5` -> `0:05`),
 * пусто для нуля (на ноль сервер не хранит списание) или текст как есть, если это не время, — чтобы пользователь
 * увидел отметку и исправил его.
 */
function normalize(value: unknown): string {
  const text = String(value ?? '').trim();
  const minutes = minutesOf(text);
  if (minutes === null) {
    return text;
  }
  return minutes === 0 ? '' : formatMinutes(minutes);
}

/** Текст, который не пуст и не время: браузер отмечает его сразу, сервер отклоняет при сохранении. */
function isInvalid(value: unknown): boolean {
  const text = String(value ?? '').trim();
  return text !== '' && minutesOf(text) === null;
}

/**
 * Отличает правку диапазона (вставка, автозаполнение) от правки одной ячейки. Не по `data`: у одной ячейки поле `data`
 * тоже есть (все строки), а `newRange` есть только у диапазона.
 */
function isRangeEdit(detail: BeforeSaveDataDetails | BeforeRangeSaveDataDetails): detail is BeforeRangeSaveDataDetails {
  return 'newRange' in detail;
}

class WeekSheetElement extends HTMLElement {
  private grid?: HTMLRevoGridElement;
  private themeObserver?: MutationObserver;

  private _model?: Model;
  private _errors: CellError[] = [];
  private _readOnly = false;
  /** Текущие тексты ячеек по индексу строки и ключу дня, как их видит пользователь; по ним считаются итоги. */
  private values: Array<Record<string, string>> = [];

  // --- Свойства, которые выставляет Vaadin. Vaadin присваивает их как JS-свойства, поэтому хватает простых сеттеров. ---

  set model(model: Model | undefined) {
    this._model = model;
    this.render();
  }

  get model(): Model | undefined {
    return this._model;
  }

  set errors(errors: CellError[] | undefined) {
    this._errors = errors ?? [];
    // cellProperties читает ошибки; новые объекты колонок заставляют RevoGrid перерисовать ячейки.
    if (this.grid && this._model) {
      this.grid.columns = this.columns(this._model);
    }
  }

  get errors(): CellError[] {
    return this._errors;
  }

  set readOnly(readOnly: boolean) {
    this._readOnly = !!readOnly;
    if (this.grid) {
      this.grid.readonly = this._readOnly;
    }
  }

  get readOnly(): boolean {
    return this._readOnly;
  }

  // --- Жизненный цикл элемента. ---

  /**
   * Вызывается, когда Vaadin вставляет элемент в страницу, и снова при его перемещении. Таблица создаётся один раз
   * и остаётся дочерним элементом, поэтому после повторного подключения элемент показывает ту же таблицу.
   */
  connectedCallback(): void {
    if (!this.grid) {
      const grid = document.createElement('revo-grid');
      grid.range = true; // выделение диапазонов мышью и Shift, перетаскивание уголка для заполнения (autofill)
      grid.useClipboard = true; // копирование и вставка диапазонов, в том числе из Excel и Google Sheets
      grid.resize = true; // ширину колонок можно менять
      grid.readonly = this._readOnly;
      grid.addEventListener('beforeedit', (e) => this.onBeforeEdit(e as CustomEvent<BeforeSaveDataDetails>));
      grid.addEventListener('beforerangeedit', (e) => this.onBeforeRangeEdit(e as CustomEvent<BeforeRangeSaveDataDetails>));
      grid.addEventListener('afteredit', (e) => this.onAfterEdit(e as CustomEvent<BeforeSaveDataDetails | BeforeRangeSaveDataDetails>));
      this.appendChild(grid);
      this.grid = grid;
    }
    this.applyTheme();
    // Приложение переключает светлую и тёмную тему атрибутом "theme" у <html> (color-scheme-switching-support.js).
    this.themeObserver = new MutationObserver(() => this.applyTheme());
    this.themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['theme'] });
    this.render();
  }

  /** Вызывается при закрытии экрана: ничто не должно продолжать слушать страницу. Таблица уходит вместе с элементом. */
  disconnectedCallback(): void {
    this.themeObserver?.disconnect();
    this.themeObserver = undefined;
  }

  // --- Отрисовка. ---

  private render(): void {
    const grid = this.grid;
    const model = this._model;
    if (!grid || !model) {
      return;
    }
    // Новая модель заменяет введённое: сервер загрузил неделю, сохранил её или отменил изменения.
    this.values = model.rows.map((row) => {
      const values: Record<string, string> = {};
      model.days.forEach((day, i) => (values[day.key] = row.cells[i]?.text ?? ''));
      return values;
    });
    grid.columns = this.columns(model);
    grid.source = model.rows.map((row, i) => ({ [CAPTION_PROP]: row.caption, ...this.values[i] }));
    this.updateTotals();
  }

  /** Закреплённая слева колонка подписей и по колонке на каждый день. */
  private columns(model: Model): ColumnRegular[] {
    const caption: ColumnRegular = {
      prop: CAPTION_PROP,
      name: model.rowHeader,
      pin: 'colPinStart',
      size: 280,
      readonly: true,
      cellProperties: () => ({ class: 'ts-week-sheet__caption' }),
    };
    const days: ColumnRegular[] = model.days.map((day, dayIndex) => ({
      prop: day.key,
      name: day.caption,
      size: 96,
      // Функция, которую RevoGrid вызывает для каждой ячейки: поэтому элемент наш, а не голый <revo-grid>,
      // которым управляет Java, — функции нельзя передать с сервера.
      readonly: (p: ColumnDataSchemaModel) => !this.isEditable(p.type, p.rowIndex, dayIndex),
      cellProperties: (p: ColumnDataSchemaModel) => this.cellProperties(p.type, p.rowIndex, dayIndex, p.model[day.key]),
    }));
    return [caption, ...days];
  }

  /**
   * Класс и подсказка ячейки дня: ячейки только для чтения затенены; ячейка отмечается, если её текст — не время
   * (проверяется здесь, пока пользователь вводит) или если сервер отклонил её при сохранении. `value` — текущий текст
   * ячейки в таблице, поэтому отметка следует за каждой правкой без лишнего учёта.
   */
  private cellProperties(type: string, rowIndex: number, dayIndex: number, value: unknown): CellProps {
    const model = this._model;
    if (!model || type !== DATA_ROWS) {
      return { class: 'ts-week-sheet__total' };
    }
    const row = model.rows[rowIndex];
    const cell = row?.cells[dayIndex];
    const error = this._errors.find((e) => e.rowKey === row?.key && e.dayKey === model.days[dayIndex].key);
    const invalid = isInvalid(value);
    const classes = ['ts-week-sheet__day'];
    if (!cell?.editable) {
      classes.push('ts-week-sheet__locked');
    }
    if (error || invalid) {
      classes.push('ts-week-sheet__error');
    }
    const title = error?.message ?? (invalid ? model.invalidText : cell?.hint);
    return { class: classes.join(' '), title: title ?? '' };
  }

  private isEditable(type: string, rowIndex: number, dayIndex: number): boolean {
    if (this._readOnly || type !== DATA_ROWS) {
      return false;
    }
    return this._model?.rows[rowIndex]?.cells[dayIndex]?.editable ?? false;
  }

  /** Закреплённая внизу строка итогов: по дням и, в ячейке подписи, за неделю. Пересчитывается при каждой правке. */
  private updateTotals(): void {
    const grid = this.grid;
    const model = this._model;
    if (!grid || !model) {
      return;
    }
    const totals: DataType = {};
    let week = 0;
    for (const day of model.days) {
      const minutes = this.values.reduce((sum, row) => sum + (minutesOf(row[day.key]) ?? 0), 0);
      totals[day.key] = formatMinutes(minutes);
      week += minutes;
    }
    totals[CAPTION_PROP] = `${model.totalHeader}: ${formatMinutes(week)}`;
    // Новый массив, а не мутация: RevoGrid перерисовывает закреплённую строку, когда свойство присваивают заново.
    grid.pinnedBottomSource = [totals];
  }

  // --- Правки пользователя. ---

  /** Одна введённая ячейка: нормализуем текст до того, как RevoGrid его сохранит, чтобы `1` показывалось и уходило как `1:00`. */
  private onBeforeEdit(event: CustomEvent<BeforeSaveDataDetails>): void {
    if (event.detail.type === DATA_ROWS) {
      event.detail.val = normalize(event.detail.val);
    }
  }

  /**
   * Вставка и автозаполнение пишут сразу целый диапазон. Ввод защищает функция readonly для каждой ячейки; из диапазона
   * заблокированные ячейки убираются здесь, до того как RevoGrid его применит. Сервер всё равно проверяет ещё раз.
   */
  private onBeforeRangeEdit(event: CustomEvent<BeforeRangeSaveDataDetails>): void {
    const model = this._model;
    if (!model || event.detail.type !== DATA_ROWS) {
      event.preventDefault();
      return;
    }
    for (const [rowIndex, values] of Object.entries(event.detail.data)) {
      for (const prop of Object.keys(values)) {
        const dayIndex = model.days.findIndex((day) => day.key === prop);
        if (dayIndex < 0 || !this.isEditable(DATA_ROWS, Number(rowIndex), dayIndex)) {
          delete (values as Record<string, unknown>)[prop];
        } else {
          // Та же нормализация, что и для введённой ячейки: вставленное `1` становится `1:00`.
          (values as Record<string, unknown>)[prop] = normalize(values[prop]);
        }
      }
    }
  }

  /**
   * После того как ячейка или диапазон применены в таблице: запоминаем новые тексты, пересчитываем итоги и шлём все
   * изменённые ячейки на сервер одним событием. Вставка блока 3x5 — один запрос к серверу, а не пятнадцать.
   */
  private onAfterEdit(event: CustomEvent<BeforeSaveDataDetails | BeforeRangeSaveDataDetails>): void {
    const model = this._model;
    if (!model) {
      return;
    }
    const detail = event.detail;
    const changes: CellChange[] = [];
    const errorCount = this._errors.length;
    const take = (rowIndex: number, prop: string, value: unknown) => {
      const dayIndex = model.days.findIndex((day) => day.key === prop);
      const row = model.rows[rowIndex];
      if (dayIndex < 0 || !row) {
        return;
      }
      const text = String(value ?? '').trim();
      this.values[rowIndex][prop] = text;
      changes.push({ row: row.key, day: prop, text });
      // Ошибка сервера относилась к старому тексту: пользователь его изменил, отметка снимается. Следующее сохранение проверит снова.
      this._errors = this._errors.filter((e) => e.rowKey !== row.key || e.dayKey !== prop);
    };

    if (isRangeEdit(detail)) {
      // Диапазон: вставка или автозаполнение. Ключи `data` — индексы строк, значения содержат изменённые props.
      if (detail.type !== DATA_ROWS) {
        return;
      }
      for (const [rowIndex, values] of Object.entries(detail.data)) {
        for (const [prop, value] of Object.entries(values ?? {})) {
          take(Number(rowIndex), prop, value);
        }
      }
    } else {
      // Одна ячейка, введённая пользователем.
      if (detail.type !== DATA_ROWS) {
        return;
      }
      take(detail.rowIndex, String(detail.prop), detail.val);
    }

    if (this._errors.length !== errorCount) {
      // cellProperties читает ошибки; новые объекты колонок заставляют RevoGrid перерисовать ячейки.
      this.grid!.columns = this.columns(model);
    }
    if (changes.length > 0) {
      this.updateTotals();
      this.dispatchEvent(new CustomEvent(CHANGE_EVENT, { detail: { changes } }));
    }
  }

  private applyTheme(): void {
    if (this.grid) {
      const dark = document.documentElement.getAttribute('theme') === 'dark';
      // Встроенные темы RevoGrid; компактные строки подходят для табеля.
      this.grid.theme = dark ? 'darkCompact' : 'compact';
    }
  }
}

// Защита от повторного определения, например после hot reload в режиме разработки.
if (!customElements.get('ts-week-sheet')) {
  customElements.define('ts-week-sheet', WeekSheetElement);
}
