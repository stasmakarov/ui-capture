import { createServer } from 'node:http'
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { afterAll, describe, expect, it } from 'vitest'
import { chromium } from 'playwright'
import { lessonIdFromName } from '../capture/interactive.ts'
import { startStudioDev } from './devStudio.ts'

const root = await mkdtemp(path.join(tmpdir(), 'ui-studio-'))
process.env.UITRAINER_HEADLESS = '1'

function siteServer(dir: string): Promise<{ url: string; close: () => Promise<void> }> {
  const server = createServer(async (request, response) => {
    const name = (request.url ?? '/').split('?')[0]
    const file = name === '/' ? 'index.html' : (name ?? 'index.html').replace(/^\//, '')
    try {
      const body = await readFile(path.join(dir, file))
      const ext = path.extname(file)
      response.setHeader('Content-Type', ext === '.css' ? 'text/css' : ext === '.svg' ? 'image/svg+xml' : 'text/html; charset=utf-8')
      response.end(body)
    } catch {
      response.statusCode = 404
      response.end('no')
    }
  })
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const address = server.address()
      const port = typeof address === 'object' && address ? address.port : 0
      resolve({ url: `http://127.0.0.1:${port}`, close: () => new Promise((done) => server.close(() => done())) })
    })
  })
}

describe('студия', () => {
  afterAll(async () => {
    delete process.env.UITRAINER_HEADLESS
    delete process.env.UITRAINER_WHISPER_SIMULATE
    delete process.env.UITRAINER_MODEL_DIR
    await rm(root, { recursive: true, force: true })
  })

  it('записывает локальную страницу, правит шаг и отдаёт trainer.html', async () => {
    process.env.UITRAINER_WHISPER_SIMULATE = '1'
    process.env.UITRAINER_MODEL_DIR = path.join(root, 'models')
    const siteDir = path.join(root, 'site')
    await mkdir(siteDir)
    await writeFile(path.join(siteDir, 'mark.svg'), '<svg xmlns="http://www.w3.org/2000/svg" id="trainer-mark-icon"><rect width="8" height="8"/></svg>')
    await writeFile(path.join(siteDir, 'app.css'), 'body { background: #f4f1ea; }')
    await writeFile(path.join(siteDir, 'index.html'), `<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8"><link rel="stylesheet" href="app.css"><title>Поиск</title></head><body>
      <img src="mark.svg" alt="метка"><h1>Поиск извещения</h1>
      <form action="results.html"><input id="q" name="q"><button type="submit">Найти</button></form>
      <script>if (location.search.includes('drive=1')) setTimeout(() => { document.querySelector('#q').value = '0123456789'; document.querySelector('button').click() }, 2500)</script>
    </body></html>`)
    await writeFile(path.join(siteDir, 'results.html'), `<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8"><link rel="stylesheet" href="app.css"><title>Карточка</title></head><body>
      <h1 id="card">Карточка</h1><button id="tab-docs" type="button">Документы</button><div id="panel">Общие</div>
      <script>setTimeout(() => document.querySelector('#tab-docs').click(), 600); document.querySelector('#tab-docs').onclick = () => { document.getElementById('panel').textContent = 'Документы извещения' }</script>
    </body></html>`)
    const site = await siteServer(siteDir)
    const lessons = path.join(root, 'lessons')
    const studio = await startStudioDev({ port: 0, open: false, lessonsDir: lessons, capturesDir: path.join(root, 'captures') })
    const browser = await chromium.launch({
      headless: true,
      args: ['--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'],
    })
    const page = await browser.newPage({ viewport: { width: 1280, height: 800 }, locale: 'ru-RU' })
    const shots = path.resolve('artifacts/studio')
    await mkdir(shots, { recursive: true })
    try {
      await page.goto(studio.url)
      await page.getByTestId('platform-status').waitFor()
      await page.getByText('Распознавание речи не установлено').waitFor()
      await page.screenshot({ path: path.join(shots, '00-home-disconnected.png') })
      await page.getByRole('button', { name: 'Новый урок' }).click()
      await page.waitForFunction(() => {
        const select = document.querySelector('select[name="browser"]')
        return !!select && Array.from((select as HTMLSelectElement).options).some((option) => option.textContent?.includes('Встроенный Chromium'))
      })
      await page.getByText('Если сайт не открывается из-за сертификата (российские сертификаты ГОСТ), выберите Яндекс Браузер.').waitFor()
      await page.screenshot({ path: path.join(shots, '01-mic-permission.png') })
      await page.getByRole('button', { name: 'Разрешить микрофон' }).click()
      const micSelect = page.locator('select[name="mic"]')
      await micSelect.waitFor({ state: 'attached' })
      const micLabel = micSelect.locator('option').first()
      await micLabel.waitFor({ state: 'attached' })
      expect(await micLabel.textContent()).not.toMatch(/^(Микрофон|Микрофон не найден|Микрофон без названия)$/)
      await page.waitForFunction(() => {
        const meter = document.querySelector('[data-level]')
        return meter !== null && Number(meter.getAttribute('data-level')) > 0
      })
      await page.locator('input[name="url"]').fill(`${site.url}/index.html?drive=1`)
      await page.locator('input[name="lessonName"]').fill('Найти извещение')
      await page.locator('select[name="browser"]').selectOption('chromium')
      await page.screenshot({ path: path.join(shots, '01-new-lesson.png') })
      await page.getByRole('button', { name: /Установить распознавание речи/ }).click()
      await page.getByRole('button', { name: /Загрузка \d+%/ }).waitFor()
      await page.screenshot({ path: path.join(shots, '05-whisper-progress.png') })
      await page.getByText('Распознавание речи установлено').first().waitFor()
      expect(await page.getByRole('button', { name: /Установить распознавание речи/ }).count()).toBe(0)
      await page.screenshot({ path: path.join(shots, '06-model-installed.png') })
      await page.reload()
      await page.getByText('Распознавание речи установлено').first().waitFor()
      await page.getByRole('button', { name: 'Новый урок' }).click()
      const micSelectAgain = page.locator('select[name="mic"]')
      try {
        await micSelectAgain.waitFor({ state: 'attached', timeout: 3000 })
      } catch {
        await page.getByRole('button', { name: 'Разрешить микрофон' }).click()
        await micSelectAgain.waitFor({ state: 'attached' })
      }
      await page.locator('input[name="url"]').fill(`${site.url}/index.html?drive=1`)
      await page.locator('input[name="lessonName"]').fill('Найти извещение')
      await page.getByRole('button', { name: 'Начать запись' }).click()
      await page.getByRole('heading', { name: 'Запись' }).waitFor({ timeout: 30000 })
      await page.getByText('Найти').first().waitFor({ timeout: 20000 })
      await page.screenshot({ path: path.join(shots, '02-recording.png') })
      await page.waitForTimeout(2000)
      await page.getByRole('button', { name: 'Стоп' }).click()
      await page.getByRole('heading', { name: 'Просмотр урока' }).waitFor({ timeout: 120000 })
      await page.screenshot({ path: path.join(shots, '03-preview.png') })
      await page.getByRole('button', { name: 'Править шаги' }).click()
      await page.getByRole('heading', { name: 'Найти извещение' }).waitFor()
      await page.locator('.screen-preview img').first().waitFor()
      await page.getByLabel('Подсказка').fill('Подсказка из редактора')
      await page.getByText('Есть несохранённые изменения').waitFor()
      await page.getByRole('button', { name: 'Переместить шаг ниже' }).first().waitFor()
      await page.evaluate(() => window.scrollTo(0, 0))
      await page.screenshot({ path: path.join(shots, '04-editor.png') })
      await page.getByRole('button', { name: 'Удалить шаг' }).click()
      await page.getByRole('heading', { name: 'Удалить шаг 1?' }).waitFor()
      await page.screenshot({ path: path.join(shots, '07-confirm-delete.png') })
      await page.getByRole('button', { name: 'Отмена' }).click()
      await page.getByRole('button', { name: 'Сохранить' }).click()
      await page.getByText('Есть несохранённые изменения').waitFor({ state: 'hidden' })
      await page.getByRole('button', { name: 'К просмотру' }).click()
      const saved = JSON.parse(await readFile(path.join(lessons, lessonIdFromName('Найти извещение'), 'lesson.json'), 'utf8')) as { steps: Array<{ hint: string; audio?: string }> }
      expect(saved.steps.some((step) => step.hint === 'Подсказка из редактора')).toBe(true)
      expect(saved.steps.some((step) => step.audio?.endsWith('.wav'))).toBe(true)
      const wav = await readFile(path.join(lessons, lessonIdFromName('Найти извещение'), saved.steps.find((step) => step.audio)?.audio ?? ''))
      expect(wav.subarray(0, 4).toString()).toBe('RIFF')
      await page.getByRole('button', { name: 'К каталогу' }).click()
      await page.getByRole('heading', { name: 'Уроки' }).waitFor()
      await page.screenshot({ path: path.join(shots, '08-catalog.png') })
      const [exported] = await Promise.all([
        page.waitForResponse((response) => response.url().includes('/api/export')),
        page.getByRole('button', { name: 'Скачать trainer.html' }).click(),
      ])
      expect(exported.ok()).toBe(true)
      const trainer = await readFile(path.join(lessons, 'trainer.html'), 'utf8')
      expect(trainer).toContain('Найти извещение')
      expect(trainer).toContain('data:audio/wav')
    } finally {
      await browser.close()
      await studio.close()
      await site.close()
    }
  }, 180000)
})
