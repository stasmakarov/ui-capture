import type { EmbeddedPack } from './embedded-stub.ts'

export type { EmbeddedCatalogItem, EmbeddedLesson, EmbeddedPack } from './embedded-stub.ts'

function readPack(): EmbeddedPack | null {
  if (typeof document === 'undefined') return null
  const el = document.getElementById('uitrainer-pack')
  if (!el?.textContent?.trim()) return null
  try {
    return JSON.parse(el.textContent) as EmbeddedPack
  } catch {
    return null
  }
}

export const embeddedPack = readPack()
