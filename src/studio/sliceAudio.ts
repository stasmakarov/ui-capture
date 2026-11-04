import { clipBounds, encodeWavMono, sliceChannel } from '../../shared/wav.ts'

export type StepMark = { t: number }

export async function sliceRecording(blob: Blob, marks: StepMark[]): Promise<string[]> {
  if (!blob.size || marks.length === 0) return []
  const context = new AudioContext()
  const decoded = await context.decodeAudioData(await blob.arrayBuffer())
  const channel = decoded.getChannelData(0)
  const clips: string[] = []
  const times = marks.map((mark) => mark.t)
  for (let index = 0; index < marks.length; index += 1) {
    const { start, end } = clipBounds(index, times, decoded.duration)
    const samples = sliceChannel(channel, decoded.sampleRate, start, end)
    if (!samples.length) {
      clips.push('')
      continue
    }
    const wav = encodeWavMono(samples, decoded.sampleRate)
    let binary = ''
    for (let i = 0; i < wav.length; i += 1) binary += String.fromCharCode(wav[i] ?? 0)
    clips.push(btoa(binary))
  }
  await context.close()
  return clips
}
