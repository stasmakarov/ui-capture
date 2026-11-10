import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { createHash } from 'node:crypto';
import { chromium, type Locator, type Page } from 'playwright';
import { loadInpageScript } from './inpageBundle.ts';
import type { Scenario } from './scenarios.ts';
import type { CaptureAction, CaptureBundle, CaptureScreen, DescribedElement, SavedAsset } from './types.ts';

const VIEWPORT = { width: 1280, height: 800 };

type TrainerApi = {
  describe: (el: Element) => DescribedElement;
  listInteractive: () => DescribedElement[];
  freeze: () => string;
  takeSnapshot: () => unknown;
};

function assetFileName(url: string, contentType: string): string {
  const hash = createHash('sha1').update(url).digest('hex').slice(0, 10);
  let base = 'asset';
  try {
    base = path.basename(new URL(url).pathname).replace(/[^a-zA-Z0-9._-]/g, '').slice(0, 48) || 'asset';
  } catch {
    base = 'asset';
  }
  const known = ['.css', '.png', '.jpg', '.jpeg', '.gif', '.webp', '.svg', '.woff', '.woff2', '.ttf', '.otf', '.ico'];
  const ext = path.extname(base).toLowerCase();
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

function trackAssets(page: Page, netDir: string, assets: SavedAsset[]) {
  const inflight = new Set<Promise<void>>();
  const seen = new Set<string>();
  page.on('response', (response) => {
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
        /* redirected or cached body is unavailable */
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

async function captureScreen(
  page: Page,
  captureDir: string,
  screenId: string,
  title: string,
  flushAssets: () => Promise<void>,
): Promise<CaptureScreen> {
  await flushAssets();
  const screenshotFile = `screens/${screenId}.original.png`;
  await page.screenshot({ path: path.join(captureDir, screenshotFile), fullPage: false });
  const elements = await page.evaluate(() => {
    const api = (window as Window & { __trainer?: TrainerApi }).__trainer;
    if (!api) throw new Error('Скрипт снимка не установлен');
    return api.listInteractive();
  });
  const snapshot = await page.evaluate(() => {
    const api = (window as Window & { __trainer?: TrainerApi }).__trainer;
    if (!api) throw new Error('Скрипт снимка не установлен');
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
    if (!api) throw new Error('Скрипт снимка не установлен');
    try {
      return api.freeze();
    } catch (error) {
      return `<!DOCTYPE html><html><body><p>${error instanceof Error ? error.message : 'freeze failed'}</p></body></html>`;
    }
  });
  const frozenHtmlFile = `screens/${screenId}.freeze.html`;
  await writeFile(path.join(captureDir, frozenHtmlFile), frozen);
  return {
    id: screenId,
    title,
    url: page.url(),
    pageTitle: await page.title(),
    viewport: VIEWPORT,
    screenshotFile,
    rrwebFile,
    frozenHtmlFile,
    elements,
  };
}

async function preferScreenElement(
  locator: Locator,
  elements: DescribedElement[],
): Promise<DescribedElement> {
  const handle = await locator.elementHandle();
  if (!handle) return describeTarget(locator);
  const matched = await handle.evaluate((el, candidates) => {
    for (const candidate of candidates) {
      let found: Element | null = null;
      try {
        found = document.querySelector(candidate.selector);
      } catch {
        found = null;
      }
      if (found === el) return candidate;
    }
    return null;
  }, elements);
  await handle.dispose();
  if (matched) return matched;
  return describeTarget(locator);
}

async function describeTarget(locator: Locator): Promise<DescribedElement> {
  return locator.evaluate((el) => {
    const api = (window as Window & { __trainer?: TrainerApi }).__trainer;
    if (!api) throw new Error('Скрипт снимка не установлен');
    return api.describe(el);
  });
}

export async function recordScenario(scenario: Scenario, outRoot = 'captures'): Promise<string> {
  const captureDir = path.resolve(outRoot, scenario.id);
  const netDir = path.join(captureDir, 'net');
  await mkdir(path.join(captureDir, 'screens'), { recursive: true });
  await mkdir(netDir, { recursive: true });
  const script = await loadInpageScript();
  const assets: SavedAsset[] = [];
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage({ viewport: VIEWPORT, locale: 'ru-RU', deviceScaleFactor: 1 });
    const flushAssets = trackAssets(page, netDir, assets);
    await page.addInitScript(script);
    await page.goto(scenario.startUrl, { waitUntil: 'domcontentloaded', timeout: 45000 });
    await page.waitForLoadState('networkidle', { timeout: 12000 }).catch(() => undefined);
    await page.waitForTimeout(600);

    const screens: CaptureScreen[] = [];
    const actions: CaptureAction[] = [];
    let current = await captureScreen(page, captureDir, 's1', scenario.initialScreenTitle, flushAssets);
    screens.push(current);

    for (const step of scenario.steps) {
      const locator = page.locator(step.locator).first();
      await locator.waitFor({ state: 'visible', timeout: 15000 });
      const described = await preferScreenElement(locator, current.elements);
      if (!current.elements.some((item) => item.hotspotId === described.hotspotId)) {
        current.elements.push(described);
      }
      const fromScreenId = current.id;
      if (step.kind === 'fill') {
        await locator.fill(step.value ?? '');
        if (step.confirm === 'enter') await locator.press('Enter');
        else await page.waitForTimeout(250);
      } else if (step.kind === 'press') {
        await locator.press(step.key ?? 'Enter');
      } else if (step.kind === 'select') {
        await locator.selectOption(step.value ?? '');
      } else {
        try {
          await locator.click({ timeout: 8000 });
        } catch {
          await locator.click({ force: true, timeout: 5000 }).catch(async () => {
            await locator.evaluate((el) => {
              if (el instanceof HTMLElement) el.click();
            });
          });
        }
      }

      let nextScreenId: string | null = null;
      if (step.opensScreen) {
        if (step.opensScreen.waitFor) {
          await page.locator(step.opensScreen.waitFor).first().waitFor({ state: 'visible', timeout: 20000 });
        }
        await page.waitForLoadState('networkidle', { timeout: 12000 }).catch(() => undefined);
        await page.waitForTimeout(500);
        const nextId = `s${screens.length + 1}`;
        current = await captureScreen(page, captureDir, nextId, step.opensScreen.title, flushAssets);
        screens.push(current);
        nextScreenId = current.id;
      }

      actions.push({
        screenId: fromScreenId,
        kind: step.kind,
        confirm: step.confirm ?? null,
        value: step.value ?? step.key ?? '',
        hotspotId: described.hotspotId,
        selector: described.selector,
        label: described.label,
        box: described.box,
        nextScreenId,
        prelude: step.prelude ?? false,
      });
      console.log(`  ${scenario.id}: ${step.kind} «${described.label}» → ${nextScreenId ?? 'тот же экран'}`);
    }

    await flushAssets();
    const bundle: CaptureBundle = {
      id: scenario.id,
      title: scenario.title,
      description: scenario.description,
      sourceSite: scenario.sourceSite,
      startUrl: scenario.startUrl,
      viewport: VIEWPORT,
      capturedAt: new Date().toISOString(),
      screens,
      actions,
      assets,
    };
    await writeFile(path.join(captureDir, 'capture.json'), JSON.stringify(bundle, null, 2));
    console.log(`Запись ${scenario.id}: ${screens.length} экранов, ${actions.length} действий, ${assets.length} ресурсов`);
    return captureDir;
  } finally {
    await browser.close();
  }
}
