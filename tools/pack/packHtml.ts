import { mkdir, readdir, readFile, stat, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Lesson } from '../../shared/lesson.ts'
import { fileDataUri, inlineScreenHtml } from './inlineAssets.ts'

export type PackOptions = {
  lessonsDir: string
  outFile: string
  ids?: string[]
  copyTo?: string[]
  templatePath?: string
}

const PACK_SCRIPT = /(<script type="application\/json" id="uitrainer-pack">)([\s\S]*?)(<\/script>)/

export function resolveTrainerTemplate(templatePath?: string): string {
  const envPath = process.env.UITRAINER_TEMPLATE
  const resources = (process as NodeJS.Process & { resourcesPath?: string }).resourcesPath
  const here = path.dirname(fileURLToPath(import.meta.url))
  const candidates = [
    templatePath,
    envPath,
    resources ? path.join(resources, 'trainer-template.html') : undefined,
    path.join(here, '..', '..', 'dist-template', 'trainer-template.html'),
    path.resolve('dist-template/trainer-template.html'),
  ].filter((item): item is string => Boolean(item))
  const found = candidates.find((item) => existsSync(item))
  if (!found) {
    throw new Error('Нет шаблона плеера (dist-template/trainer-template.html). Пересоберите студию / npm run build:template')
  }
  return found
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
  const json = JSON.stringify({ catalog, lessons })
    .replace(/</g, '\\u003c')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029')
  const template = await readFile(resolveTrainerTemplate(options.templatePath), 'utf8')
  if (!PACK_SCRIPT.test(template)) {
    throw new Error('Шаблон плеера без script#uitrainer-pack. Пересоберите студию / npm run build:template')
  }
  const html = template.replace(PACK_SCRIPT, (_all, open: string, _body: string, close: string) => `${open}${json}${close}`)
  await mkdir(path.dirname(options.outFile), { recursive: true })
  await writeFile(options.outFile, html)
  for (const extra of options.copyTo ?? []) {
    await mkdir(path.dirname(extra), { recursive: true })
    await writeFile(extra, html)
  }
  const bytes = (await stat(options.outFile)).size
  return { outFile: options.outFile, bytes, ids: lessons.map((item) => item.lesson.id) }
}
