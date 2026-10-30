import { mkdir } from 'node:fs/promises';
import path from 'node:path';
import { chromium, type Page } from 'playwright';

const base = process.env.TRAINER_URL ?? 'http://127.0.0.1:43127';
const out = path.resolve('artifacts/player');

async function shot(page: Page, name: string) {
  await page.screenshot({ path: path.join(out, name), fullPage: false });
  console.log('shot', name);
}

async function openLesson(page: Page, title: string) {
  await page.goto(base);
  await page.locator('article', { hasText: title }).getByRole('button', { name: 'Открыть урок' }).click();
  await page.locator('iframe, .shot-frame img').first().waitFor({ state: 'visible', timeout: 15000 });
  await page.waitForTimeout(400);
}

const browser = await chromium.launch({ headless: true });
await mkdir(out, { recursive: true });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, locale: 'ru-RU', deviceScaleFactor: 1 });

await page.goto(base);
await page.getByRole('heading', { name: 'Уроки из записи экрана' }).waitFor();
await shot(page, '01-catalog.png');

await openLesson(page, 'Найти статью и открыть вкладку');
await shot(page, '02-wiki-try-dom.png');

const frame = page.frameLocator('iframe');
const search = frame.locator('#searchInput');
await search.fill('не то');
await search.press('Enter');
await page.getByRole('alert').waitFor();
await shot(page, '03-wiki-wrong-input.png');
await search.fill('ещё ошибка');
await search.press('Enter');
await page.getByText('Подсказка:').waitFor();
await shot(page, '03b-wiki-hint.png');

await search.fill('извещение о закупке');
await page.getByText('На экране «Главная» нажмите «Найти»').first().waitFor();
await frame.locator('#searchform button').click();
await page.getByText('Единая информационная система в сфере закупок').first().waitFor();
await shot(page, '04-wiki-results-step.png');
await frame.locator('[data-hotspot="h3afc0f5a"]').click();
await page.getByText('нажмите «Обсуждение»').first().waitFor();
await frame.locator('#ca-talk a').click();
await page.getByRole('heading', { name: 'Урок пройден' }).waitFor();
await shot(page, '05-wiki-try-done.png');

await page.getByRole('button', { name: 'Скриншот', exact: true }).click();
await page.getByRole('button', { name: 'Сначала' }).click();
await page.locator('.shot-input').waitFor();
await shot(page, '06-wiki-try-screenshot.png');
await page.locator('.shot-input').fill('извещение о закупке');
await page.getByText('нажмите «Найти»').first().waitFor();
await page.locator('button.spot[data-hotspot="h3b422e0"]').click();
await page.getByText('Единая информационная система').first().waitFor();
await page.locator('button.spot[data-hotspot="h3afc0f5a"]').click();
await page.getByText('нажмите «Обсуждение»').first().waitFor();
await page.locator('button.spot[data-hotspot="h2eedfc50"]').click();
await page.getByRole('heading', { name: 'Урок пройден' }).waitFor();
await shot(page, '07-wiki-screenshot-done.png');

await page.getByRole('tab', { name: 'Покажи' }).click();
await page.waitForTimeout(500);
await shot(page, '08-wiki-show.png');

await page.getByRole('tab', { name: 'Проверь' }).click();
await page.locator('.shot-frame, iframe').first().waitFor();
await page.getByRole('button', { name: 'Разметка', exact: true }).click();
await frame.locator('#searchInput').fill('ошибка');
await frame.locator('#searchInput').press('Enter');
await page.getByRole('alert').waitFor();
await shot(page, '09-wiki-test-error.png');

await openLesson(page, 'Добавить задачи и переключить фильтр');
await shot(page, '10-todo-try-dom.png');
const todo = page.frameLocator('iframe');
await todo.locator('.new-todo').fill('не та задача');
await todo.locator('.new-todo').press('Enter');
await page.getByRole('alert').waitFor();
await shot(page, '11-todo-wrong-input.png');
await todo.locator('.new-todo').fill('Купить молоко');
await todo.locator('.new-todo').press('Enter');
await page.getByText('Написать отчёт').first().waitFor();
await todo.locator('.new-todo').fill('Написать отчёт');
await todo.locator('.new-todo').press('Enter');
await page.getByText('Toggle Todo').first().waitFor();
await todo.locator('.todo-list li .toggle').first().click();
await page.getByText('Completed').first().waitFor();
await todo.locator('[data-hotspot="h5a81b6f8"]').click();
await page.getByRole('heading', { name: 'Урок пройден' }).waitFor();
await shot(page, '12-todo-done.png');

const mobile = await browser.newPage({ viewport: { width: 390, height: 844 }, locale: 'ru-RU' });
await mobile.goto(base);
await mobile.getByRole('heading', { name: 'Уроки из записи экрана' }).waitFor();
await mobile.screenshot({ path: path.join(out, '13-mobile-catalog.png') });
await mobile.locator('article', { hasText: 'Найти статью' }).getByRole('button', { name: 'Открыть урок' }).click();
await mobile.locator('iframe').waitFor();
await mobile.screenshot({ path: path.join(out, '14-mobile-player.png') });

await browser.close();
console.log('player flows passed');
