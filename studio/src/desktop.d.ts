export type DesktopConfig = {
  dataDir: string
  port: number
}

export type DesktopApi = {
  isDesktop: true
  getConfig: () => Promise<DesktopConfig>
  pickDataDir: () => Promise<string | null>
  saveSettings: (payload: { dataDir: string; port: number; migrate: 'move' | 'switch' | null }) => Promise<{ ok: true } | { error: string }>
  checkUpdates: () => Promise<void>
  getAppVersion: () => Promise<string>
  onUpdateProgress: (callback: (info: { version: string; percent: number } | null) => void) => () => void
}

declare global {
  interface Window {
    uitrainerDesktop?: DesktopApi
  }
}

export {}
