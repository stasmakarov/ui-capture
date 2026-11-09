export const STUDIO_API_VERSION = 1

export const API_INCOMPATIBLE_MESSAGE = 'Платформа требует более новую студию, обновитесь'

export type PlatformVersionInfo = {
  platformVersion?: string
  apiVersion?: number
  minStudioApi?: number
  studioLatestRelease?: { version: string; url: string } | null
}

/** Совместимость: равенство major (пока major = целое apiVersion). */
export function isApiCompatible(platformApiVersion: number | undefined | null, studioApiVersion = STUDIO_API_VERSION): boolean {
  if (platformApiVersion == null || Number.isNaN(Number(platformApiVersion))) return true
  return Math.floor(Number(platformApiVersion)) === studioApiVersion
}

export async function fetchPlatformVersion(server: string): Promise<PlatformVersionInfo | null> {
  try {
    const response = await fetch(`${server.replace(/\/+$/, '')}/api/version`)
    if (!response.ok) return null
    return await response.json() as PlatformVersionInfo
  } catch {
    return null
  }
}

export function assertApiCompatible(info: PlatformVersionInfo | null): void {
  if (info && !isApiCompatible(info.apiVersion)) throw new Error(API_INCOMPATIBLE_MESSAGE)
}
