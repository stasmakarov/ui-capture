import type http from 'node:http'
import { STUDIO_API_VERSION } from './apiVersion.ts'

export function studioVersionOf(options: { studioVersion?: string }): string {
  return options.studioVersion ?? process.env.UITRAINER_STUDIO_VERSION ?? '0.6.0'
}

export function sendJson(response: http.ServerResponse, status: number, body: unknown) {
  const payload = JSON.stringify(body)
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
  })
  response.end(payload)
}

function presenceCorsHeaders(request: http.IncomingMessage): http.OutgoingHttpHeaders {
  const origin = request.headers.origin
  const headers: http.OutgoingHttpHeaders = { Vary: 'Origin' }
  if (typeof origin === 'string' && origin.length > 0) {
    headers['Access-Control-Allow-Origin'] = origin
  }
  return headers
}

/** CORS только здесь. Возвращает false, если метод не GET/OPTIONS. */
export function handlePresence(
  request: http.IncomingMessage,
  response: http.ServerResponse,
  studioVersion: string,
): boolean {
  if (request.method === 'OPTIONS') {
    response.writeHead(204, {
      ...presenceCorsHeaders(request),
      'Access-Control-Allow-Methods': 'GET',
      'Access-Control-Allow-Private-Network': 'true',
    })
    response.end()
    return true
  }
  if (request.method !== 'GET') return false
  const payload = JSON.stringify({
    app: 'uitrainer-studio',
    studioVersion,
    studioApiVersion: STUDIO_API_VERSION,
  })
  response.writeHead(200, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
    ...presenceCorsHeaders(request),
  })
  response.end(payload)
  return true
}
