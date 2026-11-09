import * as esbuild from 'esbuild'
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const desktop = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
const root = path.dirname(desktop)
const watch = process.argv.includes('--dev')

if (!watch) {
  const vite = spawnSync('npx', ['vite', 'build'], { cwd: root, stdio: 'inherit', shell: true })
  if (vite.status !== 0) process.exit(vite.status ?? 1)
}

const template = spawnSync(process.execPath, [path.join(root, 'tools/pack/buildTemplate.mjs')], { cwd: root, stdio: 'inherit' })
if (template.status !== 0) process.exit(template.status ?? 1)

const common = {
  bundle: true,
  platform: 'node',
  format: 'cjs',
  sourcemap: true,
  logLevel: 'info',
}

const external = ['electron', 'electron-updater', 'playwright', 'playwright-core', '@xenova/transformers', 'onnxruntime-node', 'sharp', 'vite', 'esbuild', 'jsdom']

await esbuild.build({
  ...common,
  entryPoints: [path.join(desktop, 'src/main.ts')],
  outfile: path.join(desktop, 'dist/main.cjs'),
  external: [...external, './server.cjs'],
})

await esbuild.build({
  ...common,
  entryPoints: [path.join(desktop, 'src/preload.ts')],
  outfile: path.join(desktop, 'dist/preload.cjs'),
  external: ['electron'],
})

await esbuild.build({
  ...common,
  entryPoints: [path.join(root, 'tools/studio/server.ts')],
  outfile: path.join(desktop, 'dist/server.cjs'),
  external,
})
