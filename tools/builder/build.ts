import { mkdir, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { PNG } from 'pngjs';
import pixelmatch from 'pixelmatch';
import type { Browser, Page } from 'playwright';
import { buildInstruction, chooseRenderMode, type Hotspot, type Lesson, type LessonStep, type ScreenFidelity } from '../../shared/lesson.ts';
import type { CaptureBundle, CaptureScreen } from '../capture/types.ts';
import { applyHotspots, localizeHtml, readSnapshot, rebuildRrweb } from './html.ts';

const VIEWPORT = { width: 1280, height: 800 };

type Variant = {
  name: 'rrweb' | 'freeze';
  html: string | null;
  targets: number;
  similarity: number | null;
  error?: string;
};

function similarityOf(originalPath: string, replayPath: string): number | null {
  const left = PNG.sync.read(readFileSync(originalPath));
  const right = PNG.sync.read(readFileSync(replayPath));
  if (left.width !== right.width || left.height !== right.height) return null;
  const mismatched = pixelmatch(left.data, right.data, null, left.width, left.height, { threshold: 0.1 });
  return 1 - mismatched / (left.width * left.height);
}

function countTargets(html: string, selectors: string[], pageUrl: string): number {
  const { missing } = applyHotspots(html, selectors.map((selector, index) => ({
    hotspotId: `probe-${index}`,
    selector,
    kind: 'clickable' as const,
    label: '',
    box: { x: 0, y: 0, width: 0, height: 0 },
  })), pageUrl);
  return selectors.length - missing.length;
}

async function prepareVariant(
  name: 'rrweb' | 'freeze',
  rawHtml: string | null,
  screen: CaptureScreen,
  captureDir: string,
  assetDir: string,
  assets: CaptureBundle['assets'],
  selectors: string[],
  warnings: string[],
): Promise<string | null> {
  if (!rawHtml) return null;
  try {
    const localized = await localizeHtml({
      html: rawHtml,
      pageUrl: screen.url,
      captureDir,
      assetDir,
      assets,
    });
    if (localized.missingAssets.length) {
      warnings.push(`${screen.id}/${name}: не скачано ресурсов ${localized.missingAssets.length}`);
    }
    const marked = applyHotspots(localized.html, screen.elements, screen.url);
    const missed = selectors.filter((selector) => marked.missing.includes(selector));
    if (missed.length) warnings.push(`${screen.id}/${name}: не найден селектор ${missed.join(', ')}`);
    return marked.html;
  } catch (error) {
    const message = error instanceof Error ? error.message : String(error);
    warnings.push(`${screen.id}/${name}: ${message}`);
    return null;
  }
}

async function shoot(page: Page, url: string, file: string) {
  await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 25000 });
  await page.evaluate(() => Promise.race([
    document.fonts.ready,
    new Promise((resolve) => setTimeout(resolve, 2000)),
  ]));
  await page.waitForTimeout(250);
  await page.screenshot({ path: file, fullPage: false });
}

function alignActions(bundle: CaptureBundle): CaptureBundle {
  const screens = bundle.screens.map((screen) => ({ ...screen, elements: [...screen.elements] }));
  const actions = bundle.actions.map((action) => {
    const screen = screens.find((item) => item.id === action.screenId);
    if (!screen) return action;
    const twin = screen.elements.find((element) =>
      element.hotspotId !== action.hotspotId
      && element.label === action.label
      && Math.abs(element.box.x - action.box.x) < 3
      && Math.abs(element.box.y - action.box.y) < 3
      && Math.abs(element.box.width - action.box.width) < 3
    );
    if (!twin) return action;
    const extra = screen.elements.findIndex((element) => element.hotspotId === action.hotspotId);
    if (extra >= 0) screen.elements.splice(extra, 1);
    return { ...action, hotspotId: twin.hotspotId, selector: twin.selector };
  });
  return { ...bundle, screens, actions };
}

function lessonSteps(bundle: CaptureBundle): LessonStep[] {
  return bundle.actions.filter((action) => !action.prelude).map((action, index) => {
    const screen = bundle.screens.find((item) => item.id === action.screenId);
    const next = bundle.screens.find((item) => item.id === action.nextScreenId);
    const actionType = action.kind === 'fill' ? 'input' : action.kind === 'select' ? 'select' : action.kind === 'press' ? 'key' : 'click';
    const confirm = action.kind === 'fill' ? action.confirm ?? 'match' : null;
    return {
      id: `step-${index + 1}`,
      index,
      screenId: action.screenId,
      action: actionType,
      hotspotId: action.hotspotId,
      expectedValue: action.value,
      confirm,
      nextScreenId: action.nextScreenId,
      instruction: buildInstruction({
        screenTitle: screen?.title ?? action.screenId,
        action: actionType,
        label: action.label,
        value: action.value,
        nextTitle: next?.title ?? null,
        confirm,
      }),
      hint: '',
      errorText: '',
    };
  });
}

function hotspotsFor(screen: CaptureScreen): Hotspot[] {
  const seen = new Set<string>();
  const hotspots: Hotspot[] = [];
  for (const element of screen.elements) {
    if (seen.has(element.hotspotId)) continue;
    if (element.box.width < 2 || element.box.height < 2) continue;
    seen.add(element.hotspotId);
    const hotspot: Hotspot = {
      id: element.hotspotId,
      kind: element.kind,
      label: element.label,
      selector: element.selector,
      box: element.box,
    };
    if (element.inputType) hotspot.inputType = element.inputType;
    if (element.placeholder) hotspot.placeholder = element.placeholder;
    hotspots.push(hotspot);
  }
  return hotspots;
}

async function renderCompare(page: Page, compareDir: string, file: string, title: string, similarity: number) {
  const percent = `${Math.round(similarity * 1000) / 10}%`;
  const frame = path.join(compareDir, 'frame.html');
  await writeFile(frame, `<!DOCTYPE html>
<html lang="ru"><head><meta charset="utf-8"><style>
  body { margin: 0; background: #221e18; color: #f6f1e6; font-family: sans-serif; }
  h1 { font-size: 18px; margin: 0; padding: 14px 16px 0; }
  .meta { padding: 4px 16px 0; color: #d9cbb3; font-size: 14px; }
  .row { display: flex; gap: 12px; padding: 12px 16px 16px; }
  figure { margin: 0; }
  figcaption { font-weight: 700; margin-bottom: 6px; }
  img { width: 640px; height: auto; display: block; background: white; border: 1px solid #5c5346; }
</style></head>
<body>
  <h1>${title}</h1>
  <div class="meta">Совпадение пикселей: ${percent}</div>
  <div class="row">
    <figure><figcaption>Оригинал</figcaption><img src="original.png"></figure>
    <figure><figcaption>Повтор DOM</figcaption><img src="replay.png"></figure>
  </div>
</body></html>`);
  await page.goto(`file://${frame}`, { waitUntil: 'load' });
  await page.screenshot({ path: file, fullPage: true });
  await rm(frame, { force: true });
}

export async function buildCapture(
  captureDir: string,
  browser: Browser,
  origin: string,
  lessonsRoot = path.resolve('lessons'),
): Promise<Lesson> {
  const bundle = alignActions(JSON.parse(await readFile(path.join(captureDir, 'capture.json'), 'utf8')) as CaptureBundle);
  const lessonDir = path.join(lessonsRoot, bundle.id);
  const assetDir = path.join(lessonDir, 'assets');
  await mkdir(path.join(lessonDir, 'screens'), { recursive: true });
  await mkdir(path.join(lessonDir, 'original'), { recursive: true });
  await mkdir(path.join(lessonDir, 'replay'), { recursive: true });
  await mkdir(path.join(lessonDir, 'compare'), { recursive: true });
  await mkdir(assetDir, { recursive: true });

  const warnings: string[] = [];
  const fidelity: ScreenFidelity[] = [];
  const page = await browser.newPage({ viewport: VIEWPORT, deviceScaleFactor: 1, locale: 'ru-RU' });
  const comparePage = await browser.newPage({ viewport: { width: 1360, height: 900 }, deviceScaleFactor: 1 });

  try {
    for (const screen of bundle.screens) {
      const selectors = bundle.actions.filter((action) => action.screenId === screen.id).map((action) => action.selector);
      let rrwebRaw: string | null = null;
      try {
        const snapshot = await readSnapshot(path.join(captureDir, screen.rrwebFile));
        rrwebRaw = rebuildRrweb(snapshot, screen.url);
      } catch (error) {
        warnings.push(`${screen.id}/rrweb: ${error instanceof Error ? error.message : String(error)}`);
      }
      const freezeRaw = await readFile(path.join(captureDir, screen.frozenHtmlFile), 'utf8');
      const rrwebHtml = await prepareVariant('rrweb', rrwebRaw, screen, captureDir, assetDir, bundle.assets, selectors, warnings);
      const freezeHtml = await prepareVariant('freeze', freezeRaw, screen, captureDir, assetDir, bundle.assets, selectors, warnings);
      const variants: Variant[] = [];
      for (const [name, html] of [['rrweb', rrwebHtml], ['freeze', freezeHtml]] as const) {
        if (!html) {
          variants.push({ name, html: null, targets: 0, similarity: null });
          continue;
        }
        const tempName = `${screen.id}.${name}.html`;
        await writeFile(path.join(lessonDir, 'screens', tempName), html);
        const replayFile = path.join(lessonDir, 'replay', `${screen.id}.${name}.png`);
        let similarity: number | null = null;
        try {
          await shoot(page, `${origin}/${bundle.id}/screens/${tempName}`, replayFile);
          similarity = similarityOf(path.join(captureDir, screen.screenshotFile), replayFile);
        } catch (error) {
          warnings.push(`${screen.id}/${name}/screenshot: ${error instanceof Error ? error.message : String(error)}`);
        }
        variants.push({
          name,
          html,
          targets: countTargets(html, selectors, screen.url),
          similarity,
        });
      }
      const required = selectors.length;
      const eligible = variants.filter((item) => item.html && item.targets === required && item.similarity !== null);
      const pool = eligible.length ? eligible : variants.filter((item) => item.html);
      pool.sort((a, b) => {
        const delta = (b.similarity ?? -1) - (a.similarity ?? -1);
        if (Math.abs(delta) > 0.005) return delta;
        return a.name === 'rrweb' ? -1 : 1;
      });
      const chosen = pool[0];
      if (!chosen?.html) throw new Error(`Нет HTML для экрана ${screen.id}`);
      await writeFile(path.join(lessonDir, 'screens', `${screen.id}.html`), chosen.html);
      const chosenReplay = path.join(lessonDir, 'replay', `${screen.id}.png`);
      const variantReplay = path.join(lessonDir, 'replay', `${screen.id}.${chosen.name}.png`);
      if (existsSync(variantReplay)) {
        await writeFile(chosenReplay, await readFile(variantReplay));
      }
      await rm(path.join(lessonDir, 'screens', `${screen.id}.rrweb.html`), { force: true });
      await rm(path.join(lessonDir, 'screens', `${screen.id}.freeze.html`), { force: true });
      await rm(path.join(lessonDir, 'replay', `${screen.id}.rrweb.png`), { force: true });
      await rm(path.join(lessonDir, 'replay', `${screen.id}.freeze.png`), { force: true });
      const originalTarget = path.join(lessonDir, 'original', `${screen.id}.png`);
      await writeFile(originalTarget, await readFile(path.join(captureDir, screen.screenshotFile)));
      const score = chosen.similarity ?? 0;
      if (existsSync(chosenReplay)) {
        const compareDir = path.join(lessonDir, 'compare');
        await writeFile(path.join(compareDir, 'original.png'), await readFile(originalTarget));
        await writeFile(path.join(compareDir, 'replay.png'), await readFile(chosenReplay));
        await renderCompare(
          comparePage,
          compareDir,
          path.join(compareDir, `${screen.id}.png`),
          `${bundle.title} — ${screen.title}`,
          score,
        );
        await rm(path.join(compareDir, 'original.png'), { force: true });
        await rm(path.join(compareDir, 'replay.png'), { force: true });
      }
      const rrweb = variants.find((item) => item.name === 'rrweb');
      const freeze = variants.find((item) => item.name === 'freeze');
      fidelity.push({
        screenId: screen.id,
        chosen: chosen.name,
        rrwebSimilarity: rrweb?.similarity ?? null,
        freezeSimilarity: freeze?.similarity ?? null,
        chosenSimilarity: score,
      });
      console.log(
        `  ${screen.id}: ${chosen.name}, сходство ${Math.round(score * 1000) / 10}% (rrweb ${fmt(rrweb?.similarity ?? null)}, freeze ${fmt(freeze?.similarity ?? null)})`,
      );
      await new Promise<void>((resolve) => setImmediate(resolve));
    }
  } finally {
    await page.close();
    await comparePage.close();
  }

  const steps = lessonSteps(bundle);
  const lesson: Lesson = {
    schemaVersion: 1,
    id: bundle.id,
    title: bundle.title,
    description: bundle.description,
    sourceSite: bundle.sourceSite,
    viewport: bundle.viewport,
    screens: bundle.screens.map((screen) => ({
      id: screen.id,
      title: screen.title,
      url: screen.url,
      html: `screens/${screen.id}.html`,
      screenshot: `original/${screen.id}.png`,
      hotspots: hotspotsFor(screen),
      viewport: screen.viewport,
    })),
    steps,
    meta: {
      generatedAt: new Date().toISOString(),
      generator: 'ui-trainer-capture-poc',
      stepCount: steps.length,
      manuallyEditedSteps: 0,
      autoStepRatio: steps.length === 0 ? 1 : 1,
      fidelity,
      warnings,
      defaultRenderMode: chooseRenderMode(
        bundle.preferredRender ?? 'auto',
        fidelity.map((item) => item.chosenSimilarity),
      ),
    },
  };
  await writeFile(path.join(lessonDir, 'lesson.json'), JSON.stringify(lesson, null, 2));
  return lesson;
}

function fmt(value: number | null): string {
  if (value === null) return '—';
  return `${Math.round(value * 1000) / 10}%`;
}

export async function writeCatalog(root = path.resolve('lessons')) {
  if (!existsSync(root)) return;
  const entries = await readdir(root, { withFileTypes: true });
  const items = [];
  for (const entry of entries) {
    if (!entry.isDirectory()) continue;
    const file = path.join(root, entry.name, 'lesson.json');
    if (!existsSync(file)) continue;
    const lesson = JSON.parse(await readFile(file, 'utf8')) as Lesson;
    items.push({
      id: lesson.id,
      title: lesson.title,
      description: lesson.description,
      sourceSite: lesson.sourceSite,
      screens: lesson.screens.length,
      steps: lesson.steps.length,
      autoStepRatio: lesson.meta.autoStepRatio,
    });
  }
  items.sort((a, b) => a.title.localeCompare(b.title, 'ru'));
  await writeFile(path.join(root, 'index.json'), JSON.stringify(items, null, 2));
}
