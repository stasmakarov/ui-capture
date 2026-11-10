import { createServer, type IncomingMessage } from 'node:http'
import { cp, mkdtemp, rm, stat } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { openPackage } from '../package/lessonPackage.ts'
import { exchangeTicket, publicLink, publishLesson, readPlatformLink } from './platform.ts'

type Seen = { url: string; headers: IncomingMessage['headers']; body: Buffer }

describe('студия ↔ платформа', () => {
  const seen: Seen[] = []
  let root = ''
  let server = ''
  let close: () => Promise<void> = async () => undefined

  beforeAll(async () => {
    root = await mkdtemp(path.join(tmpdir(), 'ui-platform-'))
    await cp(path.resolve('lessons', 'spisok-zadach'), path.join(root, 'spisok-zadach'), { recursive: true })
    const http = createServer((request, response) => {
      const chunks: Buffer[] = []
      request.on('data', (chunk: Buffer) => chunks.push(chunk))
      request.on('end', () => {
        const body = Buffer.concat(chunks)
        seen.push({ url: request.url ?? '', headers: request.headers, body })
        response.setHeader('Content-Type', 'application/json')
        if (request.url === '/api/studio/exchange') {
          const ticket = (JSON.parse(body.toString()) as { ticket: string }).ticket
          if (ticket !== 'uictk_good') { response.statusCode = 403; response.end(JSON.stringify({ error: 'Тикет уже использован' })); return }
          response.end(JSON.stringify({ token: 'uicst_secret-token', username: 'trainer', displayName: 'Тренер Иванова', courseId: 'c-1', courseTitle: 'Демо', expiresAt: null }))
          return
        }
        if (request.url === '/api/lessons/import') {
          response.statusCode = 201
          response.end(JSON.stringify({ lessonId: 'l-1', externalId: 'spisok-zadach', title: 'T', status: 'DRAFT', versionId: 'v-1', versionNo: 1, newLesson: true, url: '/lessons/l-1' }))
          return
        }
        response.statusCode = 404
        response.end('{}')
      })
    })
    await new Promise<void>((resolve) => http.listen(0, '127.0.0.1', () => resolve()))
    const address = http.address()
    server = `http://127.0.0.1:${typeof address === 'object' && address ? address.port : 0}`
    close = () => new Promise((resolve) => http.close(() => resolve()))
  })

  afterAll(async () => {
    await close()
    await rm(root, { recursive: true, force: true })
  })

  it('обменивает тикет на токен и хранит его только локально (0600)', async () => {
    await expect(exchangeTicket(root, server, 'uictk_bad')).rejects.toThrow('Тикет уже использован')
    const link = await exchangeTicket(root, `${server}/`, 'uictk_good', 'c-1')
    expect(link.server).toBe(server)
    expect(link.courseId).toBe('c-1')
    const stored = await readPlatformLink(root)
    expect(stored?.token).toBe('uicst_secret-token')
    expect(stored?.displayName).toBe('Тренер Иванова')
    if (process.platform !== 'win32') {
      expect((await stat(path.join(root, '.studio-platform.json'))).mode & 0o777).toBe(0o600)
    }
    const published = publicLink(stored)
    expect(JSON.stringify(published)).not.toContain('secret-token')
    expect(JSON.stringify(published)).not.toContain('tokenPrefix')
    expect(published).toMatchObject({ connected: true, displayName: 'Тренер Иванова', username: 'trainer' })
  })

  it('«Опубликовать на сервер» отправляет проверяемый .lesson.zip с Bearer-токеном и курсом', async () => {
    const result = await publishLesson(root, 'spisok-zadach', { withTrainer: false })
    expect(result.status).toBe('DRAFT')
    expect(result.adminUrl).toBe(`${server}/lessons/l-1`)
    const request = seen.find((item) => item.url === '/api/lessons/import')!
    expect(request.headers.authorization).toBe('Bearer uicst_secret-token')
    expect(request.headers['x-course-id']).toBe('c-1')
    expect(request.headers['content-type']).toBe('application/zip')
    const opened = openPackage(new Uint8Array(request.body))
    expect(opened.lesson.id).toBe('spisok-zadach')
    expect(opened.manifest.files.length).toBeGreaterThan(1)
  })
})
