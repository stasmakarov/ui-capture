import { describe, expect, it } from 'vitest'
import { detectBrowsers, gostBrowserHint, resolveBrowserLaunch, settingsFromSaved } from './browsers.ts'

describe('detectBrowsers', () => {
  it('находит Chrome и Яндекс Браузер по путям в /Applications', () => {
    const present = new Set([
      '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
      '/Applications/Yandex.app/Contents/MacOS/Yandex',
    ])
    const options = detectBrowsers((file) => present.has(file), () => true)
    expect(options.find((item) => item.id === 'chrome')).toMatchObject({ available: true })
    expect(options.find((item) => item.id === 'yandex')?.executablePath).toContain('Yandex.app')
    expect(options.find((item) => item.id === 'chromium')?.available).toBe(true)
  })

  it('помечает встроенный Chromium недоступным, если бинарника нет', () => {
    const options = detectBrowsers(() => false, () => false)
    expect(options.find((item) => item.id === 'chromium')?.available).toBe(false)
  })

  it('помечает отсутствующий Яндекс Браузер и не даёт его запустить', () => {
    const options = detectBrowsers(() => false)
    expect(options.find((item) => item.id === 'yandex')?.available).toBe(false)
    expect(() => resolveBrowserLaunch('yandex', '', options)).toThrow(/не найден/)
  })
})

describe('settingsFromSaved', () => {
  it('переносит старый путь Яндекс Браузера в пункт списка', () => {
    const yandex = '/Applications/Yandex.app/Contents/MacOS/Yandex'
    const options = detectBrowsers((file) => file === yandex)
    expect(settingsFromSaved({ executablePath: yandex }, options)).toEqual({ browser: 'yandex', executablePath: '' })
    expect(settingsFromSaved({ channel: 'chrome' }, options)).toEqual({ browser: 'chrome', executablePath: '' })
    expect(settingsFromSaved({ executablePath: '/opt/browser' }, options)).toEqual({ browser: 'custom', executablePath: '/opt/browser' })
  })
})

describe('gostBrowserHint', () => {
  it('объясняет выбор Яндекс Браузера простыми словами', () => {
    expect(gostBrowserHint).toContain('российские сертификаты ГОСТ')
    expect(gostBrowserHint).toContain('Яндекс Браузер')
  })
})
