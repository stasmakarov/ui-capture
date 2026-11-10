import * as esbuild from 'esbuild'
import { mkdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const outdir = path.join(root, 'dist-inpage')
mkdirSync(outdir, { recursive: true })
await esbuild.build({
  entryPoints: [path.join(root, 'tools/capture/inpage-entry.ts')],
  outfile: path.join(outdir, 'inpage.js'),
  bundle: true,
  format: 'iife',
  platform: 'browser',
  target: 'es2022',
  logLevel: 'info',
})
