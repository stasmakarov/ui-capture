export type ScenarioStep = {
  locator: string;
  kind: 'fill' | 'click' | 'press' | 'select';
  value?: string;
  key?: string;
  confirm?: 'match' | 'enter';
  opensScreen?: { title: string; waitFor?: string };
  prelude?: boolean;
};

export type Scenario = {
  id: string;
  title: string;
  description: string;
  sourceSite: string;
  startUrl: string;
  initialScreenTitle: string;
  steps: ScenarioStep[];
};

export const scenarios: Scenario[] = [
  {
    id: 'najti-statyu',
    title: 'Найти статью и открыть вкладку',
    description:
      'Аналог сценария «Найти извещение и открыть карточку» на публичном сайте: главная, поиск по ключевой фразе, список результатов, карточка статьи, вкладка «Обсуждение». Портал zakupki.gov.ru из этой среды недоступен — TLS-рукопожатие обрывается.',
    sourceSite: 'https://ru.wikipedia.org',
    startUrl: 'https://ru.wikipedia.org/wiki/Заглавная_страница',
    initialScreenTitle: 'Главная',
    steps: [
      {
        locator: '#searchInput',
        kind: 'fill',
        value: 'извещение о закупке',
        confirm: 'match',
      },
      {
        locator: 'button.cdx-search-input__end-button',
        kind: 'click',
        opensScreen: {
          title: 'Результаты поиска',
          waitFor: '.mw-search-results .mw-search-result-heading a',
        },
      },
      {
        locator: '.mw-search-results .mw-search-result-heading a:not(.new)',
        kind: 'click',
        opensScreen: {
          title: 'Карточка статьи',
          waitFor: '#ca-talk a',
        },
      },
      {
        locator: '#ca-talk a',
        kind: 'click',
        opensScreen: {
          title: 'Вкладка «Обсуждение»',
          waitFor: '#content',
        },
      },
    ],
  },
  {
    id: 'spisok-zadach',
    title: 'Добавить задачи и переключить фильтр',
    description:
      'Второй, другой сайт — SPA TodoMVC. Поле ввода, список и вкладки-фильтры. Конвейер тот же, что и для вики-сценария, без привязки к ЕИС.',
    sourceSite: 'https://demo.playwright.dev/todomvc/',
    startUrl: 'https://demo.playwright.dev/todomvc/',
    initialScreenTitle: 'Пустой список',
    steps: [
      {
        locator: '.new-todo',
        kind: 'fill',
        value: 'Купить молоко',
        confirm: 'enter',
        opensScreen: { title: 'Одна задача', waitFor: '.todo-list li' },
      },
      {
        locator: '.new-todo',
        kind: 'fill',
        value: 'Написать отчёт',
        confirm: 'enter',
        opensScreen: { title: 'Две задачи', waitFor: '.todo-list li:nth-child(2)' },
      },
      {
        locator: '.todo-list li .toggle',
        kind: 'click',
        opensScreen: { title: 'Первая задача выполнена', waitFor: '.todo-list li.completed' },
      },
      {
        locator: 'a[href="#/completed"]',
        kind: 'click',
        opensScreen: { title: 'Фильтр «Completed»', waitFor: 'a[href="#/completed"].selected' },
      },
    ],
  },
];

export function scenarioById(id: string | undefined): Scenario[] {
  if (!id) return scenarios;
  const found = scenarios.filter((item) => item.id === id);
  if (found.length === 0) {
    throw new Error(`Неизвестный сценарий «${id}». Доступны: ${scenarios.map((item) => item.id).join(', ')}`);
  }
  return found;
}
