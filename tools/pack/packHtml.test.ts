import { mkdtemp, mkdir, readFile, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { packHtml } from './packHtml.ts'

const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
)

const FIXTURE_TEMPLATE = `<!doctype html><html><body>
<script type="application/json" id="uitrainer-pack">{"catalog":[],"lessons":[]}</script>
<div id="root"></div>
</body></html>`

describe('packHtml', () => {
  it('вшивает JSON урока в шаблон без vite', async () => {
    const root = await mkdtemp(path.join(tmpdir(), 'pack-'))
    const lessonDir = path.join(root, 'tiny')
    await mkdir(path.join(lessonDir, 'screens'), { recursive: true })
    await mkdir(path.join(lessonDir, 'original'), { recursive: true })
    await writeFile(path.join(lessonDir, 'screens/s1.html'), '<html><body><h1>Экран</h1></body></html>')
    await writeFile(path.join(lessonDir, 'original/s1.png'), PNG)
    await writeFile(path.join(lessonDir, 'lesson.json'), JSON.stringify({
      schemaVersion: 1,
      id: 'tiny',
      title: 'Крошечный',
      description: 'фикстура',
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
      steps: [{
        id: 'st1',
        index: 0,
        screenId: 's1',
        action: 'click',
        hotspotId: 'h1',
        expectedValue: '',
        confirm: null,
        nextScreenId: null,
        instruction: 'Нажмите',
        hint: '',
        errorText: '',
      }],
      meta: {
        generatedAt: '2026-01-01T00:00:00.000Z',
        generator: 'test',
        stepCount: 1,
        manuallyEditedSteps: 0,
        autoStepRatio: 1,
        fidelity: [],
        warnings: [],
        defaultRenderMode: 'dom',
      },
    }))
    const templatePath = path.join(root, 'template.html')
    await writeFile(templatePath, FIXTURE_TEMPLATE)
    const outFile = path.join(root, 'trainer.html')
    const packed = await packHtml({ lessonsDir: root, outFile, templatePath })
    const html = await readFile(outFile, 'utf8')
    expect(packed.ids).toEqual(['tiny'])
    expect(html).toContain('"id":"tiny"')
    expect(html).toContain('Крошечный')
    expect(html).toContain('id="uitrainer-pack"')
    const source = await readFile(new URL('./packHtml.ts', import.meta.url), 'utf8')
    expect(source).not.toMatch(/\bnpx\b/)
    expect(source).not.toMatch(/vite build/)
    expect(source).not.toMatch(/from 'node:child_process'/)
  })
})
