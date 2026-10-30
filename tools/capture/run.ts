import { existsSync, readFileSync } from 'node:fs';
import { writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { chromium } from 'playwright';
import { scenarioById } from './scenarios.ts';
import { recordScenario } from './record.ts';

async function probeZakupki(): Promise<Record<string, string>> {
  const started = Date.now();
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage({ viewport: { width: 1280, height: 800 }, locale: 'ru-RU' });
    try {
      const response = await page.goto('https://zakupki.gov.ru/epz/main/public/home.html', {
        timeout: 25000,
        waitUntil: 'domcontentloaded',
      });
      return {
        reachable: 'yes',
        status: String(response?.status() ?? ''),
        url: page.url(),
        elapsedMs: String(Date.now() - started),
      };
    } catch (error) {
      const message = error instanceof Error ? error.message.split('\n')[0] : String(error);
      return { reachable: 'no', error: message ?? 'timeout', elapsedMs: String(Date.now() - started) };
    }
  } finally {
    await browser.close();
  }
}

const only = process.argv[2];
const probeFile = path.join('captures', 'zakupki-attempt.json');
const probe = existsSync(probeFile)
  ? JSON.parse(readFileSync(probeFile, 'utf8')) as Record<string, string>
  : await probeZakupki();
await mkdir('captures', { recursive: true });
await writeFile(probeFile, JSON.stringify(probe, null, 2));
console.log('zakupki.gov.ru:', probe.reachable === 'yes' ? 'открылся' : `недоступен (${probe.error ?? 'нет ответа'})`);

for (const scenario of scenarioById(only)) {
  console.log(`\nЗапись «${scenario.title}»`);
  await recordScenario(scenario);
}
