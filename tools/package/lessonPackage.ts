import { createHash } from 'node:crypto'
import { mkdir, readdir, readFile, stat, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { migrateLesson, type Lesson } from '../../lesson-schema/src/lesson.ts'
import { unzipStore, zipStore, type ZipEntry } from './zipStore.ts'

/** Идентификатор архива .lesson.zip. Строка не меняется: уже собранные пакеты остаются читаемыми. */
export const PACKAGE_FORMAT = 'ui-capture-lesson'
export const PACKAGE_FORMAT_VERSION = 1

const PACKED_DIRS = ['screens', 'original', 'assets', 'audio']

export type ManifestFile = { path: string; sha256: string; bytes: number }

export type LessonManifest = {
  format: typeof PACKAGE_FORMAT
  formatVersion: 1
  schemaVersion: number
  id: string
  title: string
  createdAt: string
  files: ManifestFile[]
}

function sha256(bytes: Uint8Array): string {
  return createHash('sha256').update(bytes).digest('hex')
}

export function assertPackagePath(relative: string) {
  const parts = relative.split('/')
  if (!relative || relative.startsWith('/') || parts.some((part) => part === '..' || part === '')) {
    throw new Error(`Недопустимый путь в пакете: ${relative}`)
  }
}

export function referencedPaths(lesson: Lesson): string[] {
  const paths = new Set<string>()
  for (const screen of lesson.screens) {
    paths.add(screen.html)
    paths.add(screen.screenshot)
  }
  for (const step of lesson.steps) {
    if (step.audio && !step.audio.startsWith('data:')) paths.add(step.audio)
  }
  return [...paths]
}

async function walk(dir: string, prefix: string, out: string[]) {
  const entries = await readdir(dir, { withFileTypes: true })
  for (const entry of entries) {
    const relative = `${prefix}/${entry.name}`
    if (entry.isDirectory()) await walk(path.join(dir, entry.name), relative, out)
    else if (entry.isFile()) out.push(relative)
  }
}

export async function collectLessonFiles(lessonDir: string): Promise<Map<string, Uint8Array>> {
  const files = new Map<string, Uint8Array>()
  files.set('lesson.json', new Uint8Array(await readFile(path.join(lessonDir, 'lesson.json'))))
  for (const name of PACKED_DIRS) {
    const dir = path.join(lessonDir, name)
    try {
      if (!(await stat(dir)).isDirectory()) continue
    } catch {
      continue
    }
    const list: string[] = []
    await walk(dir, name, list)
    for (const relative of list) files.set(relative, new Uint8Array(await readFile(path.join(lessonDir, relative))))
  }
  return files
}

export function buildPackageBytes(files: Map<string, Uint8Array>, createdAt = '2026-11-04T00:00:00.000Z'): Uint8Array {
  const lessonBytes = files.get('lesson.json')
  if (!lessonBytes) throw new Error('В пакете нет lesson.json')
  const lesson = migrateLesson(JSON.parse(new TextDecoder().decode(lessonBytes)))
  for (const relative of referencedPaths(lesson)) {
    assertPackagePath(relative)
    if (!files.has(relative)) throw new Error(`В уроке указан файл, которого нет рядом с lesson.json: ${relative}`)
  }
  const listed = [...files.keys()].filter((name) => name !== 'manifest.json').sort()
  const manifest: LessonManifest = {
    format: PACKAGE_FORMAT,
    formatVersion: PACKAGE_FORMAT_VERSION,
    schemaVersion: lesson.schemaVersion,
    id: lesson.id,
    title: lesson.title,
    createdAt,
    files: listed.map((name) => {
      const bytes = files.get(name)!
      return { path: name, sha256: sha256(bytes), bytes: bytes.length }
    }),
  }
  const entries: ZipEntry[] = [
    { path: 'manifest.json', bytes: new TextEncoder().encode(`${JSON.stringify(manifest, null, 2)}\n`) },
    ...listed.map((name) => ({ path: name, bytes: files.get(name)! })),
  ]
  return zipStore(entries)
}

export function openPackage(archive: Uint8Array): { manifest: LessonManifest; lesson: Lesson; files: Map<string, Uint8Array> } {
  const files = unzipStore(archive)
  const manifestBytes = files.get('manifest.json')
  if (!manifestBytes) throw new Error('В пакете нет manifest.json')
  const manifest = JSON.parse(new TextDecoder().decode(manifestBytes)) as LessonManifest
  if (manifest.format !== PACKAGE_FORMAT || manifest.formatVersion !== PACKAGE_FORMAT_VERSION) {
    throw new Error('Неизвестный формат пакета урока')
  }
  const listed = new Set(manifest.files.map((item) => item.path))
  for (const name of files.keys()) {
    if (name === 'manifest.json') continue
    if (!listed.has(name)) throw new Error(`Лишний файл в пакете: ${name}`)
  }
  for (const item of manifest.files) {
    assertPackagePath(item.path)
    const bytes = files.get(item.path)
    if (!bytes) throw new Error(`В манифесте указан ${item.path}, но в архиве его нет`)
    if (bytes.length !== item.bytes || sha256(bytes) !== item.sha256) {
      throw new Error(`Хэш ${item.path} не совпадает с манифестом`)
    }
  }
  const lessonBytes = files.get('lesson.json')
  if (!lessonBytes) throw new Error('В пакете нет lesson.json')
  const lesson = migrateLesson(JSON.parse(new TextDecoder().decode(lessonBytes)))
  for (const relative of referencedPaths(lesson)) {
    if (!files.has(relative)) throw new Error(`Урок ссылается на отсутствующий файл ${relative}`)
  }
  return { manifest, lesson, files }
}

export async function exportLessonPackage(lessonDir: string): Promise<Uint8Array> {
  return buildPackageBytes(await collectLessonFiles(lessonDir))
}

export async function importLessonPackage(archive: Uint8Array, lessonsDir: string): Promise<Lesson> {
  const opened = openPackage(archive)
  const target = path.resolve(lessonsDir, opened.lesson.id)
  const root = path.resolve(lessonsDir)
  if (!target.startsWith(root + path.sep)) throw new Error('Некорректный идентификатор урока')
  await mkdir(target, { recursive: true })
  for (const [name, bytes] of opened.files) {
    if (name === 'manifest.json') continue
    assertPackagePath(name)
    const file = path.resolve(target, name)
    if (!file.startsWith(target + path.sep) && file !== path.join(target, 'lesson.json')) {
      throw new Error(`Путь выходит за каталог урока: ${name}`)
    }
    await mkdir(path.dirname(file), { recursive: true })
    await writeFile(file, bytes)
  }
  return opened.lesson
}
