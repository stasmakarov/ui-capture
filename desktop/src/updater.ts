import { autoUpdater } from 'electron-updater'
import { app, BrowserWindow, dialog } from 'electron'
import { existsSync } from 'node:fs'
import { readFile } from 'node:fs/promises'
import path from 'node:path'

const SIX_HOURS = 6 * 60 * 60 * 1000

export function startUpdater(lessonsDir: string): void {
  if (!app.isPackaged) return
  autoUpdater.autoDownload = true
  autoUpdater.autoInstallOnAppQuit = true
  void applyFeed(lessonsDir)
  autoUpdater.on('update-downloaded', () => {
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
  const result = await autoUpdater.checkForUpdates()
  if (!result?.updateInfo) {
    await dialog.showMessageBox({ message: 'Обновлений нет.' })
  }
}

async function applyFeed(lessonsDir: string): Promise<void> {
  const file = path.join(lessonsDir, '.studio-platform.json')
  if (!existsSync(file)) return
  try {
    const parsed = JSON.parse(await readFile(file, 'utf8')) as { server?: string }
    if (!parsed.server) return
    autoUpdater.setFeedURL({ provider: 'generic', url: `${parsed.server.replace(/\/+$/, '')}/api/studio/releases/win` })
  } catch {
    /* оставляем app-update.yml со сборки */
  }
}
