import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { parseLesson } from '../../shared/lesson.ts'
import { transcribeWav } from './transcribe.ts'

export function lessonFile(lessonsDir: string, id: string): string {
  if (!/^[a-z0-9-]{2,48}$/.test(id)) throw new Error('Некорректный идентификатор урока')
  return path.join(lessonsDir, id, 'lesson.json')
}

export async function attachStepAudio(lessonsDir: string, id: string, stepId: string, wav: Buffer) {
  const file = lessonFile(lessonsDir, id)
  const lesson = parseLesson(JSON.parse(await readFile(file, 'utf8')))
  const step = lesson.steps.find((item) => item.id === stepId)
  if (!step) throw new Error('Шаг не найден')
  if (wav.length < 44) throw new Error('Пустой звук')
  const audioDir = path.join(lessonsDir, id, 'audio')
  await mkdir(audioDir, { recursive: true })
  const relative = `audio/${step.id}.wav`
  const target = path.join(lessonsDir, id, relative)
  await writeFile(target, wav)
  step.audio = relative
  const warnings: string[] = []
  try {
    const text = await transcribeWav(target)
    if (text.trim()) {
      step.narration = text.trim()
      if (!step.hint.trim()) step.hint = text.trim()
    }
  } catch (error) {
    warnings.push(error instanceof Error ? error.message : String(error))
  }
  await writeFile(file, JSON.stringify(lesson, null, 2))
  await new Promise<void>((resolve) => setImmediate(resolve))
  return { lesson, warnings }
}
