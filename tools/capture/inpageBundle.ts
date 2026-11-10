import { existsSync } from 'node:fs'
import { readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

export function resolveInpageScript(explicit?: string): string | undefined {
  const envPath = process.env.UITRAINER_INPAGE
  const resources = (process as NodeJS.Process & { resourcesPath?: string }).resourcesPath
  const here = typeof __dirname === 'string' ? __dirname : path.dirname(fileURLToPath(import.meta.url))
  const candidates = [
    explicit,
    envPath,
    resources ? path.join(resources, 'inpage.js') : undefined,
    path.join(here, '..', '..', 'dist-inpage', 'inpage.js'),
    path.resolve('dist-inpage/inpage.js'),
  ].filter((item): item is string => Boolean(item))
  return candidates.find((item) => existsSync(item))
}

export async function loadInpageScript(): Promise<string> {
  const found = resolveInpageScript()
  if (found) return readFile(found, 'utf8')
  const { bundleInpage } = await import('./inpageBuild.ts')
  return bundleInpage()
}
