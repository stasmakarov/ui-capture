import path from 'node:path'

export async function bundleInpage(): Promise<string> {
  const esbuild = await import('esbuild')
  const result = await esbuild.build({
    entryPoints: [path.resolve('tools/capture/inpage-entry.ts')],
    bundle: true,
    format: 'iife',
    platform: 'browser',
    write: false,
    target: 'es2022',
    logLevel: 'silent',
  })
  const file = result.outputFiles[0]
  if (!file) throw new Error('Не удалось собрать скрипт снимка')
  return file.text
}
