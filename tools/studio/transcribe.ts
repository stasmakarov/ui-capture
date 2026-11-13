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
  modelBytes: number
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
let modelBytes = 0
let modelBytesTried = false
let asrPipeline: Promise<AsrFn> | null = null

type AsrFn = (audio: string, options: { language: string; task: string }) => Promise<{ text?: string } | string>

const MODEL_DIR = '.uitrainer/models'

export function whisperModelDir(): string {
  const fromEnv = process.env.UITRAINER_MODEL_DIR
  if (fromEnv !== undefined) return path.resolve(fromEnv)
  return path.resolve(MODEL_DIR)
}

function modelCache(): string {
  return whisperModelDir()
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
  if (whisperReady()) return 'Распознавание речи установлено'
  return 'Распознавание речи не установлено. Голос запишется, но текст шагов придётся вводить вручную.'
}

export function whisperModelBytes(): number {
  return modelBytes
}

function countsAsModelFile(name: string): boolean {
  const base = name.split('/').pop() ?? name
  if (base.endsWith('.json')) return /^(config|tokenizer|preprocessor|generation|added_tokens|special_tokens_map|vocab)/.test(base) || base.includes('tokenizer')
  return /quantized.*\.onnx$/.test(base) || /model_quantized\.onnx$/.test(base)
}

export async function refreshWhisperModelBytes(): Promise<number> {
  if (modelBytesTried) return modelBytes
  modelBytesTried = true
  try {
    const response = await fetch('https://huggingface.co/api/models/Xenova/whisper-small?blobs=true', {
      signal: AbortSignal.timeout(8000),
    })
    if (!response.ok) return modelBytes
    const payload = await response.json() as { siblings?: Array<{ rfilename?: string; size?: number }> }
    let total = 0
    for (const file of payload.siblings ?? []) {
      if (file.rfilename && countsAsModelFile(file.rfilename) && typeof file.size === 'number') total += file.size
    }
    if (total > 0) {
      modelBytes = total
      emit()
    }
  } catch {
    /* офлайн — кнопка без размера */
  }
  return modelBytes
}

export function whisperStatus(): WhisperStatus {
  const ready = whisperReady()
  if (live.phase === 'downloading') {
    return {
      phase: 'downloading',
      ready: false,
      model: MODEL,
      hint: 'Устанавливаю распознавание речи…',
      percent: live.percent,
      loadedBytes: live.loadedBytes,
      totalBytes: live.totalBytes,
      file: live.file,
      error: '',
      modelBytes,
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
      modelBytes,
    }
  }
  if (ready) {
    return {
      phase: 'ready',
      ready: true,
      model: MODEL,
      hint: 'Распознавание речи установлено',
      percent: 100,
      loadedBytes: live.loadedBytes,
      totalBytes: live.totalBytes,
      file: live.file,
      error: '',
      modelBytes,
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
    modelBytes,
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
    if (process.env.UITRAINER_WHISPER_SIMULATE === '1') {
      await simulateDownload()
    } else {
      await fetchModel()
    }
    live = { phase: 'idle', percent: 100, loadedBytes: live.loadedBytes, totalBytes: live.totalBytes, file: live.file, error: '' }
    resetAsrPipeline()
    emit()
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error)
    publish({
      phase: 'error',
      error: `Не удалось установить распознавание речи. ${detail} Проверьте интернет и нажмите «Установить распознавание речи» ещё раз. Запись урока без него возможна.`,
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

export function resetAsrPipeline(): void {
  asrPipeline = null
}

async function getAsrPipeline(): Promise<AsrFn> {
  if (!asrPipeline) {
    asrPipeline = (async () => {
      const transformers = await loadTransformers()
      transformers.env.cacheDir = modelCache()
      transformers.env.allowRemoteModels = true
      return transformers.pipeline('automatic-speech-recognition', MODEL)
    })()
  }
  try {
    return await asrPipeline
  } catch (error) {
    asrPipeline = null
    throw error
  }
}

export async function transcribeWav(wavPath: string): Promise<string> {
  const fake = process.env.UITRAINER_FAKE_TRANSCRIPT
  if (fake !== undefined) return fake
  if (!existsSync(markerFile())) throw new Error(whisperHint())
  const marker = await readFile(markerFile(), 'utf8')
  if (marker.trim() === 'simulate') return 'комментарий к шагу'
  const asr = await getAsrPipeline()
  try {
    const result = await asr(wavPath, { language: 'russian', task: 'transcribe' })
    const text = typeof result === 'object' && result && 'text' in result ? String(result.text) : String(result)
    return text.trim()
  } catch (error) {
    asrPipeline = null
    throw error
  }
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
