import { cp, mkdir, mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { afterAll, describe, expect, it } from 'vitest'
import { chromium, type Page } from 'playwright'
import { startStudio } from './server.ts'

/**
 * Сквозной сценарий M2: тренер в кабинете платформы жмёт «Записать в студии» → студия получает тикет (запасной путь
 * через 127.0.0.1:44731) → в студии «Опубликовать на сервер» → урок черновиком на платформе →
 * «Опубликовать» без проверки (кнопка «Отправить на проверку» рядом, но не обязательна) → «Опубликован».
 * Нужен запущенный бэкенд: UITRAINER_PLATFORM_URL=http://localhost:8090 (профиль dev). Без него тест пропускается.
 * UITRAINER_SCREENS_DIR — куда сохранить скриншоты экранов.
 */
const platformUrl = process.env.UITRAINER_PLATFORM_URL?.replace(/\/+$/, '')
const screensDir = process.env.UITRAINER_SCREENS_DIR
const LESSON_ID = 'spisok-zadach'

async function shot(page: Page, name: string) {
  if (!screensDir) return
  await mkdir(screensDir, { recursive: true })
  await page.waitForTimeout(400)
  await page.screenshot({ path: path.join(screensDir, `${name}.png`) })
}

async function login(page: Page, username: string) {
  await page.goto(`${platformUrl}/login`)
  await page.locator('input[name="username"]').fill(username)
  await page.locator('input[name="password"]').fill(username)
  await page.locator('vaadin-button[slot="submit"], button[type="submit"]').first().click()
  await page.waitForURL((url) => !url.pathname.endsWith('/login'))
}

describe.skipIf(!platformUrl)('платформа: студия публикует урок, он появляется черновиком', () => {
  const cleanup: Array<() => Promise<void>> = []
  afterAll(async () => {
    for (const step of cleanup.reverse()) await step().catch(() => undefined)
  })

  it('тренер: «Записать в студии» → «Опубликовать на сервер» → черновик → опубликован', { timeout: 400_000 }, async () => {
    const root = await mkdtemp(path.join(tmpdir(), 'ui-platform-e2e-'))
    cleanup.push(() => rm(root, { recursive: true, force: true }))
    const lessonsDir = path.join(root, 'lessons')
    await mkdir(lessonsDir, { recursive: true })
    await cp(path.resolve('lessons', LESSON_ID), path.join(lessonsDir, LESSON_ID), { recursive: true })
    const browser = await chromium.launch({ headless: true })
    cleanup.push(() => browser.close())
    const context = await browser.newContext({ viewport: { width: 1440, height: 900 }, locale: 'ru-RU' })
    const page = await context.newPage()

    await page.goto(`${platformUrl}/login`)
    await shot(page, '01-login')
    await login(page, 'trainer')
    await page.goto(`${platformUrl}/lessons`)
    await page.locator('#recordInStudio').waitFor()
    await shot(page, '02-lessons-before')

    // Студия не запущена: схема не сработала, 127.0.0.1:44731 молчит — платформа показывает инструкцию и ссылку
    await page.locator('#recordInStudio').click()
    await page.getByText('Студия не найдена').waitFor({ timeout: 20000 })
    await shot(page, '03a-studio-not-found')
    await page.getByRole('button', { name: 'Закрыть' }).click()

    const studio = await startStudio({ port: 44731, open: false, lessonsDir, capturesDir: path.join(root, 'captures') })
    // закрыть после браузера: открытая вкладка студии держит SSE-соединение
    cleanup.splice(1, 0, () => studio.close())

    const studioTab = context.waitForEvent('page')
    await page.locator('#recordInStudio').click()
    const tab = await studioTab
    await tab.waitForLoadState('domcontentloaded')
    await expect.poll(async () => {
      const response = await fetch(`${studio.url}api/platform`)
      return (await response.json() as { connected: boolean }).connected
    }, { timeout: 20000 }).toBe(true)
    await page.waitForTimeout(500)
    await shot(page, '03-record-in-studio-fallback')

    await tab.reload()
    const publish = tab.getByTestId(`publish-${LESSON_ID}`)
    await publish.waitFor({ timeout: 30000 })
    await tab.setViewportSize({ width: 1280, height: 860 })
    await shot(tab, '04-studio-linked')
    await publish.click()
    await tab.getByTestId('platform-notice').waitFor({ timeout: 120000 })
    expect(await tab.getByTestId('platform-notice').innerText()).toContain('черновиком')
    await shot(tab, '05-studio-published')

    await page.goto(`${platformUrl}/lessons`)
    const row = page.locator('#lessonsGrid vaadin-grid-cell-content', { hasText: 'Добавить задачи и переключить фильтр' }).first()
    await row.waitFor({ timeout: 20000 })
    await expect.poll(() => page.locator('#lessonsGrid .uic-status-draft').count()).toBeGreaterThan(0)
    await shot(page, '06-lessons-draft')

    await row.click()
    await page.locator('#transition-published').waitFor()
    await page.locator('#transition-review').waitFor()
    await shot(page, '07-lesson-card-draft')
    await page.locator('#transition-published').click()
    await page.locator('#transition-archived').waitFor()
    await page.getByText('Журнал переходов').click()
    await shot(page, '09-lesson-journal')

    await page.getByText('Версии', { exact: true }).click()
    await page.locator('[id^="preview-v"]').first().click()
    await page.locator('#trainerPreview').waitFor({ timeout: 20000 })
    await page.waitForTimeout(3000)
    await shot(page, '10-lesson-preview')

    await page.goto(`${platformUrl}/studio-tokens`)
    await page.locator('#tokensGrid').waitFor()
    await page.locator('#tokenName input').fill('MacBook тренера')
    await page.locator('#issueToken').click()
    await page.locator('#issuedTokenValue').waitFor()
    const issued = await page.locator('#issuedTokenValue input').inputValue()
    expect(issued.startsWith('uicst_')).toBe(true)
    await shot(page, '11-studio-token-issued')
    await page.locator('#closeTokenDialog').click()
    await shot(page, '12-studio-tokens')

    await page.goto(`${platformUrl}/lessons`)
    await page.locator('#statusFilter input').fill('Опубликован')
    await page.keyboard.press('Enter')
    await page.waitForTimeout(800)
    expect(await page.locator('#lessonsGrid .uic-status-published').count()).toBeGreaterThan(0)
    await shot(page, '13-lessons-published-filter')

    // Ученик видит только опубликованные уроки
    await context.clearCookies()
    await login(page, 'student')
    await page.goto(`${platformUrl}/lessons`)
    await page.locator('#lessonsGrid').waitFor()
    await page.waitForTimeout(800)
    expect(await page.locator('#lessonsGrid .uic-status-draft').count()).toBe(0)
    await shot(page, '14-student-published-only')
  })
})
