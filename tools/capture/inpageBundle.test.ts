import { mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { afterEach, describe, expect, it } from 'vitest'
import { loadInpageScript, resolveInpageScript } from './inpageBundle.ts'

const previous = process.env.UITRAINER_INPAGE

describe('loadInpageScript', () => {
  afterEach(() => {
    if (previous === undefined) delete process.env.UITRAINER_INPAGE
    else process.env.UITRAINER_INPAGE = previous
  })

  it('читает готовый файл из UITRAINER_INPAGE', async () => {
    const dir = await mkdtemp(path.join(tmpdir(), 'ui-inpage-'))
    const file = path.join(dir, 'inpage.js')
    await writeFile(file, '/*inpage-fixture*/')
    process.env.UITRAINER_INPAGE = file
    try {
      expect(resolveInpageScript()).toBe(file)
      await expect(loadInpageScript()).resolves.toBe('/*inpage-fixture*/')
    } finally {
      await rm(dir, { recursive: true, force: true })
    }
  })
})
