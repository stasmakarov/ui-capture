export const LESSON_SCHEMA_VERSION = 1 as const;

export type StepAction = 'input' | 'click' | 'select' | 'key';
export type ConfirmKind = 'match' | 'enter';
export type HotspotKind =
  | 'input'
  | 'textarea'
  | 'select'
  | 'button'
  | 'link'
  | 'tab'
  | 'clickable';
export type Mode = 'show' | 'try' | 'test';
export type RenderMode = 'dom' | 'screenshot';

export type Box = {
  x: number;
  y: number;
  width: number;
  height: number;
};

export type Hotspot = {
  id: string;
  kind: HotspotKind;
  label: string;
  selector: string;
  box: Box;
  inputType?: string;
  placeholder?: string;
};

export type LessonScreen = {
  id: string;
  title: string;
  url: string;
  html: string;
  screenshot: string;
  hotspots: Hotspot[];
  viewport: { width: number; height: number };
};

export type LessonStep = {
  id: string;
  index: number;
  screenId: string;
  action: StepAction;
  hotspotId: string;
  expectedValue: string;
  confirm: ConfirmKind | null;
  nextScreenId: string | null;
  instruction: string;
  hint: string;
  errorText: string;
};

export type ScreenFidelity = {
  screenId: string;
  chosen: 'rrweb' | 'freeze';
  rrwebSimilarity: number | null;
  freezeSimilarity: number | null;
  chosenSimilarity: number;
};

export type LessonMeta = {
  generatedAt: string;
  generator: string;
  stepCount: number;
  manuallyEditedSteps: number;
  autoStepRatio: number;
  fidelity: ScreenFidelity[];
  warnings: string[];
};

export type Lesson = {
  schemaVersion: typeof LESSON_SCHEMA_VERSION;
  id: string;
  title: string;
  description: string;
  sourceSite: string;
  viewport: { width: number; height: number };
  screens: LessonScreen[];
  steps: LessonStep[];
  meta: LessonMeta;
};

export type Attempt =
  | { type: 'click'; hotspotId: string }
  | { type: 'input'; hotspotId: string; value: string; confirm: 'enter' | 'input' }
  | { type: 'select'; hotspotId: string; value: string };

export type Verdict = 'correct' | 'wrong-target' | 'wrong-value' | 'ignore';

export type RuntimeState = {
  mode: Mode;
  stepIndex: number;
  wrongOnStep: number;
  mistakes: number;
  firstTryCorrect: number;
  status: 'play' | 'done';
  error: string | null;
  hint: string | null;
};

export type RuntimeEvent =
  | { type: 'attempt'; attempt: Attempt }
  | { type: 'show-advance' };

const HOTSPOT_KINDS = new Set<HotspotKind>([
  'input',
  'textarea',
  'select',
  'button',
  'link',
  'tab',
  'clickable',
]);
const STEP_ACTIONS = new Set<StepAction>(['input', 'click', 'select', 'key']);

export function normalizeText(value: string): string {
  return value.replace(/\u00a0/g, ' ').replace(/\s+/g, ' ').trim();
}

export function buildInstruction(input: {
  screenTitle: string;
  action: StepAction;
  label: string;
  value: string;
  nextTitle: string | null;
  confirm: ConfirmKind | null;
}): string {
  const place = `На экране «${input.screenTitle}»`;
  const next = input.nextTitle ? ` Откроется экран «${input.nextTitle}».` : '';
  if (input.action === 'input') {
    const how = input.confirm === 'enter' ? ' и нажмите Enter' : '';
    return `${place} введите «${input.value}» в поле «${input.label}»${how}.${next}`;
  }
  if (input.action === 'select') {
    return `${place} выберите «${input.value}» в списке «${input.label}».${next}`;
  }
  if (input.action === 'key') {
    return `${place} нажмите клавишу ${input.value} в поле «${input.label}».${next}`;
  }
  return `${place} нажмите «${input.label}».${next}`;
}

export function judge(step: LessonStep, attempt: Attempt): Verdict {
  if (attempt.hotspotId !== step.hotspotId) {
    if (attempt.type === 'input' && attempt.confirm === 'input') return 'ignore';
    return 'wrong-target';
  }
  if (step.action === 'click') {
    return attempt.type === 'click' ? 'correct' : 'wrong-target';
  }
  if (step.action === 'select') {
    if (attempt.type !== 'select') return 'wrong-target';
    return normalizeText(attempt.value) === normalizeText(step.expectedValue) ? 'correct' : 'wrong-value';
  }
  if (step.action === 'key') {
    if (attempt.type === 'input' && attempt.confirm === 'enter' && step.expectedValue === 'Enter') return 'correct';
    return 'wrong-value';
  }
  if (attempt.type === 'click') return 'wrong-target';
  if (attempt.type !== 'input') return 'wrong-target';
  const matches = normalizeText(attempt.value) === normalizeText(step.expectedValue);
  if (step.confirm === 'enter') {
    if (attempt.confirm !== 'enter') return 'ignore';
    return matches ? 'correct' : 'wrong-value';
  }
  if (!matches) return attempt.confirm === 'enter' ? 'wrong-value' : 'ignore';
  return 'correct';
}

export function errorFor(
  step: LessonStep,
  verdict: 'wrong-target' | 'wrong-value',
  mode: Mode,
  label: string,
): string {
  if (mode === 'test') return 'Неверно.';
  if (step.errorText.trim()) return step.errorText.trim();
  if (verdict === 'wrong-value') {
    return `Неверное значение в поле «${label}». Проверьте ввод.`;
  }
  if (step.action === 'input') {
    return `Сначала введите значение в поле «${label}».`;
  }
  return 'Неверное действие. Посмотрите формулировку шага.';
}

export function hintFor(step: LessonStep, wrongOnStep: number, mode: Mode): string | null {
  if (mode !== 'try' || wrongOnStep < 2) return null;
  return step.hint.trim() || step.instruction;
}

export function createState(mode: Mode): RuntimeState {
  return {
    mode,
    stepIndex: 0,
    wrongOnStep: 0,
    mistakes: 0,
    firstTryCorrect: 0,
    status: 'play',
    error: null,
    hint: null,
  };
}

export function reduceLesson(lesson: Lesson, state: RuntimeState, event: RuntimeEvent): RuntimeState {
  if (state.status === 'done') return state;
  const step = lesson.steps[state.stepIndex];
  if (!step) return { ...state, status: 'done' };

  if (event.type === 'show-advance') {
    if (state.mode !== 'show') return state;
    const last = state.stepIndex >= lesson.steps.length - 1;
    return {
      ...state,
      stepIndex: last ? state.stepIndex : state.stepIndex + 1,
      status: last ? 'done' : 'play',
      wrongOnStep: 0,
      error: null,
      hint: null,
      firstTryCorrect: state.firstTryCorrect + 1,
    };
  }

  if (state.mode === 'show') return state;
  const verdict = judge(step, event.attempt);
  if (verdict === 'ignore') return state;
  if (verdict === 'correct') {
    const last = state.stepIndex >= lesson.steps.length - 1;
    const clean = state.wrongOnStep === 0;
    return {
      ...state,
      stepIndex: last ? state.stepIndex : state.stepIndex + 1,
      status: last ? 'done' : 'play',
      wrongOnStep: 0,
      mistakes: state.mistakes,
      firstTryCorrect: state.firstTryCorrect + (clean ? 1 : 0),
      error: null,
      hint: null,
    };
  }
  const wrongOnStep = state.wrongOnStep + 1;
  const hotspot = hotspotById(lesson, step.screenId, step.hotspotId);
  return {
    ...state,
    wrongOnStep,
    mistakes: state.mistakes + 1,
    error: errorFor(step, verdict, state.mode, hotspot?.label || 'поле'),
    hint: hintFor(step, wrongOnStep, state.mode),
  };
}

export function screenById(lesson: Lesson, screenId: string): LessonScreen | undefined {
  return lesson.screens.find((screen) => screen.id === screenId);
}

export function hotspotById(lesson: Lesson, screenId: string, hotspotId: string): Hotspot | undefined {
  return screenById(lesson, screenId)?.hotspots.find((hotspot) => hotspot.id === hotspotId);
}

export function currentScreen(lesson: Lesson, state: RuntimeState): LessonScreen | undefined {
  const step = lesson.steps[state.stepIndex];
  if (!step) return lesson.screens[lesson.screens.length - 1];
  if (state.status === 'done') {
    return screenById(lesson, step.nextScreenId || step.screenId) ?? screenById(lesson, step.screenId);
  }
  return screenById(lesson, step.screenId);
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readString(source: Record<string, unknown>, key: string): string {
  const value = source[key];
  if (typeof value !== 'string') throw new Error(`Поле «${key}» должно быть строкой`);
  return value;
}

function readBox(value: unknown): Box {
  if (!isRecord(value)) throw new Error('Рамка hotspot задана неверно');
  for (const key of ['x', 'y', 'width', 'height'] as const) {
    if (typeof value[key] !== 'number' || Number.isNaN(value[key])) {
      throw new Error(`Координата «${key}» должна быть числом`);
    }
  }
  return { x: value.x as number, y: value.y as number, width: value.width as number, height: value.height as number };
}

function readHotspot(value: unknown): Hotspot {
  if (!isRecord(value)) throw new Error('Hotspot задан неверно');
  const kind = value.kind;
  if (typeof kind !== 'string' || !HOTSPOT_KINDS.has(kind as HotspotKind)) {
    throw new Error('Неизвестный тип hotspot');
  }
  const hotspot: Hotspot = {
    id: readString(value, 'id'),
    kind: kind as HotspotKind,
    label: readString(value, 'label'),
    selector: readString(value, 'selector'),
    box: readBox(value.box),
  };
  if (typeof value.inputType === 'string') hotspot.inputType = value.inputType;
  if (typeof value.placeholder === 'string') hotspot.placeholder = value.placeholder;
  return hotspot;
}

function readStep(value: unknown, index: number): LessonStep {
  if (!isRecord(value)) throw new Error(`Шаг ${index + 1} задан неверно`);
  const action = value.action;
  if (typeof action !== 'string' || !STEP_ACTIONS.has(action as StepAction)) {
    throw new Error(`Неизвестное действие в шаге ${index + 1}`);
  }
  const confirm = value.confirm;
  if (confirm !== null && confirm !== 'match' && confirm !== 'enter') {
    throw new Error(`Поле confirm в шаге ${index + 1} задано неверно`);
  }
  return {
    id: readString(value, 'id'),
    index: typeof value.index === 'number' ? value.index : index,
    screenId: readString(value, 'screenId'),
    action: action as StepAction,
    hotspotId: readString(value, 'hotspotId'),
    expectedValue: typeof value.expectedValue === 'string' ? value.expectedValue : '',
    confirm,
    nextScreenId: typeof value.nextScreenId === 'string' ? value.nextScreenId : null,
    instruction: readString(value, 'instruction'),
    hint: typeof value.hint === 'string' ? value.hint : '',
    errorText: typeof value.errorText === 'string' ? value.errorText : '',
  };
}

export function parseLesson(data: unknown): Lesson {
  if (!isRecord(data)) throw new Error('Урок должен быть объектом');
  if (data.schemaVersion !== LESSON_SCHEMA_VERSION) throw new Error('Неподдерживаемая версия схемы урока');
  if (!Array.isArray(data.screens) || data.screens.length === 0) throw new Error('В уроке нет экранов');
  if (!Array.isArray(data.steps)) throw new Error('В уроке нет списка шагов');
  const screens = data.screens.map((screen) => {
    if (!isRecord(screen)) throw new Error('Экран задан неверно');
    if (!Array.isArray(screen.hotspots)) throw new Error('У экрана нет hotspot');
    const viewport = screen.viewport;
    if (!isRecord(viewport) || typeof viewport.width !== 'number' || typeof viewport.height !== 'number') {
      throw new Error('У экрана не задан viewport');
    }
    return {
      id: readString(screen, 'id'),
      title: readString(screen, 'title'),
      url: readString(screen, 'url'),
      html: readString(screen, 'html'),
      screenshot: readString(screen, 'screenshot'),
      hotspots: screen.hotspots.map((hotspot) => readHotspot(hotspot)),
      viewport: { width: viewport.width, height: viewport.height },
    } satisfies LessonScreen;
  });
  const steps = data.steps.map((step, index) => readStep(step, index));
  const meta = isRecord(data.meta) ? data.meta : {};
  const viewport = isRecord(data.viewport) ? data.viewport : { width: 1280, height: 800 };
  return {
    schemaVersion: LESSON_SCHEMA_VERSION,
    id: readString(data, 'id'),
    title: readString(data, 'title'),
    description: readString(data, 'description'),
    sourceSite: readString(data, 'sourceSite'),
    viewport: {
      width: typeof viewport.width === 'number' ? viewport.width : 1280,
      height: typeof viewport.height === 'number' ? viewport.height : 800,
    },
    screens,
    steps,
    meta: {
      generatedAt: typeof meta.generatedAt === 'string' ? meta.generatedAt : '',
      generator: typeof meta.generator === 'string' ? meta.generator : '',
      stepCount: typeof meta.stepCount === 'number' ? meta.stepCount : steps.length,
      manuallyEditedSteps: typeof meta.manuallyEditedSteps === 'number' ? meta.manuallyEditedSteps : 0,
      autoStepRatio: typeof meta.autoStepRatio === 'number' ? meta.autoStepRatio : 1,
      fidelity: Array.isArray(meta.fidelity) ? (meta.fidelity as ScreenFidelity[]) : [],
      warnings: Array.isArray(meta.warnings) ? meta.warnings.filter((item) => typeof item === 'string') : [],
    },
  };
}
