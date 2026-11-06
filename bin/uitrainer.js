#!/usr/bin/env node
import { spawn } from 'node:child_process'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const require = createRequire(import.meta.url)
let tsx
try {
  tsx = path.join(path.dirname(require.resolve('tsx/package.json')), 'dist', 'cli.mjs')
} catch {
  console.error('Не найдены зависимости. В каталоге утилиты выполните npm install.')
  process.exit(1)
}
const cli = fileURLToPath(new URL('../tools/cli.ts', import.meta.url))
const child = spawn(process.execPath, [tsx, cli, ...process.argv.slice(2)], { stdio: 'inherit' })
child.on('exit', (code, signal) => {
  if (signal) process.kill(process.pid, signal)
  process.exit(code ?? 1)
})
