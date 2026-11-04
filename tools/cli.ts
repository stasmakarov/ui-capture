import path from 'node:path'
import { lessonIdFromName, recordInteractive } from './capture/interactive.ts'
import { defaultHtml, publishCapture } from './pipeline/publish.ts'
import { packHtml } from './pack/packHtml.ts'

type Mode = 'dom' | 'screenshot' | 'auto'

const HELP = `ui-capture — запись урока с живого сайта и сборка тренажёра

Запись (откроется окно браузера; остановите закрытием окна или Ctrl+C):
  npx ui-capture <url> [--lesson-name "Найти извещение"] [--mode auto|dom|screenshot] [--out ./lessons]
  npm run capture -- <url> --lesson-name "Найти извещение"

Студия в браузере (запись, голос, правки, скачивание trainer.html):
  npx ui-capture studio
  npm start

Пересобрать один HTML из уже записанных уроков в каталоге:
  npx ui-capture pack [--out ./lessons] [--html ./lessons/trainer.html]

Скачать офлайн-модель распознавания русской речи:
  npx ui-capture whisper-download

Браузер:
  --headless              без окна (для проверки)
  --channel chrome        системный Google Chrome
  --executable-path PATH  Яндекс Браузер или другой Chromium

Пример zakupki.gov.ru:
  npx ui-capture "https://zakupki.gov.ru/epz/main/public/home.html" --lesson-name "Найти извещение"
`

function flag(argv: string[], name: string): string | undefined {
  const index = argv.indexOf(name)
  if (index === -1) return undefined
  return argv[index + 1]
}

function has(argv: string[], name: string): boolean {
  return argv.includes(name)
}

function parseMode(value: string | undefined): Mode {
  if (value === 'dom' || value === 'screenshot' || value === 'auto') return value
  if (value === undefined) return 'auto'
  throw new Error(`Неизвестный --mode ${value}. Допустимо: dom, screenshot, auto`)
}

async function main() {
  const argv = process.argv.slice(2)
  if (argv.length === 0 || has(argv, '--help') || has(argv, '-h')) {
    console.log(HELP)
    return
  }
  if (argv[0] === 'studio') {
    const { startStudio } = await import('./studio/server.ts')
    const port = Number(flag(argv, '--port') ?? 44731)
    const studio = await startStudio({
      port,
      open: !has(argv, '--no-open'),
      lessonsDir: path.resolve(flag(argv, '--out') ?? './lessons'),
      capturesDir: path.resolve('captures'),
    })
    console.log(`Студия: ${studio.url}`)
    console.log('Остановка: Ctrl+C')
    await new Promise(() => undefined)
    return
  }
  if (argv[0] === 'whisper-download') {
    const { downloadWhisper } = await import('./studio/transcribe.ts')
    console.log('Скачиваю whisper-small. Нужен интернет, дальше модель работает офлайн.')
    await downloadWhisper()
    console.log('Модель сохранена в .ui-capture/models')
    return
  }
  if (argv[0] === 'pack') {
    const lessonsDir = path.resolve(flag(argv, '--out') ?? './lessons')
    const html = path.resolve(flag(argv, '--html') ?? defaultHtml(lessonsDir))
    const packed = await packHtml({ lessonsDir, outFile: html })
    console.log(`Тренажёр: ${packed.outFile}`)
    console.log(`Уроки: ${packed.ids.join(', ')}`)
    return
  }
  const url = argv.find((item) => !item.startsWith('--') && argv[argv.indexOf(item) - 1]?.startsWith('--') !== true)
  if (!url || url === 'pack') throw new Error('Укажите адрес сайта. npx ui-capture --help')
  let parsed: URL
  try {
    parsed = new URL(url)
  } catch {
    throw new Error(`Некорректный адрес: ${url}`)
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new Error('Нужен адрес http или https')
  }
  const lessonName = flag(argv, '--lesson-name') ?? parsed.hostname
  const lessonsDir = path.resolve(flag(argv, '--out') ?? './lessons')
  const mode = parseMode(flag(argv, '--mode'))
  const lessonId = lessonIdFromName(lessonName)
  console.log(`Запись «${lessonName}»`)
  console.log(url)
  console.log('Пройдите сценарий в окне браузера. Остановка: закройте окно или нажмите Ctrl+C.')
  console.log('После последнего действия подождите секунду, чтобы экран успел сохраниться.')
  const captureDir = await recordInteractive({
    url,
    lessonName,
    lessonId,
    mode,
    capturesDir: path.resolve('captures'),
    headless: has(argv, '--headless'),
    channel: flag(argv, '--channel'),
    executablePath: flag(argv, '--executable-path'),
  })
  const html = await publishCapture(captureDir, lessonsDir, defaultHtml(lessonsDir))
  console.log(`Урок: ${path.join(lessonsDir, lessonId)}`)
  console.log(`Файл для двойного щелчка: ${html}`)
}

main().catch((error: unknown) => {
  console.error(error instanceof Error ? error.message : error)
  process.exitCode = 1
})
