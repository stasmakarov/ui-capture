export type FileProgress = { file: string; loaded: number; total: number }

export function summarizeDownload(files: FileProgress[]): { percent: number; loadedBytes: number; totalBytes: number } {
  let loadedBytes = 0
  let totalBytes = 0
  for (const file of files) {
    loadedBytes += file.loaded
    totalBytes += file.total
  }
  const percent = totalBytes > 0 ? Math.min(100, Math.round((loadedBytes / totalBytes) * 100)) : 0
  return { percent, loadedBytes, totalBytes }
}

export function formatMegabytes(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toFixed(1)} МБ`
}

export function downloadLabel(percent: number, loadedBytes: number, totalBytes: number): string {
  if (totalBytes > 0) return `Загрузка ${percent}% · ${formatMegabytes(loadedBytes)} из ${formatMegabytes(totalBytes)}`
  return 'Загрузка…'
}
