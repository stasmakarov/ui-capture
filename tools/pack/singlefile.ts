import path from 'node:path'
import { packHtml } from './packHtml.ts'

const LIMIT = 25 * 1024 * 1024
const outFile = path.resolve('artifacts/ui-trainer-wiki.html')
const result = await packHtml({
  lessonsDir: path.resolve('lessons'),
  outFile,
  ids: ['najti-statyu', 'spisok-zadach'],
  copyTo: ['/cursor/stores/self/artifacts/ui-trainer-wiki.html'],
})
const mb = (result.bytes / 1024 / 1024).toFixed(1)
console.log(`Файл: ${result.outFile}`)
console.log(`Размер: ${mb} МБ`)
console.log(`Уроки: ${result.ids.join(', ')}`)
if (result.bytes > LIMIT) {
  throw new Error(`Файл больше 25 МБ: ${mb} МБ`)
}
