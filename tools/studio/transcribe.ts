import { existsSync } from 'node:fs'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

const MODEL = 'Xenova/whisper-small'
const cacheDir = path.resolve('.ui-capture/models')
const marker = path.join(cacheDir, 'whisper-small.ready')

export function whisperModelName(): string {
  return MODEL
}

export function whisperReady(): boolean {
  return process.env.UI_CAPTURE_FAKE_TRANSCRIPT !== undefined || existsSync(marker)
}

export function whisperHint(): string {
  if (process.env.UI_CAPTURE_FAKE_TRANSCRIPT !== undefined) return 'Включена тестовая расшифровка.'
  if (existsSync(marker)) return 'Модель whisper-small скачана, распознавание офлайн.'
  return 'Модель речи не скачана. Запись и аудио работают без текста. Скачайте модель командой npx ui-capture whisper-download или кнопкой в студии. Нужен интернет только на время загрузки.'
}

export async function downloadWhisper(): Promise<void> {
  await mkdir(cacheDir, { recursive: true })
  const transformers = await loadTransformers()
  transformers.env.cacheDir = cacheDir
  transformers.env.allowRemoteModels = true
  await transformers.pipeline('automatic-speech-recognition', MODEL)
  await writeFile(marker, new Date().toISOString())
}

export async function transcribeWav(wavPath: string): Promise<string> {
  const fake = process.env.UI_CAPTURE_FAKE_TRANSCRIPT
  if (fake !== undefined) return fake
  if (!existsSync(marker)) {
    throw new Error(whisperHint())
  }
  const transformers = await loadTransformers()
  transformers.env.cacheDir = cacheDir
  transformers.env.allowRemoteModels = true
  const asr = await transformers.pipeline('automatic-speech-recognition', MODEL)
  const result = await asr(wavPath, { language: 'russian', task: 'transcribe' })
  const text = typeof result === 'object' && result && 'text' in result ? String(result.text) : String(result)
  return text.trim()
}

async function loadTransformers(): Promise<{
  env: { cacheDir: string; allowRemoteModels: boolean }
  pipeline: (task: string, model: string) => Promise<(audio: string, options: { language: string; task: string }) => Promise<{ text?: string } | string>>
}> {
  try {
    return await import('@xenova/transformers') as never
  } catch {
    throw new Error('Пакет @xenova/transformers не установлен. Выполните npm install в каталоге утилиты.')
  }
}
