import { app, BrowserWindow, dialog, ipcMain, Menu, session, shell } from 'electron'
import { existsSync } from 'node:fs'
import { cp, mkdir, readdir } from 'node:fs/promises'
import net from 'node:net'
import path from 'node:path'
import { applyDataEnv, readConfig, writeConfig, type StudioConfig } from './config.ts'
import { checkUpdatesNow, startUpdater } from './updater.ts'
import { parseLaunchUrl } from '../../tools/studio/launchUrl.ts'

type StudioServer = { url: string; close: () => Promise<void> }

let mainWindow: BrowserWindow | null = null
let studio: StudioServer | null = null
let config: StudioConfig
let lessonsDir = ''

const gotLock = app.requestSingleInstanceLock()
if (!gotLock) {
  app.quit()
} else {
  app.on('second-instance', (_event, argv) => {
    const url = findLaunchUrl(argv)
    if (url) void handleLaunchUrl(url)
    mainWindow?.show()
    mainWindow?.focus()
  })
}

if (process.defaultApp) {
  app.setAsDefaultProtocolClient('uitrainer', process.execPath, [path.resolve(process.argv[1] ?? '.')])
} else {
  app.setAsDefaultProtocolClient('uitrainer')
}

app.on('open-url', (event, url) => {
  event.preventDefault()
  void handleLaunchUrl(url)
})

function findLaunchUrl(argv: string[]): string | null {
  return argv.find((item) => item.startsWith('uitrainer://')) ?? null
}

function staticDir(): string | undefined {
  if (app.isPackaged) return path.join(process.resourcesPath, 'studio-ui')
  const fromRepo = path.join(path.dirname(path.dirname(__dirname)), 'dist')
  if (existsSync(path.join(fromRepo, 'index.html'))) return fromRepo
  return undefined
}

function isPortFree(port: number): Promise<boolean> {
  return new Promise((resolve) => {
    const server = net.createServer()
    server.once('error', () => resolve(false))
    server.listen(port, '127.0.0.1', () => {
      server.close(() => resolve(true))
    })
  })
}

async function askPort(busy: number): Promise<number | null> {
  const prompt = new BrowserWindow({
    width: 420,
    height: 220,
    parent: mainWindow ?? undefined,
    modal: Boolean(mainWindow),
    webPreferences: { preload: path.join(__dirname, 'preload.cjs') },
  })
  const html = `<!doctype html><meta charset="utf-8"><title>Порт занят</title>
  <body style="font-family:sans-serif;padding:16px">
  <p>Порт ${busy} занят. Укажите другой (1024–65535).</p>
  <form id="f"><input id="p" type="number" min="1024" max="65535" value="${busy + 1}" required>
  <button type="submit">Запустить</button>
  <button type="button" id="c">Отмена</button></form>
  <script>
    document.getElementById('f').onsubmit = (e) => {
      e.preventDefault()
      window.portDialog.submit(Number(document.getElementById('p').value))
    }
    document.getElementById('c').onclick = () => window.portDialog.cancel()
  </script></body>`
  await prompt.loadURL('data:text/html;charset=utf-8,' + encodeURIComponent(html))
  return new Promise((resolve) => {
    const done = (port: number | null) => {
      ipcMain.removeListener('port-chosen', onChosen)
      if (!prompt.isDestroyed()) prompt.close()
      resolve(port)
    }
    const onChosen = (_event: Electron.IpcMainEvent, port: number | null) => {
      if (port == null) {
        done(null)
        return
      }
      if (!Number.isInteger(port) || port < 1024 || port > 65535) return
      done(port)
    }
    ipcMain.on('port-chosen', onChosen)
    prompt.on('closed', () => done(null))
  })
}

async function startHttp(current: StudioConfig): Promise<StudioServer> {
  const dirs = applyDataEnv(current)
  lessonsDir = dirs.lessonsDir
  await mkdir(dirs.lessonsDir, { recursive: true })
  await mkdir(dirs.capturesDir, { recursive: true })
  const { startStudio } = require('./server.cjs') as typeof import('../../tools/studio/server.ts')
  return startStudio({
    port: current.port,
    open: false,
    lessonsDir: dirs.lessonsDir,
    capturesDir: dirs.capturesDir,
    staticDir: staticDir(),
    studioVersion: app.getVersion(),
  })
}

async function ensureStudio(): Promise<void> {
  config = await readConfig()
  for (;;) {
    const free = await isPortFree(config.port)
    if (free) break
    const next = await askPort(config.port)
    if (next == null) {
      app.quit()
      return
    }
    const nextFree = await isPortFree(next)
    if (!nextFree) {
      await dialog.showMessageBox({ type: 'warning', message: `Порт ${next} занят` })
      continue
    }
    config = { ...config, port: next }
    await writeConfig(config)
    break
  }
  studio = await startHttp(config)
  startUpdater(lessonsDir)
}

async function handleLaunchUrl(raw: string): Promise<void> {
  try {
    const task = parseLaunchUrl(raw)
    if (!studio) return
    await fetch(new URL('/api/platform/link', studio.url), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ server: task.server, ticket: task.ticket, course: task.course }),
    })
    mainWindow?.loadURL(studio.url)
    mainWindow?.show()
    mainWindow?.focus()
  } catch (error) {
    await dialog.showErrorBox('uitrainer Studio', error instanceof Error ? error.message : String(error))
  }
}

function originFor(port: number): string {
  return `http://127.0.0.1:${port}`
}

async function createWindow(): Promise<void> {
  if (!studio) return
  const origin = originFor(config.port)
  session.defaultSession.setPermissionRequestHandler((webContents, permission, callback) => {
    const url = webContents.getURL()
    callback(permission === 'media' && url.startsWith(origin))
  })
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 900,
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      sandbox: false,
    },
  })
  mainWindow.on('closed', () => {
    mainWindow = null
  })
  await mainWindow.loadURL(studio.url)
}

function installMenu(): void {
  const template: Electron.MenuItemConstructorOptions[] = [
    {
      label: 'Справка',
      submenu: [
        {
          label: 'О программе',
          click: () => {
            mainWindow?.webContents.send('desktop:open-about')
            void mainWindow?.webContents.executeJavaScript(
              "document.querySelector('button.tertiary') && Array.from(document.querySelectorAll('button')).find(b => b.textContent === 'О программе')?.click()",
            )
          },
        },
        {
          label: 'Проверить обновления',
          click: () => {
            void checkUpdatesNow()
          },
        },
      ],
    },
  ]
  if (process.platform === 'darwin') {
    template.unshift({ role: 'appMenu' })
  }
  Menu.setApplicationMenu(Menu.buildFromTemplate(template))
}

ipcMain.handle('desktop:getConfig', async () => config)
ipcMain.handle('desktop:getAppVersion', () => app.getVersion())
ipcMain.handle('desktop:checkUpdates', () => checkUpdatesNow())
ipcMain.handle('desktop:pickDataDir', async () => {
  const result = await dialog.showOpenDialog(mainWindow ?? undefined, {
    properties: ['openDirectory', 'createDirectory'],
  })
  return result.canceled ? null : result.filePaths[0]
})
ipcMain.handle('desktop:saveSettings', async (_event, payload: { dataDir: string; port: number; migrate: 'move' | 'switch' | null }) => {
  const port = Number(payload.port)
  if (!Number.isInteger(port) || port < 1024 || port > 65535) return { error: 'Порт должен быть от 1024 до 65535' }
  if (!(await isPortFree(port)) && port !== config.port) return { error: `Порт ${port} занят` }
  const nextDir = payload.dataDir.trim()
  if (!nextDir) return { error: 'Укажите папку данных' }
  if (payload.migrate === 'move' && nextDir !== config.dataDir) {
    try {
      const entries = existsSync(nextDir) ? await readdir(nextDir) : []
      if (entries.length > 0) {
        const confirm = await dialog.showMessageBox(mainWindow ?? undefined, {
          type: 'warning',
          buttons: ['Отмена', 'Перенести'],
          defaultId: 0,
          message: 'Новая папка не пуста. Перенести данные всё равно?',
        })
        if (confirm.response !== 1) return { error: 'Перенос отменён' }
      }
      await mkdir(nextDir, { recursive: true })
      for (const name of ['lessons', 'captures', 'models', 'playwright-browsers']) {
        const from = path.join(config.dataDir, name)
        if (existsSync(from)) await cp(from, path.join(nextDir, name), { recursive: true })
      }
    } catch (error) {
      return { error: error instanceof Error ? error.message : String(error) }
    }
  }
  if (payload.migrate === 'switch' && nextDir !== config.dataDir) {
    await dialog.showMessageBox(mainWindow ?? undefined, {
      type: 'info',
      message: 'Старые уроки остались в прежней папке.',
    })
  }
  config = { dataDir: nextDir, port }
  await writeConfig(config)
  await dialog.showMessageBox(mainWindow ?? undefined, { message: 'Перезапустите студию, чтобы применить настройки.' })
  return { ok: true as const }
})

app.whenReady().then(async () => {
  if (!app.isPackaged) process.chdir(path.join(__dirname, '..', '..'))
  installMenu()
  await ensureStudio()
  await createWindow()
  const cold = findLaunchUrl(process.argv)
  if (cold) await handleLaunchUrl(cold)
})

app.on('window-all-closed', () => {
  void studio?.close()
  app.quit()
})

app.on('web-contents-created', (_event, contents) => {
  contents.setWindowOpenHandler(({ url }) => {
    void shell.openExternal(url)
    return { action: 'deny' }
  })
})
