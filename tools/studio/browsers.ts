import { existsSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'

export type BrowserId = 'chromium' | 'chrome' | 'yandex' | 'custom'

export type BrowserOption = {
  id: BrowserId
  label: string
  available: boolean
  executablePath: string
  channel: string
}

const home = os.homedir()

export const browserCandidates: Array<{ id: Exclude<BrowserId, 'chromium' | 'custom'>; label: string; paths: string[]; channel: string }> = [
  {
    id: 'chrome',
    label: 'Google Chrome',
    channel: 'chrome',
    paths: [
      '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
      path.join(home, 'Applications/Google Chrome.app/Contents/MacOS/Google Chrome'),
      '/usr/bin/google-chrome',
      '/usr/bin/google-chrome-stable',
      'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
      'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
      path.join(home, 'AppData/Local/Google/Chrome/Application/chrome.exe'),
    ],
  },
  {
    id: 'yandex',
    label: 'Яндекс Браузер',
    channel: '',
    paths: [
      '/Applications/Yandex.app/Contents/MacOS/Yandex',
      '/Applications/Yandex Browser.app/Contents/MacOS/Yandex Browser',
      '/Applications/Yandex Browser.app/Contents/MacOS/Yandex',
      path.join(home, 'Applications/Yandex.app/Contents/MacOS/Yandex'),
      '/usr/bin/yandex-browser',
      path.join(home, 'AppData/Local/Yandex/YandexBrowser/Application/browser.exe'),
    ],
  },
]

export function detectBrowsers(exists: (file: string) => boolean = existsSync): BrowserOption[] {
  const found = browserCandidates.map((candidate) => {
    const executablePath = candidate.paths.find((file) => exists(file)) ?? ''
    return {
      id: candidate.id,
      label: candidate.label,
      available: executablePath.length > 0,
      executablePath,
      channel: candidate.channel,
    }
  })
  return [
    { id: 'chromium', label: 'Встроенный Chromium', available: true, executablePath: '', channel: '' },
    ...found,
    { id: 'custom', label: 'Другой (путь)', available: true, executablePath: '', channel: '' },
  ]
}

export function resolveBrowserLaunch(choice: BrowserId, customPath: string, options = detectBrowsers()): { channel?: string; executablePath?: string } {
  if (choice === 'chromium') return {}
  if (choice === 'custom') {
    if (!customPath.trim()) throw new Error('Укажите путь к браузеру.')
    return { executablePath: customPath.trim() }
  }
  const match = options.find((item) => item.id === choice)
  if (!match?.available || !match.executablePath) {
    throw new Error(`${match?.label ?? 'Браузер'} не найден в /Applications. Выберите «Другой» и укажите путь вручную.`)
  }
  return { executablePath: match.executablePath }
}

export const gostBrowserHint = 'Если сайт не открывается из-за сертификата (российские сертификаты ГОСТ), выберите Яндекс Браузер.'

export type StudioSettings = { browser: BrowserId; executablePath: string }

export function settingsFromSaved(
  parsed: { browser?: string; channel?: string; executablePath?: string },
  options = detectBrowsers(),
): StudioSettings {
  if (parsed.browser === 'chromium' || parsed.browser === 'chrome' || parsed.browser === 'yandex' || parsed.browser === 'custom') {
    return { browser: parsed.browser, executablePath: parsed.executablePath ?? '' }
  }
  const executablePath = parsed.executablePath ?? ''
  if (executablePath) {
    const known = options.find((item) => item.executablePath === executablePath)
    if (known && known.id !== 'chromium' && known.id !== 'custom') return { browser: known.id, executablePath: '' }
    return { browser: 'custom', executablePath }
  }
  if (parsed.channel === 'chrome') return { browser: 'chrome', executablePath: '' }
  return { browser: 'chromium', executablePath: '' }
}
