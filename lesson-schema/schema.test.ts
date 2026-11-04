import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { migrateLesson } from './src/lesson.ts'
import { lessonJsonSchema, renderLessonSchema } from './src/schema.ts'

describe('JSON Schema урока', () => {
  it('совпадает с записанным файлом схемы', () => {
    const disk = readFileSync(new URL('./schema/lesson.schema.json', import.meta.url), 'utf8')
    expect(disk).toBe(renderLessonSchema())
    expect(lessonJsonSchema.properties.schemaVersion.const).toBe(1)
    expect(lessonJsonSchema.properties.screens.items.properties.html.description).toContain('screens/')
  })

  it('открывает записанные уроки и урок без номера версии', () => {
    for (const id of ['najti-statyu', 'spisok-zadach']) {
      const raw = JSON.parse(readFileSync(new URL(`../lessons/${id}/lesson.json`, import.meta.url), 'utf8'))
      expect(migrateLesson(raw).id).toBe(id)
    }
    const legacy = JSON.parse(readFileSync(new URL('../lessons/najti-statyu/lesson.json', import.meta.url), 'utf8')) as { schemaVersion?: number }
    delete legacy.schemaVersion
    expect(migrateLesson(legacy).schemaVersion).toBe(1)
  })
})
