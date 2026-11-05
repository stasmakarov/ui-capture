import { mkdir, readFile, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { lessonIdFromName, recordInteractive } from './capture/interactive.ts'
import { defaultHtml, publishCapture } from './pipeline/publish.ts'
import { packHtml } from './pack/packHtml.ts'
import { exportLessonPackage, importLessonPackage } from './package/lessonPackage.ts'

type Mode = 'dom' | 'screenshot' | 'auto'

const HELP = `ui-capture — запись урока с живого сайта и сборка тренажёра

Запись (откроется окно браузера; остановите закрытием окна или Ctrl+C):
  npx ui-capture <url> [--lesson-name "Найти извещение"] [--mode auto|dom|screenshot] [--out ./lessons]
  npm run capture -- <url> --lesson-name "Найти извещение"

Студия в браузере (запись, голос, правки, скачивание trainer.html):
  npx ui-capture studio
  npm start
  В студии список «Браузер для записи»: Chromium, Chrome, Яндекс Браузер или свой путь.

Пересобрать один HTML из уже записанных уроков в каталоге:
  npx ui-capture pack [--out ./lessons] [--html ./lessons/trainer.html]

Пакет урока для платформы (.lesson.zip, манифест и SHA-256):
  npx ui-capture package <id> [--out ./lessons] [--file ./id.lesson.zip]
  npx ui-capture import <файл.lesson.zip> [--out ./lessons]

Связь с платформой (M2):
  npx ui-capture open-url "uicapture://new?course=…&server=…&ticket=…"   обменять тикет из админки на токен
  npx ui-capture platform-login http://localhost:8090 --token uicst_…   токен с экрана «Мои токены студии»
  npx ui-capture publish <id> [--out ./lessons]                          отправить урок черновиком на платформу

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
    const { downloadWhisper, onWhisperStatus } = await import('./studio/transcribe.ts')
    const { downloadLabel } = await import('./studio/downloadProgress.ts')
    console.log('Скачиваю whisper-small. Нужен интернет, дальше модель работает офлайн.')
    let last = -1
    const stop = onWhisperStatus((status) => {
      if (status.phase !== 'downloading' || status.percent === last) return
      last = status.percent
      console.log(downloadLabel(status.percent, status.loadedBytes, status.totalBytes))
    })
    try {
      await downloadWhisper()
    } finally {
      stop()
    }
    console.log('Модель установлена. Файлы: .ui-capture/models')
    return
  }
  if (argv[0] === 'open-url') {
    const { parseLaunchUrl } = await import('./studio/launchUrl.ts')
    const { exchangeTicket } = await import('./studio/platform.ts')
    const task = parseLaunchUrl(argv[1] ?? '')
    const lessonsDir = path.resolve(flag(argv, '--out') ?? './lessons')
    const link = await exchangeTicket(lessonsDir, task.server, task.ticket, task.course)
    console.log(`Студия связана с ${link.server} как ${link.username}${link.courseTitle ? `, курс «${link.courseTitle}»` : ''}.`)
    console.log('Запишите урок (npm start) и нажмите «Опубликовать на сервер» — или: npx ui-capture publish <id>')
    return
  }
  if (argv[0] === 'platform-login') {
    const { loginWithToken } = await import('./studio/platform.ts')
    const server = argv[1]
    const token = flag(argv, '--token') ?? process.env.UI_CAPTURE_STUDIO_TOKEN
    if (!server || server.startsWith('--') || !token) throw new Error('Использование: npx ui-capture platform-login <server> --token <токен> [--course <id>]')
    const lessonsDir = path.resolve(flag(argv, '--out') ?? './lessons')
    const link = await loginWithToken(lessonsDir, server, token, flag(argv, '--course'))
    console.log(`Студия связана с ${link.server} как ${link.username}.`)
    return
  }
  if (argv[0] === 'publish') {
    const id = argv[1]
    if (!id || id.startsWith('--')) throw new Error('Укажите идентификатор урока: npx ui-capture publish <id>')
    const { publishLesson } = await import('./studio/platform.ts')
    const lessonsDir = path.resolve(flag(argv, '--out') ?? './lessons')
    const result = await publishLesson(lessonsDir, id, { withTrainer: !has(argv, '--no-trainer') })
    console.log(`Урок «${result.title}» загружен как версия ${result.versionNo} (${result.status}): ${result.adminUrl}`)
    return
  }
  if (argv[0] === 'package') {
    const id = argv[1]
    if (!id || id.startsWith('--')) throw new Error('Укажите идентификатор урока: npx ui-capture package <id>')
    const lessonsDir = path.resolve(flag(argv, '--out') ?? './lessons')
    const file = path.resolve(flag(argv, '--file') ?? path.join(lessonsDir, `${id}.lesson.zip`))
    const bytes = await exportLessonPackage(path.join(lessonsDir, id))
    await mkdir(path.dirname(file), { recursive: true })
    await writeFile(file, bytes)
    console.log(`Пакет: ${file}`)
    return
  }
  if (argv[0] === 'import') {
    const file = argv[1]
    if (!file || file.startsWith('--')) throw new Error('Укажите файл пакета: npx ui-capture import <файл.lesson.zip>')
    const lessonsDir = path.resolve(flag(argv, '--out') ?? './lessons')
    const lesson = await importLessonPackage(new Uint8Array(await readFile(file)), lessonsDir)
    console.log(`Урок «${lesson.title}» записан в ${path.join(lessonsDir, lesson.id)}`)
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
