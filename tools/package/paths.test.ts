import { existsSync, readdirSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'
import { referencedPaths } from './lessonPackage.ts'
import { migrateLesson } from '../../lesson-schema/src/lesson.ts'

const REQUIRED = [
  'tools/cli.ts',
  'tools/capture/interactive.ts',
  'tools/capture/record.ts',
  'tools/capture/run.ts',
  'tools/pack/packHtml.ts',
  'tools/pack/singlefile.ts',
  'tools/pack/inlineAssets.ts',
  'bin/uitrainer.js',
]

describe('сверка путей урока и tools', () => {
  it('держится на закоммиченных tools/cli, capture и pack', () => {
    for (const file of REQUIRED) expect(existsSync(file), file).toBe(true)
  })

  it('экраны и аудио из lesson.json лежат рядом с уроком, compare и replay в схему не входят', () => {
    const lessons = readdirSync('lessons').filter((name) => statSync(path.join('lessons', name)).isDirectory())
    expect(lessons.length).toBeGreaterThan(0)
    for (const id of lessons) {
      const dir = path.join('lessons', id)
      const lesson = migrateLesson(JSON.parse(readFileSync(path.join(dir, 'lesson.json'), 'utf8')))
      for (const relative of referencedPaths(lesson)) {
        expect(relative.startsWith('screens/') || relative.startsWith('original/') || relative.startsWith('audio/') || relative.startsWith('data:')).toBe(true)
        if (!relative.startsWith('data:')) expect(existsSync(path.join(dir, relative)), `${id}/${relative}`).toBe(true)
      }
      const raw = readFileSync(path.join(dir, 'lesson.json'), 'utf8')
      expect(raw).not.toContain('"compare/')
      expect(raw).not.toContain('"replay/')
    }
  })
})
