import { existsSync } from 'node:fs'
import { createRequire } from 'node:module'
import os from 'node:os'
import path from 'node:path'
import type { Browser } from 'playwright'

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

export function playwrightChromiumPath(): string {
  const require = createRequire(typeof __filename === 'string' ? __filename : import.meta.url)
  const playwright = require('playwright') as { chromium: { executablePath: () => string } }
  return playwright.chromium.executablePath()
}

export function chromiumInstalled(
  exists: (file: string) => boolean = existsSync,
  executablePath: () => string = playwrightChromiumPath,
): boolean {
  try {
    const file = executablePath()
    return Boolean(file) && exists(file)
  } catch {
    return false
  }
}

export function detectBrowsers(
  exists: (file: string) => boolean = existsSync,
  chromiumReady: (existsFn: (file: string) => boolean) => boolean = (existsFn) => chromiumInstalled(existsFn),
): BrowserOption[] {
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
    { id: 'chromium', label: 'Встроенный Chromium', available: chromiumReady(exists), executablePath: '', channel: '' },
    ...found,
    { id: 'custom', label: 'Другой (путь)', available: true, executablePath: '', channel: '' },
  ]
}

export type BrowserLaunch = { channel?: string; executablePath?: string }

export function resolveBrowserLaunch(choice: BrowserId, customPath: string, options = detectBrowsers()): BrowserLaunch {
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

function sameLaunch(left: BrowserLaunch, right: BrowserLaunch): boolean {
  return (left.channel ?? '') === (right.channel ?? '') && (left.executablePath ?? '') === (right.executablePath ?? '')
}

export function headlessLaunchAttempts(
  preferred: BrowserLaunch,
  options = detectBrowsers(),
  chromiumReady = chromiumInstalled(),
): BrowserLaunch[] {
  const attempts: BrowserLaunch[] = []
  const add = (item: BrowserLaunch) => {
    if (attempts.some((existing) => sameLaunch(existing, item))) return
    attempts.push(item)
  }
  if (preferred.executablePath || preferred.channel) add(preferred)
  else if (chromiumReady) add({})
  if (chromiumReady) add({})
  const chrome = options.find((item) => item.id === 'chrome' && item.available && item.executablePath)
  if (chrome) add({ executablePath: chrome.executablePath })
  add({ channel: 'chrome' })
  add({ channel: 'msedge' })
  return attempts
}

export function resolveHeadlessLaunch(
  preferred: BrowserLaunch,
  options = detectBrowsers(),
  chromiumReady = chromiumInstalled(),
): BrowserLaunch {
  const attempts = headlessLaunchAttempts(preferred, options, chromiumReady)
  if (!chromiumReady) {
    const system = attempts.find((item) => item.executablePath)
    if (system) return system
  }
  const first = attempts[0]
  if (!first) throw new Error('Не найден браузер для сборки урока. Установите Chromium или Google Chrome.')
  return first
}

export function assertRecordingBrowser(
  choice: BrowserId,
  customPath: string,
  options = detectBrowsers(),
  chromiumReady = chromiumInstalled(),
): BrowserLaunch {
  const system = options.some((item) => (item.id === 'chrome' || item.id === 'yandex') && item.available)
  if (!chromiumReady && !system && choice !== 'custom') {
    throw new Error('Не найден браузер для записи. Установите Chromium или Google Chrome.')
  }
  if (choice === 'chromium' && !chromiumReady) {
    throw new Error('Chromium не установлен. Выберите Google Chrome или установите Chromium.')
  }
  return resolveBrowserLaunch(choice, customPath, options)
}

export async function launchHeadless(
  preferred: BrowserLaunch,
  options = detectBrowsers(),
  chromiumReady = chromiumInstalled(),
): Promise<Browser> {
  const { chromium } = await import('playwright')
  const attempts = headlessLaunchAttempts(preferred, options, chromiumReady)
  let last: unknown
  for (const attempt of attempts) {
    try {
      const launch: { headless: true; channel?: string; executablePath?: string } = { headless: true }
      if (attempt.channel) launch.channel = attempt.channel
      if (attempt.executablePath) launch.executablePath = attempt.executablePath
      return await chromium.launch(launch)
    } catch (error) {
      last = error
    }
  }
  const detail = last instanceof Error ? last.message : String(last ?? '')
  throw new Error(
    detail
      ? `Не найден браузер для сборки урока. Установите Chromium или Google Chrome. ${detail}`
      : 'Не найден браузер для сборки урока. Установите Chromium или Google Chrome.',
  )
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
