import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'
import { encodeWavMono } from '../../shared/wav.ts'
import { attachStepAudio } from './attachAudio.ts'

const lesson = {
  schemaVersion: 1,
  id: 'audio-demo',
  title: 'Звук',
  description: '',
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
  }],
  meta: {
    generatedAt: '2026-11-13',
    generator: 'test',
    stepCount: 1,
    manuallyEditedSteps: 0,
    autoStepRatio: 1,
    fidelity: [],
    warnings: [],
    defaultRenderMode: 'dom',
  },
}

describe('attachStepAudio', () => {
  const previousFake = process.env.UITRAINER_FAKE_TRANSCRIPT

  afterEach(() => {
    if (previousFake === undefined) delete process.env.UITRAINER_FAKE_TRANSCRIPT
    else process.env.UITRAINER_FAKE_TRANSCRIPT = previousFake
  })

  it('пишет wav и распознаёт без base64', async () => {
    process.env.UITRAINER_FAKE_TRANSCRIPT = 'фраза'
    const root = await mkdtemp(path.join(tmpdir(), 'ui-audio-'))
    const dir = path.join(root, 'audio-demo')
    await mkdir(dir, { recursive: true })
    await writeFile(path.join(dir, 'lesson.json'), JSON.stringify(lesson))
    try {
      const wav = Buffer.from(encodeWavMono(new Float32Array(32), 16000))
      const result = await attachStepAudio(root, 'audio-demo', 'step-1', wav)
      expect(result.lesson.steps[0]?.audio).toBe('audio/step-1.wav')
      expect(result.lesson.steps[0]?.narration).toBe('фраза')
      const saved = await readFile(path.join(dir, 'audio/step-1.wav'))
      expect(saved.subarray(0, 4).toString()).toBe('RIFF')
    } finally {
      await rm(root, { recursive: true, force: true })
    }
  })
})
