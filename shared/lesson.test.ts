import { describe, expect, it } from 'vitest';
import {
  buildInstruction,
  chooseRenderMode,
  createState,
  judge,
  parseLesson,
  reduceLesson,
  selectOptionValues,
  syncInstructionIfDefault,
  type Lesson,
  type LessonStep,
} from './lesson.ts';

function step(partial: Partial<LessonStep> & Pick<LessonStep, 'action' | 'hotspotId'>): LessonStep {
  return {
    id: 'step-1',
    index: 0,
    screenId: 's1',
    expectedValue: '',
    confirm: null,
    nextScreenId: 's2',
    instruction: 'На экране «Главная» нажмите «Найти».',
    hint: '',
    errorText: '',
    ...partial,
  };
}

function lessonWith(steps: LessonStep[]): Lesson {
  return parseLesson({
    schemaVersion: 1,
    id: 'demo',
    title: 'Демо',
    description: 'Проверка',
    sourceSite: 'https://example.test',
    viewport: { width: 1280, height: 800 },
    screens: [
      {
        id: 's1',
        title: 'Главная',
        url: 'https://example.test',
        html: 'screens/s1.html',
        screenshot: 'original/s1.png',
        viewport: { width: 1280, height: 800 },
        hotspots: [
          {
            id: 'field',
            kind: 'input',
            label: 'Поиск',
            selector: '#q',
            box: { x: 10, y: 10, width: 120, height: 32 },
          },
          {
            id: 'go',
            kind: 'button',
            label: 'Найти',
            selector: '#go',
            box: { x: 140, y: 10, width: 80, height: 32 },
          },
        ],
      },
      {
        id: 's2',
        title: 'Результат',
        url: 'https://example.test/r',
        html: 'screens/s2.html',
        screenshot: 'original/s2.png',
        viewport: { width: 1280, height: 800 },
        hotspots: [],
      },
    ],
    steps,
    meta: {
      generatedAt: '2026-10-30T00:00:00.000Z',
      generator: 'test',
      stepCount: steps.length,
      manuallyEditedSteps: 0,
      autoStepRatio: 1,
      fidelity: [],
      warnings: [],
    },
  });
}

describe('judge', () => {
  it('принимает клик по нужному hotspot', () => {
    const current = step({ action: 'click', hotspotId: 'go' });
    expect(judge(current, { type: 'click', hotspotId: 'go' })).toBe('correct');
    expect(judge(current, { type: 'click', hotspotId: 'field' })).toBe('wrong-target');
  });

  it('не считает промежуточный ввод ошибкой и принимает точное значение', () => {
    const current = step({
      action: 'input',
      hotspotId: 'field',
      expectedValue: 'закупка',
      confirm: 'match',
    });
    expect(judge(current, { type: 'input', hotspotId: 'field', value: 'зак', confirm: 'input' })).toBe('ignore');
    expect(judge(current, { type: 'input', hotspotId: 'field', value: 'закупка', confirm: 'input' })).toBe('correct');
  });

  it('для Enter ждёт подтверждения и ругает неверное значение', () => {
    const current = step({
      action: 'input',
      hotspotId: 'field',
      expectedValue: 'Купить молоко',
      confirm: 'enter',
    });
    expect(judge(current, { type: 'input', hotspotId: 'field', value: 'Купить молоко', confirm: 'input' })).toBe('ignore');
    expect(judge(current, { type: 'input', hotspotId: 'field', value: 'молоко', confirm: 'enter' })).toBe('wrong-value');
    expect(judge(current, { type: 'input', hotspotId: 'field', value: 'Купить молоко', confirm: 'enter' })).toBe('correct');
  });
});

describe('reduceLesson', () => {
  const lesson = lessonWith([
    step({ action: 'click', hotspotId: 'go', id: 'step-1' }),
    step({
      action: 'input',
      hotspotId: 'field',
      id: 'step-2',
      index: 1,
      screenId: 's2',
      expectedValue: '44-ФЗ',
      confirm: 'match',
      nextScreenId: null,
      instruction: 'На экране «Результат» введите «44-ФЗ» в поле «Поиск».',
    }),
  ]);

  it('показывает русскую ошибку и подсказку после двух промахов', () => {
    let state = createState('try');
    state = reduceLesson(lesson, state, { type: 'attempt', attempt: { type: 'click', hotspotId: 'field' } });
    expect(state.stepIndex).toBe(0);
    expect(state.error).toBe('Неверное действие. Посмотрите формулировку шага.');
    expect(state.hint).toBeNull();
    state = reduceLesson(lesson, state, { type: 'attempt', attempt: { type: 'click', hotspotId: 'field' } });
    expect(state.hint).toBe(lesson.steps[0]?.instruction);
    expect(state.mistakes).toBe(2);
  });

  it('в режиме проверки не показывает подсказку и ожидаемое значение', () => {
    let state = createState('test');
    state = reduceLesson(lesson, state, { type: 'attempt', attempt: { type: 'click', hotspotId: 'field' } });
    state = reduceLesson(lesson, state, { type: 'attempt', attempt: { type: 'click', hotspotId: 'field' } });
    expect(state.error).toBe('Неверно.');
    expect(state.hint).toBeNull();
  });

  it('доходит до конца и считает чистые первые попытки', () => {
    let state = createState('try');
    state = reduceLesson(lesson, state, { type: 'attempt', attempt: { type: 'click', hotspotId: 'go' } });
    expect(state.stepIndex).toBe(1);
    state = reduceLesson(lesson, state, {
      type: 'attempt',
      attempt: { type: 'input', hotspotId: 'field', value: 'нет', confirm: 'enter' },
    });
    state = reduceLesson(lesson, state, {
      type: 'attempt',
      attempt: { type: 'input', hotspotId: 'field', value: '44-ФЗ', confirm: 'input' },
    });
    expect(state.status).toBe('done');
    expect(state.firstTryCorrect).toBe(1);
    expect(state.mistakes).toBe(1);
  });
});

describe('syncInstructionIfDefault', () => {
  it('пересобирает инструкцию, если её не правили вручную', () => {
    const current = step({
      action: 'input',
      hotspotId: 'field',
      expectedValue: 'старое',
      confirm: 'match',
      nextScreenId: null,
      instruction: buildInstruction({
        screenTitle: 'Главная',
        action: 'input',
        label: 'Поиск',
        value: 'старое',
        nextTitle: null,
        confirm: 'match',
      }),
    });
    const lesson = lessonWith([current]);
    const result = syncInstructionIfDefault(lesson, current, 'новое');
    expect(result.instructionLocked).toBe(false);
    expect(result.instruction).toContain('«новое»');
  });

  it('не трогает инструкцию, если автор менял текст', () => {
    const current = step({
      action: 'input',
      hotspotId: 'field',
      expectedValue: 'старое',
      confirm: 'match',
      nextScreenId: null,
      instruction: 'Свой текст',
    });
    const result = syncInstructionIfDefault(lessonWith([current]), current, 'новое');
    expect(result.instructionLocked).toBe(true);
    expect(result.instruction).toBe('Свой текст');
  });

  it('читает опции select из html', () => {
    expect(selectOptionValues('<select id="kind"><option value="a">А</option><option>б</option></select>', '#kind'))
      .toEqual(['a', 'б']);
  });
});

describe('buildInstruction', () => {
  it('собирает русскую формулировку шага', () => {
    expect(
      buildInstruction({
        screenTitle: 'Главная',
        action: 'input',
        label: 'Искать в Википедии',
        value: 'извещение о закупке',
        nextTitle: null,
        confirm: 'match',
      }),
    ).toBe('На экране «Главная» введите «извещение о закупке» в поле «Искать в Википедии».');
  });
});

describe('chooseRenderMode', () => {
  it('оставляет разметку, если повтор похож на оригинал', () => {
    expect(chooseRenderMode('auto', [0.99, 0.97])).toBe('dom');
  });

  it('переключается на скриншот, если повтор сильно расходится', () => {
    expect(chooseRenderMode('auto', [0.4, 0.5])).toBe('screenshot');
    expect(chooseRenderMode('screenshot', [0.99])).toBe('screenshot');
  });
});

describe('parseLesson', () => {
  it('отклоняет чужую версию схемы', () => {
    expect(() => parseLesson({ schemaVersion: 2 })).toThrow(/версия/);
  });
});
