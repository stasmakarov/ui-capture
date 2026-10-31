import { chromium } from 'playwright'

const file = 'file:///workspace/artifacts/ui-trainer-wiki.html'
const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, locale: 'ru-RU' })
page.on('pageerror', (error) => console.log('PAGEERROR', error.message))
page.on('console', (msg) => {
  if (msg.type() === 'error') console.log('CONSOLE', msg.text())
})

await page.goto(file)
await page.getByRole('heading', { name: 'Уроки из записи экрана' }).waitFor({ timeout: 15000 })
console.log('каталог открыт с диска')

await page.locator('article', { hasText: 'Найти статью' }).getByRole('button', { name: 'Открыть урок' }).click()
await page.getByRole('tab', { name: 'Покажи' }).click()
await page.getByRole('heading', { name: 'Урок пройден' }).waitFor({ timeout: 30000 })
console.log('Покажи: урок пройден')

await page.getByRole('tab', { name: 'Попробуй' }).click()
const frame = page.frameLocator('iframe')
const search = frame.locator('#searchInput')
await search.waitFor({ timeout: 15000 })
await search.fill('не то')
await search.press('Enter')
await page.getByRole('alert').waitFor()
const errorText = await page.getByRole('alert').innerText()
if (!errorText.includes('Неверное значение')) throw new Error(`Не та ошибка: ${errorText}`)
console.log('Попробуй: ошибка на неверный ввод')

await search.fill('извещение о закупке')
await page.getByText('нажмите «Найти»').first().waitFor()
await frame.locator('#searchform button, [data-hotspot="h3b422e0"]').first().click()
await page.getByText('Единая информационная система').first().waitFor()
await frame.locator('[data-hotspot="h3afc0f5a"]').click()
await page.getByText('нажмите «Обсуждение»').first().waitFor()
await frame.locator('#ca-talk a, [data-hotspot="h2eedfc50"]').first().click()
await page.getByRole('heading', { name: 'Урок пройден' }).waitFor()
console.log('Попробуй: урок пройден')

await browser.close()
