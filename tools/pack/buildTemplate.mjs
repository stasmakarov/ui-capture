import { spawnSync } from 'node:child_process'
import { existsSync, renameSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const vite = spawnSync('npx', ['vite', 'build', '--mode', 'singlefile'], { cwd: root, stdio: 'inherit', shell: true })
if (vite.status !== 0) process.exit(vite.status ?? 1)
const from = path.join(root, 'dist-template/player.html')
const to = path.join(root, 'dist-template/trainer-template.html')
if (!existsSync(from)) {
  console.error('vite не собрал dist-template/player.html')
  process.exit(1)
}
renameSync(from, to)
