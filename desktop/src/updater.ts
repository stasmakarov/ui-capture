import { autoUpdater, type UpdateInfo, type ProgressInfo } from 'electron-updater'
import { app, BrowserWindow, dialog } from 'electron'
import { existsSync } from 'node:fs'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const SIX_HOURS = 6 * 60 * 60 * 1000

let lessonsDirForUpdates = ''
let pendingVersion = ''

export function startUpdater(lessonsDir: string): void {
  if (!app.isPackaged) return
  lessonsDirForUpdates = lessonsDir
  autoUpdater.autoDownload = true
  autoUpdater.autoInstallOnAppQuit = true
  void applyFeed(lessonsDir)
  autoUpdater.on('update-available', (info: UpdateInfo) => {
    pendingVersion = info.version
    sendProgress({ version: info.version, percent: 0 })
  })
  autoUpdater.on('download-progress', (progress: ProgressInfo) => {
    sendProgress({ version: pendingVersion || app.getVersion(), percent: Math.round(progress.percent) })
  })
  autoUpdater.on('update-downloaded', () => {
    sendProgress(null)
    const win = BrowserWindow.getAllWindows()[0]
    const box = win
      ? dialog.showMessageBox(win, {
          type: 'info',
          buttons: ['Перезапустить', 'Позже'],
          defaultId: 0,
          message: 'Обновление загружено. Перезапустить сейчас?',
        })
      : dialog.showMessageBox({
          type: 'info',
          buttons: ['Перезапустить', 'Позже'],
          defaultId: 0,
          message: 'Обновление загружено. Перезапустить сейчас?',
        })
    void box.then((result) => {
      if (result.response === 0) autoUpdater.quitAndInstall()
    })
  })
  void autoUpdater.checkForUpdates().catch(() => undefined)
  setInterval(() => {
    void autoUpdater.checkForUpdates().catch(() => undefined)
  }, SIX_HOURS)
}

export async function checkUpdatesNow(): Promise<void> {
  if (!app.isPackaged) {
    await dialog.showMessageBox({ message: 'Обновления выключены в режиме разработки.' })
    return
  }
  const server = await platformServer(lessonsDirForUpdates)
  if (!server) {
    await dialog.showMessageBox({ message: 'Студия не подключена к платформе — обновления берутся оттуда' })
    return
  }
  await applyFeed(lessonsDirForUpdates)
  try {
    const result = await autoUpdater.checkForUpdates()
    const current = app.getVersion()
    if (result?.isUpdateAvailable) {
      const next = result.updateInfo.version
      pendingVersion = next
      await dialog.showMessageBox({ message: `Найдена версия ${next}, скачиваю…` })
    } else {
      await dialog.showMessageBox({ message: `У вас последняя версия ${current}` })
    }
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error)
    await dialog.showMessageBox({
      type: 'warning',
      message: `Не удалось проверить обновления (${server}). ${detail}`,
    })
  }
}

function sendProgress(payload: { version: string; percent: number } | null): void {
  for (const win of BrowserWindow.getAllWindows()) {
    win.webContents.send('desktop:update-progress', payload)
  }
}

async function platformServer(lessonsDir: string): Promise<string | null> {
  const file = path.join(lessonsDir, '.studio-platform.json')
  if (!existsSync(file)) return null
  try {
    const parsed = JSON.parse(await readFile(file, 'utf8')) as { server?: string }
    return parsed.server?.replace(/\/+$/, '') || null
  } catch {
    return null
  }
}

async function applyFeed(lessonsDir: string): Promise<void> {
  const server = await platformServer(lessonsDir)
  if (!server) return
  autoUpdater.setFeedURL({ provider: 'generic', url: `${server}/api/studio/releases/win` })
}
