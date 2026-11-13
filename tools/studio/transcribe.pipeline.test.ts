import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const pipeline = vi.fn(async () => async () => ({ text: ' распознано ' }))

vi.mock('@xenova/transformers', () => ({
  env: { cacheDir: '', allowRemoteModels: true },
  pipeline,
}))

const previousFake = process.env.UITRAINER_FAKE_TRANSCRIPT
const previousModel = process.env.UITRAINER_MODEL_DIR

describe('кэш ASR pipeline', () => {
  beforeEach(() => {
    delete process.env.UITRAINER_FAKE_TRANSCRIPT
  })

  afterEach(() => {
    if (previousFake === undefined) delete process.env.UITRAINER_FAKE_TRANSCRIPT
    else process.env.UITRAINER_FAKE_TRANSCRIPT = previousFake
    if (previousModel === undefined) delete process.env.UITRAINER_MODEL_DIR
    else process.env.UITRAINER_MODEL_DIR = previousModel
    pipeline.mockClear()
  })

  it('создаёт pipeline один раз на два вызова', async () => {
    const { resetAsrPipeline, transcribeWav } = await import('./transcribe.ts')
    resetAsrPipeline()
    const dir = await mkdtemp(path.join(tmpdir(), 'ui-asr-'))
    process.env.UITRAINER_MODEL_DIR = dir
    await writeFile(path.join(dir, 'whisper-small.ready'), 'ok')
    try {
      await expect(transcribeWav('a.wav')).resolves.toBe('распознано')
      await expect(transcribeWav('b.wav')).resolves.toBe('распознано')
      expect(pipeline).toHaveBeenCalledTimes(1)
    } finally {
      resetAsrPipeline()
      await rm(dir, { recursive: true, force: true })
    }
  })
})
