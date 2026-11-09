import { mkdtemp, mkdir, readFile, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { packHtml } from './packHtml.ts'

const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
)

const root = await mkdtemp(path.join(tmpdir(), 'pack-smoke-'))
const lessonDir = path.join(root, 'tiny')
await mkdir(path.join(lessonDir, 'screens'), { recursive: true })
await mkdir(path.join(lessonDir, 'original'), { recursive: true })
await writeFile(path.join(lessonDir, 'screens/s1.html'), '<html><body><h1>Экран</h1></body></html>')
await writeFile(path.join(lessonDir, 'original/s1.png'), PNG)
await writeFile(path.join(lessonDir, 'lesson.json'), JSON.stringify({
  schemaVersion: 1,
  id: 'tiny',
  title: 'Smoke',
  description: 'ci',
  sourceSite: 'https://example.test',
  viewport: { width: 800, height: 600 },
  screens: [{
    id: 's1',
    title: 'Экран',
    url: 'https://example.test/',
    html: 'screens/s1.html',
    screenshot: 'original/s1.png',
    hotspots: [],
    viewport: { width: 800, height: 600 },
  }],
  steps: [],
  meta: { generatedAt: '2026-01-01T00:00:00.000Z', generator: 'test', stepCount: 0, manuallyEditedSteps: 0, autoStepRatio: 1, fidelity: [], warnings: [], defaultRenderMode: 'dom' },
}))
const outFile = path.join(root, 'trainer.html')
const packed = await packHtml({ lessonsDir: root, outFile })
const html = await readFile(outFile, 'utf8')
if (!html.includes('"id":"tiny"') || !html.includes('id="uitrainer-pack"')) {
  throw new Error('smoke packHtml: в HTML нет JSON урока')
}
console.log(`smoke packHtml ok (${packed.bytes} bytes)`)
