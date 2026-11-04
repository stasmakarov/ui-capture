import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import {
  createState,
  currentScreen,
  parseLesson,
  reduceLesson,
  type Attempt,
  type Lesson,
  type Mode,
  type RenderMode,
  type RuntimeState,
} from '../shared/lesson.ts';
import { embeddedPack } from '@embedded';
import { Stage } from './player/Stage.tsx';

type CatalogItem = {
  id: string;
  title: string;
  description: string;
  sourceSite: string;
  screens: number;
  steps: number;
  autoStepRatio: number;
};

const MODES: { id: Mode; label: string; note: string }[] = [
  { id: 'show', label: 'Покажи', note: 'Шаги выполняются сами, цель подсвечивается' },
  { id: 'try', label: 'Попробуй', note: 'Подсказка после двух ошибок' },
  { id: 'test', label: 'Проверь', note: 'Без подсказок, оценка в конце' },
];

export default function App() {
  const [catalog, setCatalog] = useState<CatalogItem[] | null>(embeddedPack?.catalog ?? null);
  const [media, setMedia] = useState<{ documents: Record<string, string>; screenshots: Record<string, string> } | null>(null);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [lesson, setLesson] = useState<Lesson | null>(null);
  const [lessonError, setLessonError] = useState<string | null>(null);
  const [mode, setMode] = useState<Mode>('try');
  const [renderMode, setRenderMode] = useState<RenderMode>('dom');
  const [runtime, setRuntime] = useState<RuntimeState>(() => createState('try'));
  const [paused, setPaused] = useState(false);
  const [readyToken, setReadyToken] = useState<string | null>(null);

  useEffect(() => {
    const playId = new URLSearchParams(window.location.search).get('play');
    if (playId) openLesson(playId);
  }, []);

  useEffect(() => {
    if (embeddedPack) return;
    let cancelled = false;
    fetch('/lessons/index.json')
      .then(async (response) => {
        if (!response.ok) throw new Error('Каталог уроков не найден');
        return response.json() as Promise<CatalogItem[]>;
      })
      .then((items) => {
        if (!cancelled) setCatalog(items);
      })
      .catch((error: unknown) => {
        if (!cancelled) setCatalogError(error instanceof Error ? error.message : 'Не удалось загрузить каталог');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const screen = lesson ? currentScreen(lesson, runtime) : undefined;
  const step = lesson && runtime.status === 'play' ? lesson.steps[runtime.stepIndex] ?? null : null;
  const ready = readyToken === `${renderMode}:${screen?.id ?? ''}`;

  useEffect(() => {
    if (!lesson || mode !== 'show' || runtime.status !== 'play' || !step?.audio) return;
    const src = step.audio.startsWith('data:') ? step.audio : `/lessons/${lesson.id}/${step.audio}`;
    const audio = new Audio(src);
    void audio.play().catch(() => undefined);
    return () => {
      audio.pause();
    };
  }, [lesson, mode, runtime.status, step?.id, step?.audio]);

  useEffect(() => {
    if (!lesson || mode !== 'show' || runtime.status !== 'play' || paused || !ready) return;
    const current = lesson.steps[runtime.stepIndex];
    if (!current) return;
    const delay = current.action === 'input' ? 1800 : 1400;
    const timer = window.setTimeout(() => {
      setRuntime((prev) => reduceLesson(lesson, prev, { type: 'show-advance' }));
    }, delay);
    return () => window.clearTimeout(timer);
  }, [lesson, mode, runtime.status, runtime.stepIndex, paused, ready]);

  const onReady = useCallback((token: string) => setReadyToken(token), []);
  const onAttempt = useCallback((attempt: Attempt) => {
    if (!lesson || mode === 'show') return;
    setRuntime((prev) => (prev.status === 'done' ? prev : reduceLesson(lesson, prev, { type: 'attempt', attempt })));
  }, [lesson, mode]);

  function openLesson(id: string) {
    setLesson(null);
    setLessonError(null);
    setReadyToken(null);
    const packed = embeddedPack?.lessons.find((item) => item.lesson.id === id);
    if (packed) {
      const parsed = parseLesson(packed.lesson);
      setLesson(parsed);
      setRenderMode(parsed.meta.defaultRenderMode);
      setMedia({ documents: packed.documents, screenshots: packed.screenshots });
      setRuntime(createState(mode));
      setPaused(false);
      return;
    }
    setMedia(null);
    fetch(`/lessons/${id}/lesson.json`)
      .then(async (response) => {
        if (!response.ok) throw new Error('Урок не найден');
        return response.json() as Promise<unknown>;
      })
      .then((data) => {
        const parsed = parseLesson(data);
        setLesson(parsed);
        setRenderMode(parsed.meta.defaultRenderMode);
        setRuntime(createState(mode));
        setPaused(false);
      })
      .catch((error: unknown) => {
        setLessonError(error instanceof Error ? error.message : 'Не удалось открыть урок');
      });
  }

  function switchMode(next: Mode) {
    setMode(next);
    setRuntime(createState(next));
    setPaused(false);
  }

  return (
    <div className="app">
      <header className="banner">
        <div className="banner-mark" aria-hidden="true" />
        <div>
          <strong>Учебный тренажёр</strong>
          <span>{lesson ? lesson.title : 'Каталог записанных уроков'}</span>
        </div>
      </header>
      {lesson && screen ? (
        <Player
          lesson={lesson}
          screenTitle={screen.title}
          step={step}
          runtime={runtime}
          mode={mode}
          renderMode={renderMode}
          paused={paused}
          onMode={switchMode}
          onRender={setRenderMode}
          onPause={() => setPaused((value) => !value)}
          onNext={() => setRuntime((prev) => reduceLesson(lesson, prev, { type: 'show-advance' }))}
          onRestart={() => {
            setRuntime(createState(mode));
            setPaused(false);
          }}
          onCatalog={() => setLesson(null)}
        >
          <Stage
            lessonId={lesson.id}
            screen={screen}
            documentHtml={media?.documents[screen.id] ?? null}
            screenshotSrc={media?.screenshots[screen.id] ?? `/lessons/${lesson.id}/${screen.screenshot}`}
            step={step}
            renderMode={renderMode}
            interactive={mode !== 'show' && runtime.status === 'play'}
            demoValue={mode === 'show' && step?.action === 'input' ? step.expectedValue : null}
            onAttempt={onAttempt}
            onReady={onReady}
          />
        </Player>
      ) : (
        <Catalog
          items={catalog}
          error={lessonError || catalogError}
          onOpen={openLesson}
        />
      )}
    </div>
  );
}

function Catalog({
  items,
  error,
  onOpen,
}: {
  items: CatalogItem[] | null;
  error: string | null;
  onOpen: (id: string) => void;
}) {
  return (
    <main className="catalog">
      <div className="catalog-lead">
        <h1>Уроки из записи экрана</h1>
        <p>Каждый урок собран автоматически: снимок страниц, действия и те же шаги для режимов «разметка» и «скриншот».</p>
      </div>
      {error && <p className="banner-error">{error}</p>}
      {!items && !error && <p className="muted">Загрузка каталога…</p>}
      {items && items.length === 0 && <p className="muted">Записанных уроков пока нет.</p>}
      <div className="cards">
        {items?.map((item) => (
          <article key={item.id} className="card">
            <p className="site">{hostOf(item.sourceSite)}</p>
            <h2>{item.title}</h2>
            <p>{item.description}</p>
            <p className="meta">{plural(item.screens, 'экран', 'экрана', 'экранов')} · {plural(item.steps, 'шаг', 'шага', 'шагов')} · автосборка {Math.round(item.autoStepRatio * 100)}%</p>
            <button type="button" onClick={() => onOpen(item.id)}>Открыть урок</button>
          </article>
        ))}
      </div>
    </main>
  );
}

function Player({
  lesson,
  screenTitle,
  step,
  runtime,
  mode,
  renderMode,
  paused,
  onMode,
  onRender,
  onPause,
  onNext,
  onRestart,
  onCatalog,
  children,
}: {
  lesson: Lesson;
  screenTitle: string;
  step: Lesson['steps'][number] | null;
  runtime: RuntimeState;
  mode: Mode;
  renderMode: RenderMode;
  paused: boolean;
  onMode: (mode: Mode) => void;
  onRender: (mode: RenderMode) => void;
  onPause: () => void;
  onNext: () => void;
  onRestart: () => void;
  onCatalog: () => void;
  children: ReactNode;
}) {
  const total = lesson.steps.length;
  const doneCount = runtime.status === 'done' ? total : runtime.stepIndex;
  return (
    <div className="workspace">
      <FitFrame>{children}</FitFrame>
      <aside className="panel">
        <p className="screen-name">Экран: {screenTitle}</p>
        <div className="switch" role="tablist" aria-label="Режим урока">
          {MODES.map((item) => (
            <button
              key={item.id}
              type="button"
              role="tab"
              aria-selected={mode === item.id}
              className={mode === item.id ? 'on' : ''}
              onClick={() => onMode(item.id)}
            >
              {item.label}
            </button>
          ))}
        </div>
        <p className="mode-note">{MODES.find((item) => item.id === mode)?.note}</p>
        <div className="switch" role="tablist" aria-label="Способ показа">
          <button type="button" className={renderMode === 'dom' ? 'on' : ''} onClick={() => onRender('dom')}>Разметка</button>
          <button type="button" className={renderMode === 'screenshot' ? 'on' : ''} onClick={() => onRender('screenshot')}>Скриншот</button>
        </div>
        {runtime.status === 'done' ? (
          <div className="result">
            <h2>{mode === 'test' ? 'Проверка завершена' : 'Урок пройден'}</h2>
            {mode === 'test' ? (
              <p>С первой попытки: {runtime.firstTryCorrect} из {total}. Ошибок: {runtime.mistakes}.</p>
            ) : (
              <p>Все шаги выполнены. Можно переключить показ на скриншот и пройти тот же урок ещё раз.</p>
            )}
          </div>
        ) : (
          <div className="now">
            <p className="kicker">Шаг {runtime.stepIndex + 1} из {total}</p>
            <p className="instruction">{mode === 'test' ? 'Выполните действие на экране без подсказки.' : step?.instruction}</p>
            {mode === 'show' && step?.narration ? <p className="hint">Комментарий: {step.narration}</p> : null}
          </div>
        )}
        {runtime.error && <p className="error" role="alert">{runtime.error}</p>}
        {runtime.hint && <p className="hint">Подсказка: {runtime.hint}</p>}
        <ol className="steps">
          {lesson.steps.map((item, index) => {
            const status = runtime.status === 'done' || index < doneCount ? 'done' : index === runtime.stepIndex ? 'current' : 'wait';
            return (
              <li key={item.id} className={status}>
                <span>{index + 1}</span>
                <p>{labelFor(item.instruction, status, mode)}</p>
              </li>
            );
          })}
        </ol>
        <div className="actions">
          {mode === 'show' && runtime.status === 'play' && (
            <>
              <button type="button" onClick={onPause}>{paused ? 'Дальше само' : 'Пауза'}</button>
              <button type="button" onClick={onNext}>Далее</button>
            </>
          )}
          <button type="button" onClick={onRestart}>Сначала</button>
          <button type="button" className="ghost" onClick={onCatalog}>К каталогу</button>
        </div>
      </aside>
    </div>
  );
}

function FitFrame({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const [scale, setScale] = useState(1);
  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    const apply = () => setScale(Math.min(1, Math.max(0.2, (node.clientWidth - 8) / 1280)));
    apply();
    const observer = new ResizeObserver(apply);
    observer.observe(node);
    return () => observer.disconnect();
  }, []);
  return (
    <div className="stage-wrap" ref={ref}>
      <div className="scaler" style={{ width: 1280 * scale, height: 800 * scale }}>
        <div className="scaled" style={{ transform: `scale(${scale})` }}>{children}</div>
      </div>
    </div>
  );
}

function labelFor(instruction: string, status: string, mode: Mode): string {
  if (status === 'done') return instruction;
  if (mode === 'test') return status === 'current' ? 'Без подсказки' : 'Скрыто';
  if (status === 'wait') return 'Скрыто до этого шага';
  return instruction;
}

function plural(count: number, one: string, few: string, many: string): string {
  const mod10 = count % 10;
  const mod100 = count % 100;
  if (mod10 === 1 && mod100 !== 11) return `${count} ${one}`;
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return `${count} ${few}`;
  return `${count} ${many}`;
}

function hostOf(url: string): string {
  try {
    return new URL(url).host;
  } catch {
    return url;
  }
}
