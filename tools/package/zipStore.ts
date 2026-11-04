const CRC_TABLE = new Uint32Array(256)
for (let index = 0; index < 256; index += 1) {
  let crc = index
  for (let bit = 0; bit < 8; bit += 1) crc = (crc & 1) ? (0xedb88320 ^ (crc >>> 1)) : (crc >>> 1)
  CRC_TABLE[index] = crc >>> 0
}

export function crc32(data: Uint8Array): number {
  let crc = 0xffffffff
  for (const byte of data) crc = CRC_TABLE[(crc ^ byte) & 0xff]! ^ (crc >>> 8)
  return (crc ^ 0xffffffff) >>> 0
}

export type ZipEntry = { path: string; bytes: Uint8Array }

function dosTime(date: Date): { time: number; day: number } {
  const time = (date.getSeconds() >> 1) | (date.getMinutes() << 5) | (date.getHours() << 11)
  const day = date.getDate() | ((date.getMonth() + 1) << 5) | ((date.getFullYear() - 1980) << 9)
  return { time, day }
}

export function zipStore(entries: ZipEntry[], modified = new Date('2026-11-04T00:00:00Z')): Uint8Array {
  const stamp = dosTime(modified)
  const locals: Uint8Array[] = []
  const centrals: Uint8Array[] = []
  let offset = 0
  for (const entry of entries) {
    const name = new TextEncoder().encode(entry.path)
    const crc = crc32(entry.bytes)
    const local = new Uint8Array(30 + name.length + entry.bytes.length)
    const view = new DataView(local.buffer)
    view.setUint32(0, 0x04034b50, true)
    view.setUint16(4, 20, true)
    view.setUint16(6, 0x0800, true)
    view.setUint16(8, 0, true)
    view.setUint16(10, stamp.time, true)
    view.setUint16(12, stamp.day, true)
    view.setUint32(14, crc, true)
    view.setUint32(18, entry.bytes.length, true)
    view.setUint32(22, entry.bytes.length, true)
    view.setUint16(26, name.length, true)
    view.setUint16(28, 0, true)
    local.set(name, 30)
    local.set(entry.bytes, 30 + name.length)
    locals.push(local)
    const central = new Uint8Array(46 + name.length)
    const centralView = new DataView(central.buffer)
    centralView.setUint32(0, 0x02014b50, true)
    centralView.setUint16(4, 20, true)
    centralView.setUint16(6, 20, true)
    centralView.setUint16(8, 0x0800, true)
    centralView.setUint16(10, 0, true)
    centralView.setUint16(12, stamp.time, true)
    centralView.setUint16(14, stamp.day, true)
    centralView.setUint32(16, crc, true)
    centralView.setUint32(20, entry.bytes.length, true)
    centralView.setUint32(24, entry.bytes.length, true)
    centralView.setUint16(28, name.length, true)
    centralView.setUint16(30, 0, true)
    centralView.setUint16(32, 0, true)
    centralView.setUint16(34, 0, true)
    centralView.setUint16(36, 0, true)
    centralView.setUint32(38, 0, true)
    centralView.setUint32(42, offset, true)
    central.set(name, 46)
    centrals.push(central)
    offset += local.length
  }
  const centralSize = centrals.reduce((sum, item) => sum + item.length, 0)
  const end = new Uint8Array(22)
  const endView = new DataView(end.buffer)
  endView.setUint32(0, 0x06054b50, true)
  endView.setUint16(4, 0, true)
  endView.setUint16(6, 0, true)
  endView.setUint16(8, entries.length, true)
  endView.setUint16(10, entries.length, true)
  endView.setUint32(12, centralSize, true)
  endView.setUint32(16, offset, true)
  endView.setUint16(20, 0, true)
  const total = offset + centralSize + end.length
  const out = new Uint8Array(total)
  let cursor = 0
  for (const local of locals) {
    out.set(local, cursor)
    cursor += local.length
  }
  for (const central of centrals) {
    out.set(central, cursor)
    cursor += central.length
  }
  out.set(end, cursor)
  return out
}

export function unzipStore(archive: Uint8Array): Map<string, Uint8Array> {
  const view = new DataView(archive.buffer, archive.byteOffset, archive.byteLength)
  let end = -1
  for (let index = archive.length - 22; index >= 0; index -= 1) {
    if (view.getUint32(index, true) === 0x06054b50) {
      end = index
      break
    }
  }
  if (end < 0) throw new Error('Это не zip-архив')
  const count = view.getUint16(end + 10, true)
  let cursor = view.getUint32(end + 16, true)
  const files = new Map<string, Uint8Array>()
  for (let index = 0; index < count; index += 1) {
    if (view.getUint32(cursor, true) !== 0x02014b50) throw new Error('Повреждён каталог zip')
    const method = view.getUint16(cursor + 10, true)
    const compressed = view.getUint32(cursor + 20, true)
    const nameLength = view.getUint16(cursor + 28, true)
    const extra = view.getUint16(cursor + 30, true)
    const comment = view.getUint16(cursor + 32, true)
    const localOffset = view.getUint32(cursor + 42, true)
    const name = new TextDecoder().decode(archive.subarray(cursor + 46, cursor + 46 + nameLength))
    cursor += 46 + nameLength + extra + comment
    if (name.endsWith('/')) continue
    if (view.getUint32(localOffset, true) !== 0x04034b50) throw new Error(`Нет локального заголовка для ${name}`)
    const localName = view.getUint16(localOffset + 26, true)
    const localExtra = view.getUint16(localOffset + 28, true)
    const start = localOffset + 30 + localName + localExtra
    const bytes = archive.subarray(start, start + compressed)
    if (method !== 0) throw new Error(`Файл ${name} сжат способом, который пакет урока не использует`)
    if (crc32(bytes) !== view.getUint32(localOffset + 14, true)) throw new Error(`Контрольная сумма ${name} не сходится`)
    files.set(name, bytes)
  }
  return files
}
