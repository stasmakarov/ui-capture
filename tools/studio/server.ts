import { createReadStream, existsSync, statSync } from 'node:fs'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import http from 'node:http'
import { createRequire } from 'node:module'
import path from 'node:path'
import { spawn } from 'node:child_process'
import { STUDIO_API_VERSION, fetchPlatformVersion, isApiCompatible } from './apiVersion.ts'
import { parseLesson, type Lesson, type LessonStep } from '../../shared/lesson.ts'
import { lessonIdFromName, recordInteractive, type RecordingControls } from '../capture/interactive.ts'
import { buildCapture, writeCatalog } from '../builder/build.ts'
import { serveLessons } from '../builder/staticServer.ts'
import { packHtml } from '../pack/packHtml.ts'
import { exportLessonPackage, importLessonPackage } from '../package/lessonPackage.ts'
import { assertRecordingBrowser, chromiumInstalled, detectBrowsers, gostBrowserHint, launchHeadless, settingsFromSaved, type BrowserId, type BrowserLaunch, type StudioSettings } from './browsers.ts'
import { exchangeTicket, forgetPlatformLink, normalizeServer, publicLink, publishLesson, readPlatformLink } from './platform.ts'
import { attachStepAudio, lessonFile } from './attachAudio.ts'
import { downloadWhisper, onWhisperStatus, refreshWhisperModelBytes, transcribeWav, whisperStatus } from './transcribe.ts'
import { loadInpageScript } from '../capture/inpageBundle.ts'
import { handlePresence, sendJson, studioVersionOf } from './presence.ts'

export { loadInpageScript }

export type DevMiddleware = {
  middlewares: (request: http.IncomingMessage, response: http.ServerResponse, next: () => void) => void
  close: () => Promise<void>
}

export type StudioOptions = {
  port: number
  open: boolean
  lessonsDir: string
  capturesDir: string
  staticDir?: string
  studioVersion?: string
  dev?: DevMiddleware
}

type ChromiumInstallPhase = 'idle' | 'installing' | 'ready' | 'error'

type ChromiumInstallStatus = {
  phase: ChromiumInstallPhase
  installed: boolean
  error: string
}

let chromiumInstall: ChromiumInstallStatus = { phase: 'idle', installed: false, error: '' }

function chromiumStatus(): ChromiumInstallStatus {
  const installed = chromiumInstalled()
  if (installed && chromiumInstall.phase !== 'installing') {
    return { phase: 'ready', installed: true, error: '' }
  }
  return { ...chromiumInstall, installed }
}

function browsersPayload() {
  return { options: detectBrowsers(), hint: gostBrowserHint, chromium: chromiumStatus() }
}

type LiveStep = {
  id: string
  label: string
  kind: string
  value: string
  at: number
}

type Session = {
  controls: RecordingControls
  done: Promise<string>
  steps: LiveStep[]
  listeners: http.ServerResponse[]
  paused: boolean
  launch: BrowserLaunch
  url: string
  lessonName: string
  mode: 'dom' | 'screenshot' | 'auto'
  lessonId: string
}

let session: Session | null = null

function settingsFile(lessonsDir: string): string {
  return path.join(lessonsDir, '.studio-settings.json')
}

async function readSettings(lessonsDir: string): Promise<StudioSettings> {
  try {
    const parsed = JSON.parse(await readFile(settingsFile(lessonsDir), 'utf8')) as { browser?: string; channel?: string; executablePath?: string }
    return settingsFromSaved(parsed)
  } catch {
    return { browser: 'chromium', executablePath: '' }
  }
}

function browserChoice(value: string | undefined): BrowserId {
  if (value === 'chrome' || value === 'yandex' || value === 'custom' || value === 'chromium') return value
  return 'chromium'
}

function sendBytes(response: http.ServerResponse, status: number, body: Uint8Array, type: string, filename?: string) {
  response.writeHead(status, {
    'Content-Type': type,
    'Content-Length': body.length,
    ...(filename ? { 'Content-Disposition': `attachment; filename="${filename}"` } : {}),
  })
  response.end(body)
}

function readRaw(request: http.IncomingMessage, limit = 80_000_000): Promise<Buffer> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = []
    let size = 0
    request.on('data', (chunk: Buffer) => {
      size += chunk.length
      if (size > limit) {
        reject(new Error('Слишком большой пакет'))
        request.destroy()
        return
      }
      chunks.push(chunk)
    })
    request.on('end', () => resolve(Buffer.concat(chunks)))
    request.on('error', reject)
  })
}

function readBody(request: http.IncomingMessage, limit = 32_000_000): Promise<string> {
  return new Promise((resolve, reject) => {
    const chunks: Buffer[] = []
    let size = 0
    request.on('data', (chunk: Buffer) => {
      size += chunk.length
      if (size > limit) {
        reject(new Error('Слишком большой запрос'))
        request.destroy()
        return
      }
      chunks.push(chunk)
    })
    request.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
    request.on('error', reject)
  })
}

function broadcast(current: Session, event: unknown) {
  const line = `data: ${JSON.stringify(event)}\n\n`
  current.listeners = current.listeners.filter((listener) => !listener.writableEnded)
  for (const listener of current.listeners) listener.write(line)
}

async function lessonList(lessonsDir: string) {
  await mkdir(lessonsDir, { recursive: true })
  const { readdir } = await import('node:fs/promises')
  const entries = await readdir(lessonsDir, { withFileTypes: true })
  const items = []
  for (const entry of entries) {
    if (!entry.isDirectory()) continue
    const file = path.join(lessonsDir, entry.name, 'lesson.json')
    if (!existsSync(file)) continue
    const lesson = JSON.parse(await readFile(file, 'utf8')) as Lesson
    items.push({
      id: lesson.id,
      title: lesson.title,
      description: lesson.description,
      sourceSite: lesson.sourceSite,
      screens: lesson.screens.length,
      steps: lesson.steps.length,
      autoStepRatio: lesson.meta.autoStepRatio,
    })
  }
  items.sort((a, b) => a.title.localeCompare(b.title, 'ru'))
  return items
}

const STATIC_TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.wav': 'audio/wav',
  '.svg': 'image/svg+xml',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.map': 'application/json',
  '.ico': 'image/x-icon',
}

function sendFile(response: http.ServerResponse, file: string) {
  response.writeHead(200, { 'Content-Type': STATIC_TYPES[path.extname(file)] ?? 'application/octet-stream' })
  createReadStream(file).pipe(response)
}

function serveStatic(staticDir: string, pathname: string, response: http.ServerResponse): boolean {
  const decoded = decodeURIComponent(pathname.split('?')[0] ?? '/')
  const target = path.resolve(staticDir, '.' + (decoded === '/' ? '/index.html' : decoded))
  if (!target.startsWith(path.resolve(staticDir))) return false
  if (existsSync(target) && statSync(target).isFile()) {
    sendFile(response, target)
    return true
  }
  const index = path.join(staticDir, 'index.html')
  if (existsSync(index)) {
    sendFile(response, index)
    return true
  }
  return false
}

export async function startStudio(options: StudioOptions): Promise<{ url: string; close: () => Promise<void> }> {
  void refreshWhisperModelBytes()
  const lessonsDir = options.lessonsDir
  await mkdir(lessonsDir, { recursive: true })

  const server = http.createServer(async (request, response) => {
    try {
      const url = new URL(request.url ?? '/', 'http://127.0.0.1')
      if (url.pathname.startsWith('/api/')) {
        await handleApi(request, response, url, options)
        return
      }
      if (url.pathname.startsWith('/lessons/')) {
        const relative = decodeURIComponent(url.pathname.slice('/lessons/'.length))
        const file = path.resolve(lessonsDir, relative)
        if (!file.startsWith(lessonsDir) || !existsSync(file) || statSync(file).isDirectory()) {
          response.writeHead(404)
          response.end('not found')
          return
        }
        sendFile(response, file)
        return
      }
      if (options.staticDir) {
        if (!serveStatic(options.staticDir, url.pathname, response)) {
          response.writeHead(404)
          response.end('not found')
        }
        return
      }
      if (options.dev) {
        options.dev.middlewares(request, response, () => {
          response.writeHead(404)
          response.end('not found')
        })
        return
      }
      response.writeHead(404)
      response.end('not found')
    } catch (error) {
      sendJson(response, 500, { error: error instanceof Error ? error.message : String(error) })
    }
  })

  await new Promise<void>((resolve, reject) => {
    server.once('error', reject)
    server.listen(options.port, '127.0.0.1', () => resolve())
  })
  const address = server.address()
  const port = typeof address === 'object' && address ? address.port : options.port
  const url = `http://127.0.0.1:${port}/`
  if (options.open) openBrowser(url)
  return {
    url,
    close: async () => {
      if (session) await session.controls.stop().catch(() => undefined)
      await new Promise<void>((resolve) => server.close(() => resolve()))
      await options.dev?.close()
    },
  }
}

function openBrowser(url: string) {
  const child = process.platform === 'win32'
    ? spawn('cmd', ['/c', 'start', '', url], { detached: true, stdio: 'ignore', windowsHide: true })
    : spawn(process.platform === 'darwin' ? 'open' : 'xdg-open', [url], { detached: true, stdio: 'ignore' })
  child.unref()
}

async function handleApi(request: http.IncomingMessage, response: http.ServerResponse, url: URL, options: StudioOptions) {
  const { lessonsDir, capturesDir } = options
  if (url.pathname === '/api/presence') {
    if (handlePresence(request, response, studioVersionOf(options))) return
  }
  if (request.method === 'GET' && url.pathname === '/api/health') {
    sendJson(response, 200, { ok: true })
    return
  }
  if (request.method === 'GET' && url.pathname === '/api/studio-info') {
    const link = await readPlatformLink(lessonsDir)
    const platform = link ? await fetchPlatformVersion(link.server) : null
    sendJson(response, 200, {
      studioVersion: studioVersionOf(options),
      studioApiVersion: STUDIO_API_VERSION,
      port: options.port,
      dataDir: path.dirname(lessonsDir),
      platform: link
        ? {
            connected: true,
            server: link.server,
            platformVersion: platform?.platformVersion ?? null,
            apiVersion: platform?.apiVersion ?? null,
            compatible: isApiCompatible(platform?.apiVersion),
          }
        : { connected: false },
    })
    return
  }
  if (request.method === 'GET' && url.pathname === '/api/settings') {
    sendJson(response, 200, await readSettings(lessonsDir))
    return
  }
  if (request.method === 'PUT' && url.pathname === '/api/settings') {
    const body = JSON.parse(await readBody(request)) as { browser?: string; executablePath?: string }
    const settings: StudioSettings = {
      browser: browserChoice(body.browser),
      executablePath: body.executablePath ?? '',
    }
    await writeFile(settingsFile(lessonsDir), JSON.stringify(settings, null, 2))
    sendJson(response, 200, settings)
    return
  }
  if (request.method === 'GET' && url.pathname === '/api/browsers') {
    sendJson(response, 200, browsersPayload())
    return
  }
  if (request.method === 'POST' && url.pathname === '/api/browsers/install-chromium') {
    if (chromiumInstalled()) {
      chromiumInstall = { phase: 'ready', installed: true, error: '' }
      sendJson(response, 200, browsersPayload())
      return
    }
    if (chromiumInstall.phase !== 'installing') {
      chromiumInstall = { phase: 'installing', installed: false, error: '' }
      void installPlaywrightChromium()
        .then(() => {
          chromiumInstall = { phase: 'ready', installed: true, error: '' }
        })
        .catch((error) => {
          const detail = error instanceof Error ? error.message : String(error)
          chromiumInstall = {
            phase: 'error',
            installed: false,
            error: `Не удалось установить Chromium. Проверьте интернет и нажмите «Установить Chromium» ещё раз. ${detail}`,
          }
        })
    }
    sendJson(response, 200, browsersPayload())
    return
  }
  if (request.method === 'GET' && url.pathname === '/api/whisper') {
    sendJson(response, 200, whisperStatus())
    return
  }
  if (request.method === 'GET' && url.pathname === '/api/whisper/events') {
    response.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    })
    response.write(`data: ${JSON.stringify(whisperStatus())}\n\n`)
    const stop = onWhisperStatus((status) => {
      if (!response.writableEnded) response.write(`data: ${JSON.stringify(status)}\n\n`)
    })
    request.on('close', stop)
    return
  }
  if (request.method === 'POST' && url.pathname === '/api/whisper/download') {
    void downloadWhisper().catch(() => undefined)
    sendJson(response, 200, whisperStatus())
    return
  }
  if (request.method === 'GET' && url.pathname === '/api/platform') {
    sendJson(response, 200, publicLink(await readPlatformLink(lessonsDir)))
    return
  }
  if (request.method === 'DELETE' && url.pathname === '/api/platform') {
    await forgetPlatformLink(lessonsDir)
    sendJson(response, 200, { connected: false })
    return
  }
  if (request.method === 'POST' && url.pathname === '/api/platform/link') {
    // Задание с платформы (запасной путь кнопки «Записать в студии») или из ссылки uitrainer://new.
    // Тело — JSON, но приходит как text/plain из fetch(no-cors), поэтому тип не проверяем.
    const body = JSON.parse(await readBody(request)) as { server?: string; ticket?: string; course?: string }
    if (!body.server || !body.ticket) throw new Error('Нужны server и ticket')
    const server = normalizeServer(body.server)
    const origin = request.headers.origin
    if (origin && origin !== 'null' && new URL(origin).origin !== new URL(server).origin) {
      sendJson(response, 403, { error: `Задание пришло со страницы ${origin}, а сервер в нём ${server}` })
      return
    }
    const link = await exchangeTicket(lessonsDir, server, body.ticket, body.course, options.port)
    sendJson(response, 200, publicLink(link))
    return
  }
  if (request.method === 'GET' && url.pathname === '/api/lessons') {
    sendJson(response, 200, await lessonList(lessonsDir))
    return
  }
  if (request.method === 'POST' && url.pathname === '/api/import') {
    const lesson = await importLessonPackage(new Uint8Array(await readRaw(request)), lessonsDir)
    await writeCatalog(lessonsDir)
    sendJson(response, 200, { id: lesson.id, title: lesson.title })
    return
  }
  const lessonMatch = url.pathname.match(/^\/api\/lessons\/([a-z0-9-]+)(.*)$/)
  if (lessonMatch) {
    const id = lessonMatch[1] ?? ''
    const rest = lessonMatch[2] ?? ''
    if (request.method === 'GET' && rest === '') {
      sendJson(response, 200, JSON.parse(await readFile(lessonFile(lessonsDir, id), 'utf8')))
      return
    }
    if (request.method === 'GET' && rest === '/package') {
      const bytes = await exportLessonPackage(path.join(lessonsDir, id))
      sendBytes(response, 200, bytes, 'application/zip', `${id}.lesson.zip`)
      return
    }
    if (request.method === 'POST' && rest === '/publish') {
      sendJson(response, 200, await publishLesson(lessonsDir, id))
      return
    }
    if (request.method === 'PUT' && rest === '') {
      const parsed = parseLesson(JSON.parse(await readBody(request)))
      if (parsed.id !== id) throw new Error('Идентификатор урока менять нельзя')
      await writeFile(lessonFile(lessonsDir, id), JSON.stringify(parsed, null, 2))
      await writeCatalog(lessonsDir)
      sendJson(response, 200, parsed)
      return
    }
    if (request.method === 'DELETE' && rest === '') {
      await rm(path.join(lessonsDir, id), { recursive: true, force: true })
      await writeCatalog(lessonsDir)
      sendJson(response, 200, { ok: true })
      return
    }
    const audioMatch = rest.match(/^\/steps\/([^/]+)\/audio$/)
    if (request.method === 'POST' && audioMatch) {
      const stepId = decodeURIComponent(audioMatch[1] ?? '')
      sendJson(response, 200, await attachStepAudio(lessonsDir, id, stepId, await readRaw(request)))
      return
    }
    const transcribeMatch = rest.match(/^\/steps\/([^/]+)\/transcribe$/)
    if (request.method === 'POST' && transcribeMatch) {
      const stepId = decodeURIComponent(transcribeMatch[1] ?? '')
      const file = lessonFile(lessonsDir, id)
      const lesson = parseLesson(JSON.parse(await readFile(file, 'utf8')))
      const step = lesson.steps.find((item) => item.id === stepId)
      if (!step?.audio) throw new Error('У шага нет аудио')
      const text = await transcribeWav(path.join(lessonsDir, id, step.audio))
      step.narration = text
      step.hint = text
      await writeFile(file, JSON.stringify(lesson, null, 2))
      sendJson(response, 200, lesson)
      return
    }
  }
  if (request.method === 'POST' && url.pathname === '/api/export') {
    const body = JSON.parse(await readBody(request)) as { ids?: string[] }
    const outFile = path.join(lessonsDir, 'trainer.html')
    const packed = await packHtml({ lessonsDir, outFile, ids: body.ids })
    sendJson(response, 200, { file: 'trainer.html', bytes: packed.bytes, ids: packed.ids })
    return
  }
  if (request.method === 'GET' && url.pathname === '/api/record/events') {
    response.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    })
    if (!session) {
      response.write(`data: ${JSON.stringify({ type: 'idle' })}\n\n`)
    } else {
      response.write(`data: ${JSON.stringify({ type: 'ready', steps: session.steps, paused: session.paused })}\n\n`)
      session.listeners.push(response)
    }
    request.on('close', () => {
      if (!session) return
      session.listeners = session.listeners.filter((item) => item !== response)
    })
    return
  }
  if (request.method === 'POST' && url.pathname === '/api/record/start') {
    if (session) throw new Error('Запись уже идёт')
    const body = JSON.parse(await readBody(request)) as {
      url?: string
      lessonName?: string
      mode?: 'dom' | 'screenshot' | 'auto'
      browser?: string
      executablePath?: string
    }
    if (!body.url) throw new Error('Укажите адрес сайта')
    const settings: StudioSettings = {
      browser: browserChoice(body.browser),
      executablePath: body.executablePath ?? '',
    }
    await writeFile(settingsFile(lessonsDir), JSON.stringify(settings, null, 2))
    const launch = assertRecordingBrowser(settings.browser, settings.executablePath)
    const lessonName = body.lessonName?.trim() || new URL(body.url).hostname
    const lessonId = await openRecording({
      url: body.url,
      lessonName,
      mode: body.mode ?? 'auto',
      launch,
      capturesDir,
      listeners: [],
      event: 'ready',
    })
    sendJson(response, 200, { ok: true, lessonId })
    return
  }
  if (request.method === 'POST' && url.pathname === '/api/record/pause') {
    if (!session) throw new Error('Запись не идёт')
    session.paused = true
    session.controls.setPaused(true)
    broadcast(session, { type: 'paused' })
    sendJson(response, 200, { ok: true })
    return
  }
  if (request.method === 'POST' && url.pathname === '/api/record/resume') {
    if (!session) throw new Error('Запись не идёт')
    session.paused = false
    session.controls.setPaused(false)
    broadcast(session, { type: 'resumed' })
    sendJson(response, 200, { ok: true })
    return
  }
  if (request.method === 'POST' && url.pathname === '/api/record/stop') {
    if (!session) throw new Error('Запись не идёт')
    const current = session
    session = null
    broadcast(current, { type: 'stopping' })
    await current.controls.stop()
    const captureDir = await current.done
    const preview = await serveLessons(lessonsDir)
    const browser = await launchHeadless(current.launch)
    let lesson: Lesson
    try {
      lesson = await buildCapture(captureDir, browser, preview.url, lessonsDir)
      await writeCatalog(lessonsDir)
    } finally {
      await browser.close()
      await preview.close()
    }
    sendJson(response, 200, { id: lesson.id, steps: lesson.steps.map(summarizeStep) })
    return
  }
  if (request.method === 'POST' && url.pathname === '/api/record/reset') {
    if (!session) throw new Error('Запись не идёт')
    const current = session
    session = null
    broadcast(current, { type: 'reset', steps: [] })
    for (const listener of current.listeners) {
      if (!listener.writableEnded) listener.end()
    }
    current.listeners = []
    await current.controls.stop()
    await current.done.catch(() => undefined)
    await rm(path.join(capturesDir, current.lessonId), { recursive: true, force: true })
    const lessonId = await openRecording({
      url: current.url,
      lessonName: current.lessonName,
      mode: current.mode,
      launch: current.launch,
      capturesDir,
      listeners: [],
      event: 'reset',
    })
    sendJson(response, 200, { ok: true, lessonId })
    return
  }
  if (request.method === 'POST' && url.pathname === '/api/record/undo') {
    if (!session) throw new Error('Запись не идёт')
    const result = await session.controls.undoLast()
    if (result.undone) session.steps.pop()
    broadcast(session, {
      type: 'undo',
      steps: session.steps,
      removedScreenId: result.removedScreenId,
      recapturedScreenId: result.recapturedScreenId,
    })
    sendJson(response, 200, { ok: true, undone: result.undone, steps: session.steps })
    return
  }
  sendJson(response, 404, { error: 'Неизвестный запрос' })
}

async function openRecording(input: {
  url: string
  lessonName: string
  mode: 'dom' | 'screenshot' | 'auto'
  launch: BrowserLaunch
  capturesDir: string
  listeners: http.ServerResponse[]
  event: 'ready' | 'reset'
}): Promise<string> {
  const lessonId = lessonIdFromName(input.lessonName)
  let controls: RecordingControls | null = null
  const ready = new Promise<void>((resolve, reject) => {
    const done = recordInteractive({
      url: input.url,
      lessonName: input.lessonName,
      lessonId,
      mode: input.mode,
      capturesDir: input.capturesDir,
      headless: process.env.UITRAINER_HEADLESS === '1',
      channel: input.launch.channel,
      executablePath: input.launch.executablePath,
      onAction: (action) => {
        if (!session) return
        const step: LiveStep = {
          id: `live-${session.steps.length + 1}`,
          label: action.label,
          kind: action.kind,
          value: action.value,
          at: Date.now(),
        }
        session.steps.push(step)
        broadcast(session, { type: 'step', step })
      },
      onSession: (value) => {
        controls = value
        session = {
          controls: value,
          done,
          steps: [],
          listeners: input.listeners,
          paused: false,
          launch: input.launch,
          url: input.url,
          lessonName: input.lessonName,
          mode: input.mode,
          lessonId,
        }
        broadcast(session, { type: input.event, steps: [] })
        resolve()
      },
    })
    done.catch((error: unknown) => {
      if (!controls) reject(error)
    })
  })
  await ready
  return lessonId
}

function summarizeStep(step: LessonStep) {
  return { id: step.id, instruction: step.instruction, action: step.action }
}

function installPlaywrightChromium(): Promise<void> {
  const require = createRequire(typeof __filename === 'string' ? __filename : import.meta.url)
  const cli = path.join(path.dirname(require.resolve('playwright/package.json')), 'cli.js')
  const node = process.env.ELECTRON_RUN_AS_NODE === '1' ? process.execPath : process.execPath
  const env = { ...process.env, ELECTRON_RUN_AS_NODE: process.versions.electron ? '1' : process.env.ELECTRON_RUN_AS_NODE ?? '' }
  return new Promise((resolve, reject) => {
    const child = spawn(node, [cli, 'install', 'chromium'], { env, stdio: 'ignore' })
    child.on('error', reject)
    child.on('exit', (code) => {
      if (code === 0) resolve()
      else reject(new Error('Не удалось скачать Chromium'))
    })
  })
}
