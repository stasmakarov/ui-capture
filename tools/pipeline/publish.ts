import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright'
import { buildCapture, writeCatalog } from '../builder/build.ts'
import { serveLessons } from '../builder/staticServer.ts'
import { packHtml } from '../pack/packHtml.ts'

export async function publishCapture(captureDir: string, lessonsDir: string, htmlFile: string): Promise<string> {
  await mkdir(lessonsDir, { recursive: true })
  const server = await serveLessons(lessonsDir)
  const browser = await chromium.launch({ headless: true })
  try {
    console.log('Собираю экраны урока…')
    await buildCapture(captureDir, browser, server.url, lessonsDir)
    await writeCatalog(lessonsDir)
  } finally {
    await browser.close()
    await server.close()
  }
  console.log('Собираю один HTML-файл…')
  const packed = await packHtml({ lessonsDir, outFile: htmlFile })
  const mb = (packed.bytes / 1024 / 1024).toFixed(1)
  console.log(`Тренажёр: ${packed.outFile} (${mb} МБ, уроки: ${packed.ids.join(', ')})`)
  console.log('Откройте этот файл двойным щелчком — сервер не нужен.')
  return packed.outFile
}

export function defaultHtml(lessonsDir: string): string {
  return path.join(lessonsDir, 'trainer.html')
}
