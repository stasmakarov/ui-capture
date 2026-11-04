import { createReadStream, existsSync, statSync } from 'node:fs'
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import http from 'node:http'
import path from 'node:path'
import { spawn } from 'node:child_process'
import { createServer as createViteServer } from 'vite'
import { parseLesson, type Lesson, type LessonStep } from '../../shared/lesson.ts'
import { lessonIdFromName, recordInteractive, type RecordingControls } from '../capture/interactive.ts'
import { buildCapture, writeCatalog } from '../builder/build.ts'
import { serveLessons } from '../builder/staticServer.ts'
import { packHtml } from '../pack/packHtml.ts'
import { exportLessonPackage, importLessonPackage } from '../package/lessonPackage.ts'
import { detectBrowsers, gostBrowserHint, resolveBrowserLaunch, settingsFromSaved, type BrowserId, type StudioSettings } from './browsers.ts'
import { downloadWhisper, onWhisperStatus, transcribeWav, whisperStatus } from './transcribe.ts'

type StudioOptions = {
  port: number
  open: boolean
  lessonsDir: string
  capturesDir: string
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

function sendJson(response: http.ServerResponse, status: number, body: unknown) {
  const payload = JSON.stringify(body)
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
  })
  response.end(payload)
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

function lessonFile(lessonsDir: string, id: string): string {
  if (!/^[a-z0-9-]{2,48}$/.test(id)) throw new Error('Некорректный идентификатор урока')
  return path.join(lessonsDir, id, 'lesson.json')
}

async function attachAudio(lessonsDir: string, id: string, clips: Array<{ stepId: string; wavBase64: string }>) {
  const file = lessonFile(lessonsDir, id)
  const lesson = parseLesson(JSON.parse(await readFile(file, 'utf8')))
  const warnings: string[] = []
  const audioDir = path.join(lessonsDir, id, 'audio')
  await mkdir(audioDir, { recursive: true })
  for (const clip of clips) {
    const step = lesson.steps.find((item) => item.id === clip.stepId)
    if (!step || !clip.wavBase64) continue
    const wav = Buffer.from(clip.wavBase64, 'base64')
    if (wav.length < 44) continue
    const relative = `audio/${step.id}.wav`
    const target = path.join(lessonsDir, id, relative)
    await writeFile(target, wav)
    step.audio = relative
    try {
      const text = await transcribeWav(target)
      if (text.trim()) {
        step.narration = text.trim()
        if (!step.hint.trim()) step.hint = text.trim()
      }
    } catch (error) {
      warnings.push(error instanceof Error ? error.message : String(error))
    }
  }
  await writeFile(file, JSON.stringify(lesson, null, 2))
  return { lesson, warnings: [...new Set(warnings)] }
}

export async function startStudio(options: StudioOptions): Promise<{ url: string; close: () => Promise<void> }> {
  const vite = await createViteServer({
    server: {
      middlewareMode: true,
      watch: {
        ignored: ['**/artifacts/**', '**/dist-single/**', '**/captures/**', '**/lessons/**', '**/.ui-capture/**'],
      },
    },
    appType: 'spa',
  })
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
        const types: Record<string, string> = {
          '.html': 'text/html; charset=utf-8',
          '.json': 'application/json; charset=utf-8',
          '.png': 'image/png',
          '.wav': 'audio/wav',
          '.css': 'text/css; charset=utf-8',
          '.svg': 'image/svg+xml',
          '.jpg': 'image/jpeg',
          '.webp': 'image/webp',
          '.woff2': 'font/woff2',
        }
        response.writeHead(200, { 'Content-Type': types[path.extname(file)] ?? 'application/octet-stream' })
        createReadStream(file).pipe(response)
        return
      }
      vite.middlewares(request, response, () => {
        response.writeHead(404)
        response.end('not found')
      })
    } catch (error) {
      sendJson(response, 500, { error: error instanceof Error ? error.message : String(error) })
    }
  })

  await new Promise<void>((resolve) => {
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
      await vite.close()
    },
  }
}

function openBrowser(url: string) {
  const command = process.platform === 'darwin' ? 'open' : 'xdg-open'
  const child = spawn(command, [url], { detached: true, stdio: 'ignore' })
  child.unref()
}

async function handleApi(request: http.IncomingMessage, response: http.ServerResponse, url: URL, options: StudioOptions) {
  const { lessonsDir, capturesDir } = options
  if (request.method === 'GET' && url.pathname === '/api/health') {
    sendJson(response, 200, { ok: true })
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
    sendJson(response, 200, { options: detectBrowsers(), hint: gostBrowserHint })
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
    if (request.method === 'POST' && rest === '/audio') {
      const body = JSON.parse(await readBody(request)) as { clips?: Array<{ stepId: string; wavBase64: string }> }
      sendJson(response, 200, await attachAudio(lessonsDir, id, body.clips ?? []))
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
      if (session) session.listeners = session.listeners.filter((item) => item !== response)
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
    const launch = resolveBrowserLaunch(settings.browser, settings.executablePath)
    const lessonName = body.lessonName?.trim() || new URL(body.url).hostname
    const lessonId = lessonIdFromName(lessonName)
    let controls: RecordingControls | null = null
    const ready = new Promise<void>((resolve, reject) => {
      const done = recordInteractive({
        url: body.url!,
        lessonName,
        lessonId,
        mode: body.mode ?? 'auto',
        capturesDir,
        headless: process.env.UI_CAPTURE_HEADLESS === '1',
        channel: launch.channel,
        executablePath: launch.executablePath,
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
          session = { controls: value, done, steps: [], listeners: [], paused: false }
          broadcast(session, { type: 'ready' })
          resolve()
        },
      })
      done.catch((error: unknown) => {
        if (!controls) reject(error)
      })
    })
    await ready
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
    const { chromium } = await import('playwright')
    const browser = await chromium.launch({ headless: true })
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
  sendJson(response, 404, { error: 'Неизвестный запрос' })
}

function summarizeStep(step: LessonStep) {
  return { id: step.id, instruction: step.instruction, action: step.action }
}
