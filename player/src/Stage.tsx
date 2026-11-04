import { useEffect, useRef, type CSSProperties, type RefObject } from 'react';
import type { Attempt, LessonScreen, LessonStep, RenderMode } from '../../shared/lesson.ts';

type StageProps = {
  lessonId: string;
  screen: LessonScreen;
  documentHtml: string | null;
  screenshotSrc: string;
  step: LessonStep | null;
  renderMode: RenderMode;
  interactive: boolean;
  demoValue: string | null;
  onAttempt: (attempt: Attempt) => void;
  onReady: (token: string) => void;
};

function boxStyle(box: LessonScreen['hotspots'][number]['box']): CSSProperties {
  return {
    left: `${box.x}px`,
    top: `${box.y}px`,
    width: `${Math.max(box.width, 8)}px`,
    height: `${Math.max(box.height, 8)}px`,
  };
}

export function Stage({ lessonId, screen, documentHtml, screenshotSrc, step, renderMode, interactive, demoValue, onAttempt, onReady }: StageProps) {
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const attemptRef = useRef(onAttempt);
  const interactiveRef = useRef(interactive);
  const stepRef = useRef(step);
  const demoRef = useRef(demoValue);
  attemptRef.current = onAttempt;
  interactiveRef.current = interactive;
  stepRef.current = step;
  demoRef.current = demoValue;
  const token = `${renderMode}:${screen.id}`;

  useEffect(() => {
    if (renderMode !== 'dom') return;
    const iframe = iframeRef.current;
    if (!iframe) return;
    const onLoad = () => {
      const doc = iframe.contentDocument;
      if (!doc?.documentElement || !doc.body) return;
      const inline = documentHtml !== null;
      if (!inline && (doc.URL === 'about:blank' || doc.URL.startsWith('about:'))) return;
      if (inline && doc.body.childElementCount === 0) return;
      if (doc.documentElement.dataset.trainerBound === 'yes') return;
      bindDocument(doc, attemptRef, interactiveRef);
      paintDom(doc, stepRef.current, demoRef.current);
      onReady(token);
    };
    iframe.addEventListener('load', onLoad);
    const timer = window.setInterval(onLoad, 100);
    const stop = window.setTimeout(() => window.clearInterval(timer), 3000);
    return () => {
      iframe.removeEventListener('load', onLoad);
      window.clearInterval(timer);
      window.clearTimeout(stop);
    };
  }, [documentHtml, lessonId, screen.html, renderMode, token, onReady]);

  useEffect(() => {
    if (renderMode !== 'dom') return;
    const doc = iframeRef.current?.contentDocument;
    if (!doc) return;
    return paintDom(doc, step, demoValue);
  }, [renderMode, step, demoValue, token]);

  useEffect(() => {
    if (renderMode === 'screenshot') onReady(token);
  }, [renderMode, token, onReady]);

  if (renderMode === 'screenshot') {
    const field = step?.action === 'input'
      ? screen.hotspots.find((hotspot) => hotspot.id === step.hotspotId)
      : undefined;
    return (
      <div className="frame shot-frame">
        <img
          src={screenshotSrc}
          alt={screen.title}
          width={screen.viewport.width}
          height={screen.viewport.height}
          onLoad={() => onReady(token)}
        />
        {screen.hotspots.map((hotspot) => {
          if (field && hotspot.id === field.id) return null;
          const active = step?.hotspotId === hotspot.id;
          return (
            <button
              key={hotspot.id}
              type="button"
              className={active ? 'spot spot-on' : 'spot'}
              style={boxStyle(hotspot.box)}
              title={hotspot.label}
              data-hotspot={hotspot.id}
              disabled={!interactive}
              onClick={() => onAttempt({ type: 'click', hotspotId: hotspot.id })}
            />
          );
        })}
        {field && step && (
          <ScreenshotField
            key={`${step.id}:${demoValue ?? ''}`}
            label={field.label}
            style={boxStyle(field.box)}
            placeholder={field.placeholder}
            demoValue={demoValue}
            interactive={interactive}
            onAttempt={(value, confirm) => onAttempt({
              type: 'input',
              hotspotId: field.id,
              value,
              confirm,
            })}
          />
        )}
      </div>
    );
  }

  return (
    <iframe
      ref={iframeRef}
      className="frame"
      title={screen.title}
      {...(documentHtml ? { srcDoc: documentHtml } : { src: `/lessons/${lessonId}/${screen.html}` })}
    />
  );
}

function ScreenshotField({
  label,
  style,
  placeholder,
  demoValue,
  interactive,
  onAttempt,
}: {
  label: string;
  style: CSSProperties;
  placeholder?: string;
  demoValue: string | null;
  interactive: boolean;
  onAttempt: (value: string, confirm: 'enter' | 'input') => void;
}) {
  const ref = useRef<HTMLInputElement>(null);
  useEffect(() => {
    const input = ref.current;
    if (!input || demoValue === null) return;
    let index = 0;
    input.value = '';
    const timer = window.setInterval(() => {
      index += 1;
      input.value = demoValue.slice(0, index);
      if (index >= demoValue.length) window.clearInterval(timer);
    }, 32);
    return () => window.clearInterval(timer);
  }, [demoValue]);

  return (
    <input
      ref={ref}
      className="shot-input"
      style={style}
      data-hotspot="field"
      aria-label={label}
      placeholder={placeholder}
      readOnly={demoValue !== null || !interactive}
      onChange={(event) => onAttempt(event.target.value, 'input')}
      onKeyDown={(event) => {
        if (event.key === 'Enter') onAttempt(event.currentTarget.value, 'enter');
      }}
    />
  );
}

function bindDocument(
  doc: Document,
  attemptRef: RefObject<((attempt: Attempt) => void) | null>,
  interactiveRef: RefObject<boolean>,
) {
  if (!doc.documentElement || doc.documentElement.dataset.trainerBound === 'yes') return;
  doc.documentElement.dataset.trainerBound = 'yes';
  const style = doc.createElement('style');
  style.textContent = '.trainer-target{outline:3px solid #e0a800 !important;outline-offset:2px !important;box-shadow:0 0 0 6px rgba(255,214,70,.45) !important}';
  doc.head.appendChild(style);
  doc.addEventListener('submit', (event) => event.preventDefault(), true);
  doc.addEventListener('click', (event) => {
    const target = asElement(event.target);
    if (!target) return;
    const host = target.closest('[data-hotspot]');
    event.preventDefault();
    event.stopPropagation();
    if (!interactiveRef.current || !host) return;
    const id = host.getAttribute('data-hotspot');
    if (!id) return;
    const tag = host.tagName;
    const type = (host.getAttribute('type') || 'text').toLowerCase();
    const textual = tag === 'TEXTAREA' || (tag === 'INPUT' && !['button', 'submit', 'checkbox', 'radio', 'image', 'reset'].includes(type));
    if (textual) return;
    attemptRef.current?.({ type: 'click', hotspotId: id });
  }, true);
  doc.addEventListener('input', (event) => {
    if (!interactiveRef.current) return;
    const target = asElement(event.target);
    if (!target || (target.tagName !== 'INPUT' && target.tagName !== 'TEXTAREA')) return;
    const id = target.closest('[data-hotspot]')?.getAttribute('data-hotspot');
    if (!id || !('value' in target)) return;
    attemptRef.current?.({ type: 'input', hotspotId: id, value: String(target.value), confirm: 'input' });
  }, true);
  doc.addEventListener('change', (event) => {
    if (!interactiveRef.current) return;
    const target = asElement(event.target);
    if (!target || target.tagName !== 'SELECT' || !('value' in target)) return;
    const id = target.closest('[data-hotspot]')?.getAttribute('data-hotspot');
    if (!id) return;
    attemptRef.current?.({ type: 'select', hotspotId: id, value: String(target.value) });
  }, true);
  doc.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter' || !interactiveRef.current) return;
    const target = asElement(event.target);
    if (!target || (target.tagName !== 'INPUT' && target.tagName !== 'TEXTAREA') || !('value' in target)) return;
    const id = target.closest('[data-hotspot]')?.getAttribute('data-hotspot');
    if (!id) return;
    event.preventDefault();
    attemptRef.current?.({ type: 'input', hotspotId: id, value: String(target.value), confirm: 'enter' });
  }, true);
}

function asElement(target: EventTarget | null): (Element & { value?: string }) | null {
  if (!target || typeof target !== 'object' || !('tagName' in target)) return null;
  return target as Element & { value?: string };
}

function paintDom(doc: Document, step: LessonStep | null, demoValue: string | null) {
  doc.querySelectorAll('.trainer-target').forEach((node) => node.classList.remove('trainer-target'));
  if (!step) return;
  const target = doc.querySelector(`[data-hotspot="${CSS.escape(step.hotspotId)}"]`);
  if (!target || !('classList' in target)) return;
  const node = target as HTMLElement;
  node.classList.add('trainer-target');
  node.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  if (demoValue === null || (node.tagName !== 'INPUT' && node.tagName !== 'TEXTAREA')) return;
  const field = node as HTMLInputElement;
  let index = 0;
  field.value = '';
  const timer = window.setInterval(() => {
    index += 1;
    field.value = demoValue.slice(0, index);
    if (index >= demoValue.length) window.clearInterval(timer);
  }, 32);
  return () => window.clearInterval(timer);
}
