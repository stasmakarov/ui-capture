import { createServer } from 'node:http'
import { mkdtemp, readFile, rm, writeFile, mkdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { afterAll, describe, expect, it } from 'vitest'
import { chromium } from 'playwright'
import { lessonIdFromName, recordInteractive } from '../capture/interactive.ts'
import { publishCapture } from '../pipeline/publish.ts'

const root = await mkdtemp(path.join(tmpdir(), 'uitrainer-'))

function siteServer(dir: string): Promise<{ url: string; close: () => Promise<void> }> {
  const server = createServer(async (request, response) => {
    const name = (request.url ?? '/').split('?')[0]
    const file = name === '/' ? 'index.html' : name?.replace(/^\//, '')
    try {
      const body = await readFile(path.join(dir, file ?? 'index.html'))
      const ext = path.extname(file ?? '')
      const type = ext === '.css' ? 'text/css' : ext === '.svg' ? 'image/svg+xml' : 'text/html; charset=utf-8'
      response.setHeader('Content-Type', type)
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
      resolve({
        url: `http://127.0.0.1:${port}`,
        close: () => new Promise((done) => server.close(() => done())),
      })
    })
  })
}

describe('локальная запись и file://', () => {
  afterAll(async () => {
    await rm(root, { recursive: true, force: true })
  })

  it('собирает тренажёр с вшитой иконкой и проходит «Покажи»', async () => {
    const site = path.join(root, 'site')
    await mkdir(site)
    await writeFile(path.join(site, 'mark.svg'), '<svg xmlns="http://www.w3.org/2000/svg" id="trainer-mark-icon"><rect width="8" height="8"/></svg>')
    await writeFile(path.join(site, 'app.css'), 'body { background: #f4f1ea; } .mark { width: 32px; height: 32px; } #panel { border-top: 4px solid #17375e; }')
    await writeFile(path.join(site, 'index.html'), `<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8"><title>Поиск извещения</title><link rel="stylesheet" href="app.css"></head><body>
      <img class="mark" src="mark.svg" alt="метка">
      <h1>Поиск извещения</h1>
      <form action="results.html"><label>Номер <input id="q" name="q"></label> <button type="submit">Найти</button></form>
    </body></html>`)
    await writeFile(path.join(site, 'results.html'), `<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8"><title>Карточка извещения</title><link rel="stylesheet" href="app.css"></head><body>
      <img class="mark" src="mark.svg" alt="метка">
      <h1 id="card">Карточка извещения</h1>
      <button id="tab-docs" type="button">Документы</button>
      <div id="panel">Общие сведения</div>
      <script>document.getElementById('tab-docs').onclick = () => { document.getElementById('panel').textContent = 'Документы извещения' }</script>
    </body></html>`)
    const server = await siteServer(site)
    const lessons = path.join(root, 'lessons')
    const lessonName = 'Найти извещение'
    const lessonId = lessonIdFromName(lessonName)
    try {
      const captureDir = await recordInteractive({
        url: `${server.url}/index.html`,
        lessonName,
        lessonId,
        mode: 'auto',
        capturesDir: path.join(root, 'captures'),
        headless: true,
        onReady: async (page) => {
          await page.locator('#q').fill('0123456789')
          await page.locator('button[type="submit"]').click()
          await page.locator('#tab-docs').waitFor()
          await page.locator('#tab-docs').click()
          await page.waitForFunction(() => document.getElementById('panel')?.textContent === 'Документы извещения')
          await page.waitForTimeout(1500)
          await page.close()
        },
      })
      const htmlFile = path.join(lessons, 'trainer.html')
      await publishCapture(captureDir, lessons, htmlFile)
      const lesson = JSON.parse(await readFile(path.join(lessons, lessonId, 'lesson.json'), 'utf8')) as { steps: Array<{ action: string; expectedValue: string }> }
      expect(lesson.steps.some((step) => step.action === 'input' && step.expectedValue === '0123456789')).toBe(true)
      expect(lesson.steps.some((step) => step.action === 'click')).toBe(true)
      const html = await readFile(htmlFile, 'utf8')
      const embeddedIcon = html.match(/data:image\/svg\+xml;base64,([A-Za-z0-9+/=]+)/)
      expect(embeddedIcon?.[1]).toBeTruthy()
      expect(Buffer.from(embeddedIcon?.[1] ?? '', 'base64').toString('utf8')).toContain('trainer-mark-icon')
      const screenHtml = await readFile(path.join(lessons, lessonId, 'screens', 's1.html'), 'utf8')
      expect(screenHtml).toMatch(/<style[\s>]/i)
      expect(screenHtml.toLowerCase()).toMatch(/f4f1ea|244/)
      const browser = await chromium.launch({ headless: true })
      const player = await browser.newPage({ viewport: { width: 1280, height: 800 } })
      await player.goto(`file://${htmlFile}`)
      await player.getByRole('heading', { name: 'Уроки из записи экрана' }).waitFor()
      await player.getByRole('button', { name: 'Открыть урок' }).click()
      await player.getByRole('tab', { name: 'Покажи' }).click()
      await player.getByRole('heading', { name: 'Урок пройден' }).waitFor({ timeout: 30000 })
      await player.getByRole('tab', { name: 'Попробуй' }).click()
      const frame = player.frameLocator('iframe')
      await frame.locator('#q').fill('нет')
      await frame.locator('#q').press('Enter')
      await player.getByRole('alert').waitFor()
      expect(await player.getByRole('alert').innerText()).toContain('Неверное значение')
      await browser.close()
    } finally {
      await server.close()
    }
  }, 120000)
})
