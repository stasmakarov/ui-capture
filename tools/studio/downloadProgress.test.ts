import { describe, expect, it } from 'vitest'
import { downloadLabel, formatMegabytes, summarizeDownload } from './downloadProgress.ts'

describe('summarizeDownload', () => {
  it('суммирует байты нескольких файлов модели', () => {
    const summary = summarizeDownload([
      { file: 'model.onnx', loaded: 25_000_000, total: 50_000_000 },
      { file: 'tokenizer.json', loaded: 5_000_000, total: 10_000_000 },
    ])
    expect(summary.percent).toBe(50)
    expect(summary.loadedBytes).toBe(30_000_000)
    expect(formatMegabytes(summary.loadedBytes)).toBe('28.6 МБ')
    expect(downloadLabel(summary.percent, summary.loadedBytes, summary.totalBytes)).toBe('Загрузка 50% · 28.6 МБ из 57.2 МБ')
  })
})
