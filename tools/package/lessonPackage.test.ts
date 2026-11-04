import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { buildPackageBytes, exportLessonPackage, importLessonPackage, openPackage } from './lessonPackage.ts'

const lesson = {
  schemaVersion: 1,
  id: 'demo-urok',
  title: 'Демо урок',
  description: 'Пакет',
  sourceSite: 'https://example.test',
  viewport: { width: 1280, height: 800 },
  screens: [{
    id: 's1',
    title: 'Экран',
    url: 'https://example.test',
    html: 'screens/s1.html',
    screenshot: 'original/s1.png',
    hotspots: [],
    viewport: { width: 1280, height: 800 },
  }],
  steps: [{
    id: 'step-1',
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
    audio: 'audio/step-1.wav',
  }],
  meta: {
    generatedAt: '2026-11-04',
    generator: 'test',
    stepCount: 1,
    manuallyEditedSteps: 0,
    autoStepRatio: 1,
    fidelity: [],
    warnings: [],
    defaultRenderMode: 'dom',
  },
}

describe('пакет .lesson.zip', () => {
  it('пишет манифест с хэшами и читает урок обратно', async () => {
    const root = await mkdtemp(path.join(tmpdir(), 'ui-pack-'))
    const source = path.join(root, 'demo-urok')
    await mkdir(path.join(source, 'screens'), { recursive: true })
    await mkdir(path.join(source, 'original'), { recursive: true })
    await mkdir(path.join(source, 'audio'), { recursive: true })
    await mkdir(path.join(source, 'compare'), { recursive: true })
    await writeFile(path.join(source, 'lesson.json'), JSON.stringify(lesson))
    await writeFile(path.join(source, 'screens/s1.html'), '<p>экран</p>')
    await writeFile(path.join(source, 'original/s1.png'), Buffer.from([137, 80, 78, 71]))
    await writeFile(path.join(source, 'audio/step-1.wav'), Buffer.from('RIFF'))
    await writeFile(path.join(source, 'compare/skip.txt'), 'не паковать')
    try {
      const packed = await exportLessonPackage(source)
      const opened = openPackage(packed)
      expect(opened.manifest.format).toBe('ui-capture-lesson')
      expect(opened.manifest.files.map((item) => item.path).sort()).toEqual([
        'audio/step-1.wav',
        'lesson.json',
        'original/s1.png',
        'screens/s1.html',
      ])
      expect(opened.manifest.files.every((item) => item.sha256.length === 64)).toBe(true)
      expect(opened.files.has('compare/skip.txt')).toBe(false)
      const restored = path.join(root, 'in')
      const imported = await importLessonPackage(packed, restored)
      expect(imported.title).toBe('Демо урок')
      expect(await readFile(path.join(restored, 'demo-urok/screens/s1.html'), 'utf8')).toBe('<p>экран</p>')
      const broken = new Uint8Array(packed)
      broken[broken.length - 5] ^= 0xff
      expect(() => openPackage(broken)).toThrow()
      const files = new Map<string, Uint8Array>([['lesson.json', new TextEncoder().encode(JSON.stringify(lesson))]])
      expect(() => buildPackageBytes(files)).toThrow(/screens\/s1.html/)
    } finally {
      await rm(root, { recursive: true, force: true })
    }
  })
})
