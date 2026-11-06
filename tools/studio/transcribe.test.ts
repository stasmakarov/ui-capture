import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'
import { downloadWhisper, onWhisperStatus, transcribeWav, whisperStatus } from './transcribe.ts'

const previousModel = process.env.UITRAINER_MODEL_DIR
const previousSimulate = process.env.UITRAINER_WHISPER_SIMULATE
const previousLegacyModel = process.env.UI_CAPTURE_MODEL_DIR
const previousLegacySimulate = process.env.UI_CAPTURE_WHISPER_SIMULATE

describe('downloadWhisper', () => {
  afterEach(() => {
    if (previousModel === undefined) delete process.env.UITRAINER_MODEL_DIR
    else process.env.UITRAINER_MODEL_DIR = previousModel
    if (previousSimulate === undefined) delete process.env.UITRAINER_WHISPER_SIMULATE
    else process.env.UITRAINER_WHISPER_SIMULATE = previousSimulate
    if (previousLegacyModel === undefined) delete process.env.UI_CAPTURE_MODEL_DIR
    else process.env.UI_CAPTURE_MODEL_DIR = previousLegacyModel
    if (previousLegacySimulate === undefined) delete process.env.UI_CAPTURE_WHISPER_SIMULATE
    else process.env.UI_CAPTURE_WHISPER_SIMULATE = previousLegacySimulate
  })

  it('в имитации сообщает проценты и отмечает модель установленной', async () => {
    const dir = await mkdtemp(path.join(tmpdir(), 'ui-whisper-'))
    delete process.env.UI_CAPTURE_MODEL_DIR
    delete process.env.UI_CAPTURE_WHISPER_SIMULATE
    process.env.UITRAINER_MODEL_DIR = dir
    process.env.UITRAINER_WHISPER_SIMULATE = '1'
    const percents: number[] = []
    const stop = onWhisperStatus((status) => {
      if (status.phase === 'downloading') percents.push(status.percent)
    })
    try {
      await downloadWhisper()
      expect(percents).toContain(55)
      expect(whisperStatus().phase).toBe('ready')
      expect(whisperStatus().hint).toBe('Модель установлена')
      await expect(transcribeWav('нет-файла.wav')).resolves.toBe('комментарий к шагу')
    } finally {
      stop()
      await rm(dir, { recursive: true, force: true })
    }
  })
})
