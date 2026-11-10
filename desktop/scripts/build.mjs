import * as esbuild from 'esbuild'
import { builtinModules } from 'node:module'
import { readFileSync } from 'node:fs'
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

const inpage = spawnSync(process.execPath, [path.join(root, 'tools/capture/buildInpage.mjs')], { cwd: root, stdio: 'inherit' })
if (inpage.status !== 0) process.exit(inpage.status ?? 1)

const common = {
  bundle: true,
  platform: 'node',
  format: 'cjs',
  sourcemap: true,
  logLevel: 'info',
}

const external = ['electron', 'electron-updater', 'playwright', 'playwright-core', '@xenova/transformers', 'onnxruntime-node', 'sharp', 'jsdom']

const stubInpageBuild = {
  name: 'stub-inpage-build',
  setup(build) {
    build.onLoad({ filter: /tools[/\\]capture[/\\]inpageBuild\.ts$/ }, () => ({
      contents: `export async function bundleInpage() {
  throw new Error('Нет скрипта снимка (dist-inpage/inpage.js). Пересоберите студию / npm run build:inpage')
}`,
      loader: 'js',
    }))
  },
}

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
  plugins: [stubInpageBuild],
})

const pkg = JSON.parse(readFileSync(path.join(desktop, 'package.json'), 'utf8'))
const allowed = new Set([
  ...Object.keys(pkg.dependencies ?? {}),
  'electron',
  './server.cjs',
  ...builtinModules,
  ...builtinModules.map((name) => `node:${name}`),
])

function checkRuntimeRequires(file) {
  const text = readFileSync(file, 'utf8')
  const re = /require\((['"])([^'"]+)\1\)/g
  const bad = new Set()
  let match
  while ((match = re.exec(text))) {
    const id = match[2]
    if (id.startsWith('.') || id.startsWith('/') || id.startsWith('node:')) continue
    const pkgName = id.startsWith('@') ? id.split('/').slice(0, 2).join('/') : id.split('/')[0]
    if (allowed.has(id) || allowed.has(pkgName) || builtinModules.includes(pkgName)) continue
    bad.add(id)
  }
  if (bad.size > 0) {
    console.error(`${path.relative(root, file)}: в рантайме нет пакетов ${[...bad].join(', ')} (их нет в dependencies desktop)`)
    process.exit(1)
  }
}

checkRuntimeRequires(path.join(desktop, 'dist/main.cjs'))
checkRuntimeRequires(path.join(desktop, 'dist/server.cjs'))
