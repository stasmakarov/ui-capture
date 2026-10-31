import { mkdir, readFile, stat, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { spawn } from 'node:child_process'
import type { Lesson } from '../../shared/lesson.ts'
import { fileDataUri, inlineScreenHtml } from './inlineAssets.ts'

const LIMIT = 25 * 1024 * 1024
const OUT = path.resolve('artifacts/ui-trainer-wiki.html')
const STORE = '/cursor/stores/self/artifacts/ui-trainer-wiki.html'
const PACK = path.resolve('src/generated/pack.ts')

type CatalogItem = {
  id: string
  title: string
  description: string
  sourceSite: string
  screens: number
  steps: number
  autoStepRatio: number
}

async function packLessons(ids: string[]) {
  const lessons = []
  for (const id of ids) {
    const root = path.resolve('lessons', id)
    const lesson = JSON.parse(await readFile(path.join(root, 'lesson.json'), 'utf8')) as Lesson
    const documents: Record<string, string> = {}
    const screenshots: Record<string, string> = {}
    for (const screen of lesson.screens) {
      const htmlFile = path.join(root, screen.html)
      const html = await readFile(htmlFile, 'utf8')
      documents[screen.id] = inlineScreenHtml(html, htmlFile)
      screenshots[screen.id] = fileDataUri(path.join(root, screen.screenshot))
    }
    lessons.push({ lesson, documents, screenshots })
    console.log(`  ${id}: ${lesson.screens.length} экранов, HTML ${mb(sizeOf(documents))}`)
  }
  const catalog: CatalogItem[] = lessons.map(({ lesson }) => ({
    id: lesson.id,
    title: lesson.title,
    description: lesson.description,
    sourceSite: lesson.sourceSite,
    screens: lesson.screens.length,
    steps: lesson.steps.length,
    autoStepRatio: lesson.meta.autoStepRatio,
  }))
  await mkdir(path.dirname(PACK), { recursive: true })
  const json = JSON.stringify({ catalog, lessons })
    .replace(/</g, '\\u003c')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029')
  await writeFile(PACK, `export const embeddedPack = ${json}\n`)
  const packed = await stat(PACK)
  console.log(`Пакет данных: ${mb(packed.size)}`)
  return packed.size
}

function sizeOf(documents: Record<string, string>): number {
  return Object.values(documents).reduce((sum, html) => sum + html.length, 0)
}

function mb(bytes: number): string {
  return `${(bytes / 1024 / 1024).toFixed(1)} МБ`
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

const requested = ['najti-statyu', 'spisok-zadach']
console.log('Сборка самодостаточного HTML')
let bytes = await packLessons(requested)
let ids = requested
if (bytes > LIMIT * 0.9) {
  console.log('Пакет близко к пределу, оставляю только урок Википедии')
  ids = ['najti-statyu']
  bytes = await packLessons(ids)
}
await viteBuild()
const built = path.resolve('dist-single/index.html')
const html = await readFile(built)
await mkdir(path.dirname(OUT), { recursive: true })
await writeFile(OUT, html)
await mkdir(path.dirname(STORE), { recursive: true })
await writeFile(STORE, html)
const finalSize = (await stat(OUT)).size
console.log(`Файл: ${OUT}`)
console.log(`Размер: ${mb(finalSize)}`)
console.log(`Уроки: ${ids.join(', ')}`)
if (finalSize > LIMIT) {
  throw new Error(`Файл больше 25 МБ: ${mb(finalSize)}`)
}
