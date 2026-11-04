import { createServer } from 'node:http'
import { mkdtemp, readFile, rm, writeFile, mkdir } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { afterAll, describe, expect, it } from 'vitest'
import { recordInteractive } from './interactive.ts'

const root = await mkdtemp(path.join(tmpdir(), 'ui-nav-'))

function siteServer(dir: string): Promise<{ url: string; close: () => Promise<void> }> {
  const server = createServer(async (request, response) => {
    const name = (request.url ?? '/').split('?')[0] ?? '/'
    const file = name === '/' ? 'index.html' : name.replace(/^\//, '')
    try {
      response.setHeader('Content-Type', 'text/html; charset=utf-8')
      response.end(await readFile(path.join(dir, file)))
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

describe('запись после перехода и новой вкладки', () => {
  afterAll(async () => {
    await rm(root, { recursive: true, force: true })
  })

  it('не теряет клик на новой странице и во вкладке target=_blank', async () => {
    const site = path.join(root, 'site')
    await mkdir(site)
    await writeFile(path.join(site, 'index.html'), `<!DOCTYPE html><html lang="ru"><body>
      <a href="next.html">Дальше</a>
    </body></html>`)
    await writeFile(path.join(site, 'next.html'), `<!DOCTYPE html><html lang="ru"><body>
      <button type="button" id="after">После перехода</button>
      <a href="tab.html" target="_blank" id="open-tab">Открыть вкладку</a>
    </body></html>`)
    await writeFile(path.join(site, 'tab.html'), `<!DOCTYPE html><html lang="ru"><body>
      <button type="button" id="in-tab">Во вкладке</button>
    </body></html>`)
    const server = await siteServer(site)
    try {
      const captureDir = await recordInteractive({
        url: `${server.url}/index.html`,
        lessonName: 'Переход',
        lessonId: 'perehod',
        mode: 'dom',
        capturesDir: path.join(root, 'captures'),
        headless: true,
        onReady: async (page) => {
          await page.getByRole('link', { name: 'Дальше' }).click()
          await page.getByRole('button', { name: 'После перехода' }).click()
          const popupPromise = page.waitForEvent('popup')
          await page.getByRole('link', { name: 'Открыть вкладку' }).click()
          const popup = await popupPromise
          await popup.getByRole('button', { name: 'Во вкладке' }).click()
          await popup.waitForTimeout(1200)
          await popup.close()
          await page.close()
        },
      })
      const bundle = JSON.parse(await readFile(path.join(captureDir, 'capture.json'), 'utf8')) as {
        screens: Array<{ url: string }>
        actions: Array<{ label: string }>
      }
      const labels = bundle.actions.map((action) => action.label)
      expect(labels).toContain('Дальше')
      expect(labels).toContain('После перехода')
      expect(labels).toContain('Открыть вкладку')
      expect(labels).toContain('Во вкладке')
      expect(bundle.screens.length).toBeGreaterThanOrEqual(3)
    } finally {
      await server.close()
    }
  }, 60000)
})
