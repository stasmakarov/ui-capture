export type LaunchTask = {
  course: string
  server: string
  ticket: string
}

const LAUNCH_PROTOCOLS = new Set(['uitrainer:'])

export function parseLaunchUrl(value: string): LaunchTask {
  const url = new URL(value)
  if (!LAUNCH_PROTOCOLS.has(url.protocol)) throw new Error('Ожидается ссылка uitrainer://')
  if (url.hostname !== 'new') throw new Error('Неизвестная команда ссылки. Ожидается uitrainer://new')
  const course = url.searchParams.get('course')?.trim() ?? ''
  const server = url.searchParams.get('server')?.trim() ?? ''
  const ticket = url.searchParams.get('ticket')?.trim() ?? ''
  if (!course || !server || !ticket) throw new Error('В ссылке нужны course, server и ticket')
  let parsed: URL
  try {
    parsed = new URL(server)
  } catch {
    throw new Error('server в ссылке должен быть адресом платформы')
  }
  if (parsed.protocol !== 'https:' && parsed.protocol !== 'http:') throw new Error('server должен быть http или https')
  return { course, server: parsed.origin, ticket }
}
