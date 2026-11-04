import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { chromium, type Browser, type BrowserContext, type Page } from 'playwright';
import { explainLaunchError, explainOpenError } from './errors.ts';
import type { CaptureAction, CaptureBundle, CaptureScreen, DescribedElement, SavedAsset } from './types.ts';

const VIEWPORT = { width: 1280, height: 800 };

type TrainerApi = {
  describe: (el: Element) => DescribedElement;
  listInteractive: () => DescribedElement[];
  freeze: () => string;
  takeSnapshot: () => unknown;
  setBaseline?: () => void;
};

type Reported = {
  id: string;
  type: 'click' | 'fill' | 'select';
  described: DescribedElement;
  value: string;
  confirm: 'match' | 'enter' | null;
  changedInputs: Array<{ described: DescribedElement; value: string }>;
  href?: string;
};

const RECORDER = `
(() => {
  if (window.__trainerRecorder) return;
  window.__trainerRecorder = true;
  window.__trainerQueue = window.__trainerQueue || [];
  let seq = 0;
  const push = (payload) => {
    // timeOrigin свой у каждого документа: после перехода и в новой вкладке номера не повторяются
    payload.id = String(Math.round(performance.timeOrigin)) + '-' + (++seq);
    payload.href = location.href;
    window.__trainerQueue.push(payload);
    const report = window.__trainerReport;
    if (typeof report === 'function') void report(payload);
  };
  const changedInputs = () => {
    const api = window.__trainer;
    const base = window.__trainerBaseline || {};
    if (!api) return [];
    const found = [];
    for (const el of document.querySelectorAll('input, textarea')) {
      const type = (el.getAttribute('type') || 'text').toLowerCase();
      if (['button', 'submit', 'checkbox', 'radio', 'hidden', 'image', 'reset', 'file'].includes(type)) continue;
      const described = api.describe(el);
      const previous = base[described.hotspotId];
      const value = 'value' in el ? String(el.value) : '';
      if (value !== (previous ? previous.value : '')) found.push({ described, value });
    }
    return found;
  };
  window.__trainerSetBaseline = () => {
    const api = window.__trainer;
    const map = {};
    if (!api) return;
    for (const el of document.querySelectorAll('input, textarea, select')) {
      const described = api.describe(el);
      map[described.hotspotId] = { value: 'value' in el ? String(el.value) : '' };
    }
    window.__trainerBaseline = map;
  };
  document.addEventListener('click', (event) => {
    const target = event.target;
    if (!(target instanceof Element) || !window.__trainer) return;
    const host = target.closest('a, button, input, select, textarea, [role="tab"], [role="button"]');
    if (!host) return;
    const type = (host.getAttribute('type') || 'text').toLowerCase();
    const textual = host.tagName === 'TEXTAREA' || (host.tagName === 'INPUT' && !['button', 'submit', 'checkbox', 'radio', 'image', 'reset'].includes(type));
    if (textual) return;
    push({
      type: 'click',
      described: window.__trainer.describe(host),
      value: '',
      confirm: null,
      changedInputs: changedInputs(),
    });
  }, true);
  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Enter' || !window.__trainer) return;
    const target = event.target;
    if (!(target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement)) return;
    push({
      type: 'fill',
      described: window.__trainer.describe(target),
      value: target.value,
      confirm: 'enter',
      changedInputs: [],
    });
  }, true);
  document.addEventListener('change', (event) => {
    const target = event.target;
    if (!(target instanceof HTMLSelectElement) || !window.__trainer) return;
    const selected = target.options[target.selectedIndex];
    push({
      type: 'select',
      described: window.__trainer.describe(target),
      value: selected ? selected.text.trim() || target.value : target.value,
      confirm: null,
      changedInputs: [],
    });
  }, true);
})();
`;

type LaunchChoice = {
  headless: boolean;
  channel?: string;
  executablePath?: string;
};

async function bundleInpage(): Promise<string> {
  const esbuild = await import('esbuild');
  const result = await esbuild.build({
    entryPoints: [path.resolve('tools/capture/inpage-entry.ts')],
    bundle: true,
    format: 'iife',
    platform: 'browser',
    write: false,
    target: 'es2022',
    logLevel: 'silent',
  });
  const file = result.outputFiles[0];
  if (!file) throw new Error('Не удалось собрать скрипт снимка');
  return file.text;
}

function assetFileName(url: string, contentType: string): string {
  const hash = createHash('sha1').update(url).digest('hex').slice(0, 10);
  let base = 'asset';
  try {
    base = path.basename(new URL(url).pathname).replace(/[^a-zA-Z0-9._-]/g, '').slice(0, 48) || 'asset';
  } catch {
    base = 'asset';
  }
  const ext = path.extname(base).toLowerCase();
  const known = ['.css', '.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg', '.woff', '.woff2', '.ttf', '.otf', '.ico'];
  if (known.includes(ext)) return `${hash}-${base}`;
  if (contentType.includes('css')) return `${hash}-${base}.css`;
  if (contentType.includes('png')) return `${hash}-${base}.png`;
  if (contentType.includes('jpeg')) return `${hash}-${base}.jpg`;
  if (contentType.includes('svg')) return `${hash}-${base}.svg`;
  if (contentType.includes('woff2')) return `${hash}-${base}.woff2`;
  if (contentType.includes('woff')) return `${hash}-${base}.woff`;
  if (contentType.includes('gif')) return `${hash}-${base}.gif`;
  if (contentType.includes('webp')) return `${hash}-${base}.webp`;
  return `${hash}-${base}.bin`;
}

function trackAssets(context: BrowserContext, netDir: string, assets: SavedAsset[]) {
  const inflight = new Set<Promise<void>>();
  const seen = new Set<string>();
  context.on('response', (response) => {
    const resource = response.request().resourceType();
    if (resource !== 'stylesheet' && resource !== 'image' && resource !== 'font') return;
    const url = response.url();
    if (!url.startsWith('http') || seen.has(url)) return;
    seen.add(url);
    const task = (async () => {
      try {
        const body = await response.body();
        const contentType = response.headers()['content-type'] ?? '';
        const file = assetFileName(url, contentType);
        await writeFile(path.join(netDir, file), body);
        assets.push({ url, file, contentType });
      } catch {
        /* тело ответа уже недоступно */
      }
    })();
    inflight.add(task);
    void task.finally(() => inflight.delete(task));
  });
  return async function flushAssets() {
    const pending = [...inflight];
    if (pending.length) await Promise.all(pending);
  };
}

async function launchBrowser(choice: LaunchChoice): Promise<Browser> {
  const args = [
    '--disable-background-networking',
    '--disable-component-update',
    '--disable-sync',
    '--disable-default-apps',
    '--no-first-run',
    '--disable-breakpad',
  ];
  try {
    if (choice.executablePath) {
      return await chromium.launch({ headless: choice.headless, executablePath: choice.executablePath, args });
    }
    if (choice.channel) {
      return await chromium.launch({
        headless: choice.headless,
        channel: choice.channel as 'chrome',
        args,
      });
    }
    return await chromium.launch({ headless: choice.headless, args });
  } catch (error) {
    throw new Error(explainLaunchError(error));
  }
}

async function captureScreen(
  page: Page,
  captureDir: string,
  screenId: string,
  flushAssets: () => Promise<void>,
): Promise<CaptureScreen> {
  await flushAssets();
  await page.waitForLoadState('domcontentloaded', { timeout: 8000 }).catch(() => undefined);
  const screenshotFile = `screens/${screenId}.original.png`;
  await page.screenshot({ path: path.join(captureDir, screenshotFile), fullPage: false });
  const elements = await page.evaluate(() => {
    const api = (window as Window & { __trainer?: TrainerApi }).__trainer;
    if (!api) throw new Error('Скрипт снимка не установлен');
    return api.listInteractive();
  });
  const snapshot = await page.evaluate(() => {
    const api = (window as Window & { __trainer?: TrainerApi }).__trainer;
    if (!api) return null;
    try {
      return api.takeSnapshot();
    } catch (error) {
      return { __error: error instanceof Error ? error.message : String(error) };
    }
  });
  const rrwebFile = `screens/${screenId}.rrweb.json`;
  await writeFile(path.join(captureDir, rrwebFile), JSON.stringify(snapshot));
  const frozen = await page.evaluate(() => {
    const api = (window as Window & { __trainer?: TrainerApi }).__trainer;
    if (!api) return '<!DOCTYPE html><html><body></body></html>';
    try {
      return api.freeze();
    } catch (error) {
      return `<!DOCTYPE html><html><body><p>${error instanceof Error ? error.message : 'freeze failed'}</p></body></html>`;
    }
  });
  const frozenHtmlFile = `screens/${screenId}.freeze.html`;
  await writeFile(path.join(captureDir, frozenHtmlFile), frozen);
  const pageTitle = await page.title();
  await page.evaluate(() => {
    const setBaseline = (window as Window & { __trainerSetBaseline?: () => void }).__trainerSetBaseline;
    setBaseline?.();
  });
  return {
    id: screenId,
    title: pageTitle.trim() || `Экран ${screenId.slice(1)}`,
    url: page.url(),
    pageTitle,
    viewport: VIEWPORT,
    screenshotFile,
    rrwebFile,
    frozenHtmlFile,
    elements,
  };
}

async function fingerprint(page: Page): Promise<string> {
  return page.evaluate(() => `${location.href}\n${(document.body?.innerText ?? '').replace(/\s+/g, ' ').slice(0, 1500)}`);
}

function remember(screen: CaptureScreen, described: DescribedElement): DescribedElement {
  const existing = screen.elements.find((item) => item.hotspotId === described.hotspotId || item.selector === described.selector);
  if (existing) return existing;
  screen.elements.push(described);
  return described;
}

export type RecordOptions = {
  url: string;
  lessonName: string;
  lessonId: string;
  mode: 'dom' | 'screenshot' | 'auto';
  capturesDir: string;
  headless: boolean;
  channel?: string;
  executablePath?: string;
  onReady?: (page: Page) => Promise<void>;
  onAction?: (action: CaptureAction) => void;
  onSession?: (controls: RecordingControls) => void;
};

export type RecordingControls = {
  stop: () => Promise<void>;
  setPaused: (paused: boolean) => void;
};

export async function recordInteractive(options: RecordOptions): Promise<string> {
  const captureDir = path.join(options.capturesDir, options.lessonId);
  const netDir = path.join(captureDir, 'net');
  await mkdir(path.join(captureDir, 'screens'), { recursive: true });
  await mkdir(netDir, { recursive: true });
  const script = await bundleInpage();
  const assets: SavedAsset[] = [];
  const browser = await launchBrowser(options);
  const context = await browser.newContext({
    viewport: VIEWPORT,
    locale: 'ru-RU',
    deviceScaleFactor: 1,
  });
  const flushAssets = trackAssets(context, netDir, assets);
  const seen = new Set<string>();
  const pending: Array<Reported & { page: Page }> = [];
  const screens: CaptureScreen[] = [];
  const actions: CaptureAction[] = [];
  const pages = new Set<Page>();
  let current: CaptureScreen | null = null;
  let currentPage: Page | null = null;
  let currentPrint = '';
  let active: Page | null = null;
  let paused = false;
  let timer: NodeJS.Timeout | undefined;
  let chain = Promise.resolve();
  let schedule = () => {};
  const rememberEvent = (from: Page, payload: Reported) => {
    if (!payload?.id || seen.has(payload.id)) return;
    seen.add(payload.id);
    if (paused) return;
    pending.push({ ...payload, page: from });
    active = from;
    schedule();
  };
  await context.exposeBinding('__trainerReport', (source: { page: Page }, payload: Reported) => {
    rememberEvent(source.page, payload);
  });
  await context.addInitScript(script);
  await context.addInitScript(RECORDER);
  const watchPage = (next: Page) => {
    if (pages.has(next)) return;
    pages.add(next);
    active = next;
    next.on('close', () => {
      pages.delete(next);
      if (active === next) active = [...pages].at(-1) ?? null;
      if (pages.size === 0 && browser.isConnected()) void browser.close();
    });
  };
  context.on('page', watchPage);
  const page = await context.newPage();
  watchPage(page);
  const pullQueue = async (target: Page) => {
    if (target.isClosed()) return;
    const batch = await target.evaluate(() => {
      const queue = (window as Window & { __trainerQueue?: Reported[] }).__trainerQueue ?? [];
      (window as Window & { __trainerQueue?: Reported[] }).__trainerQueue = [];
      return queue;
    }).catch(() => [] as Reported[]);
    for (const item of batch) rememberEvent(target, item);
  };

  const adopt = async (target: Page) => {
    if (target.isClosed()) return null;
    await target.waitForLoadState('domcontentloaded', { timeout: 8000 }).catch(() => undefined);
    const next = await captureScreen(target, captureDir, `s${screens.length + 1}`, flushAssets).catch((error: unknown) => {
      console.error('Не удалось снять экран', target.url(), error instanceof Error ? error.message : error);
      return null;
    });
    if (!next) return null;
    screens.push(next);
    const previous = actions[actions.length - 1];
    if (previous && previous.nextScreenId === null) previous.nextScreenId = next.id;
    current = next;
    currentPage = target;
    currentPrint = await fingerprint(target).catch(() => target.url());
    return next;
  };

  const flush = async () => {
    for (const target of [...pages]) await pullQueue(target);
    if (!current) return;
    const batch = pending.splice(0);
    if (!batch.length) return;
    for (const event of batch) {
      const href = event.href || (event.page.isClosed() ? current?.url : event.page.url());
      const otherDocument = !!current && !!href && href !== current.url;
      if (otherDocument && !event.page.isClosed()) {
        const stillThere = event.page.url() === href;
        if (stillThere || event.page !== currentPage) await adopt(event.page);
      }
      const screen = current;
      if (!screen) continue;
      const fills = event.type === 'click' ? event.changedInputs : [];
      for (const input of fills) {
        const described = remember(screen, input.described);
        actions.push({
          screenId: screen.id,
          kind: 'fill',
          confirm: 'match',
          value: input.value,
          hotspotId: described.hotspotId,
          selector: described.selector,
          label: described.label,
          box: described.box,
          nextScreenId: null,
          prelude: false,
        });
        console.log(`  ввод «${input.value}» в «${described.label}»`);
        options.onAction?.(actions[actions.length - 1]!);
      }
      const described = remember(screen, event.described);
      actions.push({
        screenId: screen.id,
        kind: event.type === 'fill' ? 'fill' : event.type === 'select' ? 'select' : 'click',
        confirm: event.type === 'fill' ? event.confirm : null,
        value: event.value,
        hotspotId: described.hotspotId,
        selector: described.selector,
        label: described.label,
        box: described.box,
        nextScreenId: null,
        prelude: false,
      });
      console.log(`  ${event.type} «${described.label}»`);
      options.onAction?.(actions[actions.length - 1]!);
    }
    const target = currentPage && !currentPage.isClosed() ? currentPage : active;
    if (target && !target.isClosed()) {
      await target.waitForLoadState('domcontentloaded', { timeout: 8000 }).catch(() => undefined);
      await target.waitForTimeout(200).catch(() => undefined);
      const after = await fingerprint(target).catch(() => currentPrint);
      if (after !== currentPrint) await adopt(target);
    }
    if (pending.length) schedule();
  };

  schedule = () => {
    if (timer) return;
    timer = setTimeout(() => {
      timer = undefined;
      chain = chain.then(() => flush()).catch((error: unknown) => {
        console.error(error instanceof Error ? error.message : error);
      });
    }, 500);
  };

  const poll = setInterval(() => {
    void (async () => {
      for (const target of [...pages]) await pullQueue(target);
    })().catch(() => undefined);
  }, 250);

  try {
    try {
      await page.goto(options.url, { waitUntil: 'domcontentloaded', timeout: 45000 });
    } catch (error) {
      throw new Error(explainOpenError(error, options.url));
    }
    await page.waitForLoadState('networkidle', { timeout: 8000 }).catch(() => undefined);
    current = await captureScreen(page, captureDir, 's1', flushAssets);
    screens.push(current);
    currentPage = page;
    currentPrint = await fingerprint(page).catch(() => page.url());
    if (pending.length) schedule();
    console.log(`Стартовый экран: «${current.title}»`);
    options.onSession?.({
      stop: async () => {
        if (browser.isConnected()) await browser.close();
      },
      setPaused: (value) => {
        paused = value;
      },
    });

    const finished = new Promise<void>((resolve) => {
      browser.on('disconnected', () => resolve());
    });
    const onSigint = () => {
      console.log('\nОстанавливаю запись…');
      void browser.close();
    };
    process.on('SIGINT', onSigint);
    if (options.onReady) await options.onReady(page);
    if (browser.isConnected()) await finished;
    process.off('SIGINT', onSigint);
    if (timer) clearTimeout(timer);
    timer = undefined;
    await chain.catch(() => undefined);
    await flush().catch(() => undefined);
  } finally {
    clearInterval(poll);
    await flushAssets().catch(() => undefined);
    if (browser.isConnected()) await browser.close().catch(() => undefined);
  }

  if (!current || screens.length === 0) throw new Error('Не удалось сохранить ни одного экрана');
  const bundle: CaptureBundle = {
    id: options.lessonId,
    title: options.lessonName,
    description: `Запись ${options.url}. Пройдите шаги так же, как при записи.`,
    sourceSite: new URL(options.url).origin,
    startUrl: options.url,
    viewport: VIEWPORT,
    capturedAt: new Date().toISOString(),
    screens,
    actions,
    assets,
    preferredRender: options.mode,
  };
  await writeFile(path.join(captureDir, 'capture.json'), JSON.stringify(bundle, null, 2));
  console.log(`Запись сохранена: ${screens.length} экранов, ${actions.length} действий, ${assets.length} ресурсов`);
  return captureDir;
}

export function lessonIdFromName(name: string): string {
  const ascii = name.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
  if (ascii.length >= 2) return ascii.slice(0, 48);
  return `urok-${createHash('sha1').update(name).digest('hex').slice(0, 8)}`;
}
