import { chmod, mkdir, mkdtemp, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { buildPackageBytes, collectLessonFiles } from '../package/lessonPackage.ts'
import { packHtml } from '../pack/packHtml.ts'

/** Связь студии с платформой (M2): токен студии, сервер и курс, выданные по тикету из админки. */
export type PlatformLink = {
  server: string
  token: string
  username: string
  courseId: string | null
  courseTitle: string | null
  expiresAt: string | null
  linkedAt: string
}

export type PublicPlatformLink = Omit<PlatformLink, 'token'> & { connected: true; tokenPrefix: string }

export type PublishResult = {
  lessonId: string
  externalId: string
  title: string
  status: string
  versionId: string
  versionNo: number
  newLesson: boolean
  url: string
  adminUrl: string
}

export function platformFile(lessonsDir: string): string {
  return path.join(lessonsDir, '.studio-platform.json')
}

export function normalizeServer(value: string): string {
  let parsed: URL
  try {
    parsed = new URL(value)
  } catch {
    throw new Error('server должен быть адресом платформы, например http://localhost:8090')
  }
  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') throw new Error('server должен быть http или https')
  return `${parsed.origin}${parsed.pathname.replace(/\/+$/, '')}`
}

export async function readPlatformLink(lessonsDir: string): Promise<PlatformLink | null> {
  try {
    const parsed = JSON.parse(await readFile(platformFile(lessonsDir), 'utf8')) as PlatformLink
    if (!parsed.server || !parsed.token) return null
    return parsed
  } catch {
    return null
  }
}

export function publicLink(link: PlatformLink | null): PublicPlatformLink | { connected: false } {
  if (!link) return { connected: false }
  const { token, ...rest } = link
  return { ...rest, connected: true, tokenPrefix: `${token.slice(0, 12)}…` }
}

export async function savePlatformLink(lessonsDir: string, link: PlatformLink): Promise<void> {
  await mkdir(lessonsDir, { recursive: true })
  const file = platformFile(lessonsDir)
  await writeFile(file, JSON.stringify(link, null, 2), { mode: 0o600 })
  await chmod(file, 0o600).catch(() => undefined)
}

export async function forgetPlatformLink(lessonsDir: string): Promise<void> {
  await rm(platformFile(lessonsDir), { force: true })
}

async function errorText(response: Response): Promise<string> {
  const text = await response.text()
  try {
    const parsed = JSON.parse(text) as { error?: string }
    if (parsed.error) return parsed.error
  } catch {
    /* не JSON */
  }
  return text.slice(0, 300) || `HTTP ${response.status}`
}

/** Обменять одноразовый тикет из ссылки uicapture://new на токен студии и запомнить его. */
export async function exchangeTicket(lessonsDir: string, serverValue: string, ticket: string, course?: string | null): Promise<PlatformLink> {
  const server = normalizeServer(serverValue)
  if (!ticket.trim()) throw new Error('Нет тикета')
  const response = await fetch(`${server}/api/studio/exchange`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ticket: ticket.trim() }),
  })
  if (!response.ok) throw new Error(`Платформа не приняла тикет: ${await errorText(response)}`)
  const payload = await response.json() as { token: string; username: string; courseId: string | null; courseTitle: string | null; expiresAt: string | null }
  const link: PlatformLink = {
    server,
    token: payload.token,
    username: payload.username,
    courseId: payload.courseId ?? (course?.trim() || null),
    courseTitle: payload.courseTitle ?? null,
    expiresAt: payload.expiresAt ?? null,
    linkedAt: new Date().toISOString(),
  }
  await savePlatformLink(lessonsDir, link)
  return link
}

/** Привязать студию токеном, выпущенным вручную на экране «Мои токены студии». */
export async function loginWithToken(lessonsDir: string, serverValue: string, token: string, course?: string | null): Promise<PlatformLink> {
  const server = normalizeServer(serverValue)
  const response = await fetch(`${server}/api/studio/whoami`, { headers: { Authorization: `Bearer ${token.trim()}` } })
  if (!response.ok) throw new Error(`Токен не подошёл: ${await errorText(response)}`)
  const payload = await response.json() as { username: string }
  const link: PlatformLink = {
    server,
    token: token.trim(),
    username: payload.username,
    courseId: course?.trim() || null,
    courseTitle: null,
    expiresAt: null,
    linkedAt: new Date().toISOString(),
  }
  await savePlatformLink(lessonsDir, link)
  return link
}

/** Собрать пакет урока для платформы: файлы урока + trainer.html этого урока (для предпросмотра в админке). */
export async function buildPublishPackage(lessonsDir: string, id: string, options: { withTrainer?: boolean } = {}): Promise<Uint8Array> {
  const files = await collectLessonFiles(path.join(lessonsDir, id))
  if (options.withTrainer !== false) {
    const dir = await mkdtemp(path.join(tmpdir(), 'ui-capture-publish-'))
    try {
      const outFile = path.join(dir, 'trainer.html')
      await packHtml({ lessonsDir, outFile, ids: [id] })
      files.set('trainer.html', new Uint8Array(await readFile(outFile)))
    } catch {
      /* без trainer.html предпросмотр в админке недоступен, но урок принимается */
    } finally {
      await rm(dir, { recursive: true, force: true })
    }
  }
  return buildPackageBytes(files, new Date().toISOString())
}

/** «Опубликовать на сервер»: .lesson.zip → POST /api/lessons/import, на платформе появляется черновик. */
export async function publishLesson(lessonsDir: string, id: string, options: { withTrainer?: boolean; studioVersion?: string } = {}): Promise<PublishResult> {
  const link = await readPlatformLink(lessonsDir)
  if (!link) throw new Error('Студия не связана с платформой. Нажмите «Записать в студии» в админке или выполните npx ui-capture platform-login')
  const bytes = await buildPublishPackage(lessonsDir, id, options)
  const headers: Record<string, string> = {
    'Content-Type': 'application/zip',
    Authorization: `Bearer ${link.token}`,
    'X-Studio-Version': options.studioVersion ?? '0.6.0',
  }
  if (link.courseId) headers['X-Course-Id'] = link.courseId
  const response = await fetch(`${link.server}/api/lessons/import`, { method: 'POST', headers, body: Buffer.from(bytes) })
  if (!response.ok) throw new Error(`Платформа отклонила пакет (${response.status}): ${await errorText(response)}`)
  const payload = await response.json() as Omit<PublishResult, 'adminUrl'>
  return { ...payload, adminUrl: `${link.server}${payload.url}` }
}
