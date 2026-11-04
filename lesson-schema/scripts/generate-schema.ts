import { writeFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { renderLessonSchema } from '../src/schema.ts'

const file = path.join(path.dirname(fileURLToPath(import.meta.url)), '../schema/lesson.schema.json')
await writeFile(file, renderLessonSchema())
console.log(file)
