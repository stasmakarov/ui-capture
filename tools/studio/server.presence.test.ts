import { readFileSync } from 'node:fs'
import { createServer } from 'node:http'
import path from 'node:path'
import { afterAll, describe, expect, it } from 'vitest'
import { handlePresence, sendJson } from './presence.ts'

describe('/api/presence', () => {
  const server = createServer((request, response) => {
    const url = new URL(request.url ?? '/', 'http://127.0.0.1')
    if (url.pathname === '/api/presence') {
      if (handlePresence(request, response, '0.6.2')) return
    }
    if (request.method === 'GET' && url.pathname === '/api/studio-info') {
      sendJson(response, 200, { studioVersion: '0.6.2', dataDir: '/tmp/studio' })
      return
    }
    response.writeHead(404)
    response.end()
  })
  const listening = new Promise<string>((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const address = server.address()
      const port = typeof address === 'object' && address ? address.port : 0
      resolve(`http://127.0.0.1:${port}/`)
    })
  })
  afterAll(() => new Promise<void>((resolve) => server.close(() => resolve())))

  it('отдаёт только app/версию с CORS, studio-info без CORS', async () => {
    const base = await listening
    const origin = 'http://66.45.241.140:8090'
    const presence = await fetch(`${base}api/presence`, { headers: { Origin: origin } })
    expect(presence.status).toBe(200)
    expect(presence.headers.get('access-control-allow-origin')).toBe(origin)
    expect(presence.headers.get('vary')).toMatch(/Origin/i)
    await expect(presence.json()).resolves.toEqual({
      app: 'uitrainer-studio',
      studioVersion: '0.6.2',
      studioApiVersion: 1,
    })

    const preflight = await fetch(`${base}api/presence`, {
      method: 'OPTIONS',
      headers: {
        Origin: origin,
        'Access-Control-Request-Method': 'GET',
        'Access-Control-Request-Private-Network': 'true',
      },
    })
    expect(preflight.status).toBe(204)
    expect(preflight.headers.get('access-control-allow-origin')).toBe(origin)
    expect(preflight.headers.get('access-control-allow-methods')).toMatch(/GET/)
    expect(preflight.headers.get('access-control-allow-private-network')).toBe('true')
    expect(preflight.headers.get('vary')).toMatch(/Origin/i)

    const info = await fetch(`${base}api/studio-info`, { headers: { Origin: origin } })
    expect(info.status).toBe(200)
    expect(info.headers.get('access-control-allow-origin')).toBeNull()
    const body = await info.json() as { studioVersion: string; dataDir?: string }
    expect(body.studioVersion).toBe('0.6.2')
    expect(body.dataDir).toBeTruthy()

    const wired = readFileSync(path.resolve('tools/studio/server.ts'), 'utf8')
    expect(wired).toContain("url.pathname === '/api/presence'")
    expect(wired).toContain('handlePresence(request, response, studioVersionOf(options))')
    expect(wired).toContain("url.pathname === '/api/studio-info'")
  })
})
