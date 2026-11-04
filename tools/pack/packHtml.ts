import { mkdir, readdir, readFile, stat, writeFile } from 'node:fs/promises'
import { spawn } from 'node:child_process'
import path from 'node:path'
import type { Lesson } from '../../shared/lesson.ts'
import { fileDataUri, inlineScreenHtml } from './inlineAssets.ts'

export type PackOptions = {
  lessonsDir: string
  outFile: string
  ids?: string[]
  copyTo?: string[]
}

export async function packHtml(options: PackOptions): Promise<{ outFile: string; bytes: number; ids: string[] }> {
  const entries = await readdir(options.lessonsDir, { withFileTypes: true })
  const ids = options.ids ?? entries
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .filter((id) => id !== 'trainer.html')
  const lessons = []
  for (const id of ids) {
    const root = path.join(options.lessonsDir, id)
    const lessonPath = path.join(root, 'lesson.json')
    let lesson: Lesson
    try {
      lesson = JSON.parse(await readFile(lessonPath, 'utf8')) as Lesson
    } catch {
      continue
    }
    const documents: Record<string, string> = {}
    const screenshots: Record<string, string> = {}
    for (const step of lesson.steps) {
      if (!step.audio || step.audio.startsWith('data:')) continue
      const audioFile = path.join(root, step.audio)
      try {
        step.audio = fileDataUri(audioFile)
      } catch {
        /* файл аудио уже удалён */
      }
    }
    for (const screen of lesson.screens) {
      const htmlFile = path.join(root, screen.html)
      documents[screen.id] = inlineScreenHtml(await readFile(htmlFile, 'utf8'), htmlFile)
      screenshots[screen.id] = fileDataUri(path.join(root, screen.screenshot))
    }
    lessons.push({ lesson, documents, screenshots })
  }
  if (lessons.length === 0) throw new Error(`В ${options.lessonsDir} нет уроков с lesson.json`)
  const catalog = lessons.map(({ lesson }) => ({
    id: lesson.id,
    title: lesson.title,
    description: lesson.description,
    sourceSite: lesson.sourceSite,
    screens: lesson.screens.length,
    steps: lesson.steps.length,
    autoStepRatio: lesson.meta.autoStepRatio,
  }))
  const packPath = path.resolve('src/generated/pack.ts')
  await mkdir(path.dirname(packPath), { recursive: true })
  const json = JSON.stringify({ catalog, lessons })
    .replace(/</g, '\\u003c')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029')
  await writeFile(packPath, `export const embeddedPack = ${json}\n`)
  await viteBuild()
  const html = await readFile(path.resolve('dist-single/index.html'))
  await mkdir(path.dirname(options.outFile), { recursive: true })
  await writeFile(options.outFile, html)
  for (const extra of options.copyTo ?? []) {
    await mkdir(path.dirname(extra), { recursive: true })
    await writeFile(extra, html)
  }
  const bytes = (await stat(options.outFile)).size
  return { outFile: options.outFile, bytes, ids: lessons.map((item) => item.lesson.id) }
}

function viteBuild(): Promise<void> {
  return new Promise((resolve, reject) => {
    const child = spawn('npx', ['vite', 'build', '--mode', 'singlefile'], { stdio: 'inherit' })
    child.on('exit', (code) => {
      if (code === 0) resolve()
      else reject(new Error(`vite build завершился с кодом ${code ?? 'нет'}`))
    })
  })
}
