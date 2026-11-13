import { existsSync } from 'node:fs'
import { mkdir, mkdtemp, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { prepareCaptureDir } from './interactive.ts'

describe('prepareCaptureDir', () => {
  it('удаляет старые файлы captures/<id>', async () => {
    const root = await mkdtemp(path.join(tmpdir(), 'ui-cap-'))
    const stale = path.join(root, 'demo', 'screens')
    await mkdir(stale, { recursive: true })
    const leftover = path.join(stale, 's9.original.png')
    await writeFile(leftover, 'old')
    const captureDir = await prepareCaptureDir(root, 'demo')
    expect(captureDir).toBe(path.join(root, 'demo'))
    expect(existsSync(leftover)).toBe(false)
    expect(existsSync(path.join(captureDir, 'screens'))).toBe(true)
  })
})
