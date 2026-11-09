import { execFile } from 'node:child_process'
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { promisify } from 'node:util'
import { app } from 'electron'

const execFileAsync = promisify(execFile)

export type StudioConfig = {
  dataDir: string
  port: number
}

const REG_KEY = 'HKCU\\Software\\uitrainer Studio'

export function configPath(): string {
  return path.join(app.getPath('userData'), 'config.json')
}

export function defaultDataDir(): string {
  return path.join(app.getPath('documents'), 'uitrainer')
}

export async function readConfig(): Promise<StudioConfig> {
  try {
    const parsed = JSON.parse(await readFile(configPath(), 'utf8')) as Partial<StudioConfig>
    const port = Number(parsed.port)
    return {
      dataDir: typeof parsed.dataDir === 'string' && parsed.dataDir.trim() ? parsed.dataDir : defaultDataDir(),
      port: Number.isInteger(port) && port >= 1024 && port <= 65535 ? port : 44731,
    }
  } catch {
    const fromReg = await readInstallerRegistry()
    const config: StudioConfig = {
      dataDir: fromReg.dataDir ?? defaultDataDir(),
      port: fromReg.port ?? 44731,
    }
    await writeConfig(config)
    return config
  }
}

export async function writeConfig(config: StudioConfig): Promise<void> {
  const dir = path.dirname(configPath())
  await mkdir(dir, { recursive: true })
  await writeFile(configPath(), JSON.stringify(config, null, 2))
}

async function readInstallerRegistry(): Promise<Partial<StudioConfig>> {
  if (process.platform !== 'win32') return {}
  try {
    const { stdout } = await execFileAsync('reg', ['query', REG_KEY])
    const dataDir = stdout.match(/DataDir\s+REG_SZ\s+(.+)/i)?.[1]?.trim()
    const portRaw = stdout.match(/Port\s+REG_SZ\s+(\d+)/i)?.[1]
    const port = portRaw ? Number(portRaw) : undefined
    return {
      dataDir: dataDir && dataDir.length > 0 ? dataDir : undefined,
      port: port != null && Number.isInteger(port) && port >= 1024 && port <= 65535 ? port : undefined,
    }
  } catch {
    return {}
  }
}

export function applyDataEnv(config: StudioConfig): { lessonsDir: string; capturesDir: string } {
  const lessonsDir = path.join(config.dataDir, 'lessons')
  const capturesDir = path.join(config.dataDir, 'captures')
  const modelsDir = path.join(config.dataDir, 'models')
  const browsersDir = path.join(config.dataDir, 'playwright-browsers')
  process.env.UITRAINER_MODEL_DIR = modelsDir
  process.env.PLAYWRIGHT_BROWSERS_PATH = browsersDir
  process.env.UITRAINER_STUDIO_VERSION = app.getVersion()
  return { lessonsDir, capturesDir }
}

