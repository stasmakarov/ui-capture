import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'

const MIME: Record<string, string> = {
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.otf': 'font/otf',
  '.css': 'text/css',
  '.wav': 'audio/wav',
  '.webm': 'audio/webm',
}

const dataUriCache = new Map<string, string>()

function mimeOf(file: string): string {
  return MIME[path.extname(file).toLowerCase()] ?? 'application/octet-stream'
}

function fileToData(raw: string, baseDir: string): string | null {
  if (!raw || raw.startsWith('data:') || raw.startsWith('blob:') || raw.startsWith('#') || raw.startsWith('http:') || raw.startsWith('https:') || raw.startsWith('//')) {
    return null
  }
  const clean = decodeURIComponent(raw.split('#')[0]?.split('?')[0] ?? raw)
  if (!clean || path.isAbsolute(clean)) return null
  const abs = path.resolve(baseDir, clean)
  const cached = dataUriCache.get(abs)
  if (cached) return cached
  if (!existsSync(abs)) return null
  const data = `data:${mimeOf(abs)};base64,${readFileSync(abs).toString('base64')}`
  dataUriCache.set(abs, data)
  return data
}

function findUrlEnd(css: string, start: number): number {
  let i = start + 4
  while (css[i] === ' ' || css[i] === '\n' || css[i] === '\t') i += 1
  const quote = css[i] === '"' || css[i] === "'" ? css[i] : ''
  if (quote) {
    i += 1
    while (i < css.length) {
      if (css[i] === '\\') {
        i += 2
        continue
      }
      if (css[i] === quote) {
        i += 1
        break
      }
      i += 1
    }
  } else {
    while (i < css.length && css[i] !== ')') i += 1
  }
  while (css[i] === ' ') i += 1
  if (css[i] === ')') i += 1
  return i
}

function urlValue(full: string): string {
  const inner = full.slice(4, full.endsWith(')') ? -1 : undefined).trim()
  if (inner.startsWith('"') || inner.startsWith("'")) {
    return inner.slice(1, -1).replace(/\\(.)/g, '$1')
  }
  return inner
}

export function rewriteCssUrls(css: string, baseDir: string): string {
  let out = ''
  let cursor = 0
  while (cursor < css.length) {
    const idx = css.indexOf('url(', cursor)
    if (idx === -1) {
      out += css.slice(cursor)
      break
    }
    out += css.slice(cursor, idx)
    const end = findUrlEnd(css, idx)
    const full = css.slice(idx, end)
    const raw = urlValue(full)
    const data = raw.startsWith('data:') ? null : fileToData(raw, baseDir)
    out += data ? `url("${data}")` : full
    cursor = end
  }
  return out
}

function inlineStylesheets(html: string, htmlDir: string): string {
  return html.replace(/<link\b[^>]*>/gi, (tag) => {
    if (!/rel\s*=\s*["']?stylesheet["']?/i.test(tag)) return tag
    const href = /href\s*=\s*["']([^"']+)["']/i.exec(tag)?.[1]
    if (!href || href.startsWith('data:') || href.startsWith('http')) return tag
    const clean = decodeURIComponent(href.split('#')[0]?.split('?')[0] ?? href)
    if (!clean || path.isAbsolute(clean)) return tag
    const abs = path.resolve(htmlDir, clean)
    if (!existsSync(abs)) return tag
    const css = rewriteCssUrls(readFileSync(abs, 'utf8'), path.dirname(abs)).replace(/<\/style/gi, '<\\/style')
    return `<style>${css}</style>`
  })
}

function rewriteAttributes(html: string, htmlDir: string): string {
  return html.replace(/\s(src|poster)\s*=\s*("([^"]*)"|'([^']*)')/gi, (full, name: string, _quoted: string, dq: string | undefined, sq: string | undefined) => {
    const raw = dq ?? sq ?? ''
    const data = fileToData(raw, htmlDir)
    if (!data) return full
    return ` ${name}="${data}"`
  })
}

export function inlineScreenHtml(html: string, htmlFile: string): string {
  const htmlDir = path.dirname(htmlFile)
  const withCss = inlineStylesheets(html, htmlDir)
  const withoutSrcset = withCss.replace(/\s(?:srcset)\s*=\s*("(?:[^"]*)"|'(?:[^']*)')/gi, '')
  const withImages = rewriteAttributes(withoutSrcset, htmlDir)
  return rewriteCssUrls(withImages, htmlDir)
}

export function fileDataUri(file: string): string {
  const cached = dataUriCache.get(file)
  if (cached) return cached
  const data = `data:${mimeOf(file)};base64,${readFileSync(file).toString('base64')}`
  dataUriCache.set(file, data)
  return data
}
