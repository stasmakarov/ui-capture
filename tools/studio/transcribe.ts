import { existsSync } from 'node:fs'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { summarizeDownload, type FileProgress } from './downloadProgress.ts'

const MODEL = 'Xenova/whisper-small'

export type WhisperPhase = 'idle' | 'downloading' | 'ready' | 'error'

export type WhisperStatus = {
  phase: WhisperPhase
  ready: boolean
  model: string
  hint: string
  percent: number
  loadedBytes: number
  totalBytes: number
  file: string
  error: string
}

type HubProgress = {
  status?: string
  file?: string
  loaded?: number
  total?: number
}

type Live = {
  phase: 'idle' | 'downloading' | 'error'
  percent: number
  loadedBytes: number
  totalBytes: number
  file: string
  error: string
}

const listeners = new Set<(status: WhisperStatus) => void>()
let live: Live = { phase: 'idle', percent: 0, loadedBytes: 0, totalBytes: 0, file: '', error: '' }
let running: Promise<void> | null = null

function modelCache(): string {
  return path.resolve(process.env.UI_CAPTURE_MODEL_DIR ?? '.ui-capture/models')
}

function markerFile(): string {
  return path.join(modelCache(), 'whisper-small.ready')
}

export function whisperModelName(): string {
  return MODEL
}

export function whisperReady(): boolean {
  return existsSync(markerFile())
}

export function whisperHint(): string {
  if (whisperReady()) return 'Модель установлена'
  return 'Модель речи не скачана. Запись и аудио работают без текста. Скачайте модель кнопкой «Скачать модель речи» или командой npx ui-capture whisper-download. Интернет нужен только на время загрузки.'
}

export function whisperStatus(): WhisperStatus {
  const ready = whisperReady()
  if (live.phase === 'downloading') {
    return {
      phase: 'downloading',
      ready: false,
      model: MODEL,
      hint: 'Скачиваю модель речи…',
      percent: live.percent,
      loadedBytes: live.loadedBytes,
      totalBytes: live.totalBytes,
      file: live.file,
      error: '',
    }
  }
  if (live.phase === 'error' && !ready) {
    return {
      phase: 'error',
      ready: false,
      model: MODEL,
      hint: whisperHint(),
      percent: live.percent,
      loadedBytes: live.loadedBytes,
      totalBytes: live.totalBytes,
      file: live.file,
      error: live.error,
    }
  }
  if (ready) {
    return {
      phase: 'ready',
      ready: true,
      model: MODEL,
      hint: 'Модель установлена',
      percent: 100,
      loadedBytes: live.loadedBytes,
      totalBytes: live.totalBytes,
      file: live.file,
      error: '',
    }
  }
  return {
    phase: 'idle',
    ready: false,
    model: MODEL,
    hint: whisperHint(),
    percent: 0,
    loadedBytes: 0,
    totalBytes: 0,
    file: '',
    error: '',
  }
}

export function onWhisperStatus(listener: (status: WhisperStatus) => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function emit() {
  const status = whisperStatus()
  for (const listener of listeners) listener(status)
}

function publish(next: Partial<Live>) {
  live = { ...live, ...next }
  emit()
}

export function downloadWhisper(): Promise<void> {
  if (running) return running
  running = runDownload().finally(() => {
    running = null
  })
  return running
}

async function runDownload(): Promise<void> {
  if (whisperReady()) {
    live = { phase: 'idle', percent: 100, loadedBytes: live.loadedBytes, totalBytes: live.totalBytes, file: live.file, error: '' }
    emit()
    return
  }
  publish({ phase: 'downloading', percent: 0, loadedBytes: 0, totalBytes: 0, file: '', error: '' })
  try {
    await mkdir(modelCache(), { recursive: true })
    if (process.env.UI_CAPTURE_WHISPER_SIMULATE === '1') {
      await simulateDownload()
    } else {
      await fetchModel()
    }
    live = { phase: 'idle', percent: 100, loadedBytes: live.loadedBytes, totalBytes: live.totalBytes, file: live.file, error: '' }
    emit()
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error)
    publish({
      phase: 'error',
      error: `Не удалось скачать модель речи. ${detail} Проверьте интернет и нажмите «Скачать модель речи» ещё раз. Запись урока без модели возможна.`,
    })
    throw error
  }
}

async function simulateDownload(): Promise<void> {
  const totalBytes = 20_000_000
  publish({ phase: 'downloading', percent: 10, loadedBytes: 2_000_000, totalBytes, file: 'whisper-small' })
  await wait(400)
  publish({ phase: 'downloading', percent: 55, loadedBytes: 11_000_000, totalBytes, file: 'whisper-small' })
  await wait(400)
  await writeFile(markerFile(), 'simulate')
}

async function fetchModel(): Promise<void> {
  const transformers = await loadTransformers()
  transformers.env.cacheDir = modelCache()
  transformers.env.allowRemoteModels = true
  const files = new Map<string, FileProgress>()
  let currentFile = 'whisper-small'
  await transformers.pipeline('automatic-speech-recognition', MODEL, {
    progress_callback: (event) => {
      if (event.file) currentFile = event.file
      if (event.status === 'progress' || typeof event.loaded === 'number') {
        const file = event.file || currentFile
        const previous = files.get(file)
        files.set(file, {
          file,
          loaded: event.loaded ?? previous?.loaded ?? 0,
          total: event.total ?? previous?.total ?? event.loaded ?? previous?.loaded ?? 0,
        })
        const summary = summarizeDownload([...files.values()])
        publish({
          phase: 'downloading',
          percent: summary.percent,
          loadedBytes: summary.loadedBytes,
          totalBytes: summary.totalBytes,
          file,
        })
      }
    },
  })
  await writeFile(markerFile(), new Date().toISOString())
}

export async function transcribeWav(wavPath: string): Promise<string> {
  const fake = process.env.UI_CAPTURE_FAKE_TRANSCRIPT
  if (fake !== undefined) return fake
  if (!existsSync(markerFile())) throw new Error(whisperHint())
  const marker = await readFile(markerFile(), 'utf8')
  if (marker.trim() === 'simulate') return 'комментарий к шагу'
  const transformers = await loadTransformers()
  transformers.env.cacheDir = modelCache()
  transformers.env.allowRemoteModels = true
  const asr = await transformers.pipeline('automatic-speech-recognition', MODEL)
  const result = await asr(wavPath, { language: 'russian', task: 'transcribe' })
  const text = typeof result === 'object' && result && 'text' in result ? String(result.text) : String(result)
  return text.trim()
}

function wait(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function loadTransformers(): Promise<{
  env: { cacheDir: string; allowRemoteModels: boolean }
  pipeline: (
    task: string,
    model: string,
    options?: { progress_callback?: (event: HubProgress) => void },
  ) => Promise<(audio: string, options: { language: string; task: string }) => Promise<{ text?: string } | string>>
}> {
  try {
    return await import('@xenova/transformers') as never
  } catch {
    throw new Error('Пакет @xenova/transformers не установлен. Выполните npm install в каталоге утилиты.')
  }
}
