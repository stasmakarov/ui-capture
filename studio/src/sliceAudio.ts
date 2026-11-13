import { clipBounds, encodeWavMono, sliceChannel } from '../../shared/wav.ts'

export type StepMark = { t: number }

export async function sliceRecording(blob: Blob, marks: StepMark[]): Promise<Blob[]> {
  if (!blob.size || marks.length === 0) return []
  const context = new AudioContext()
  const decoded = await context.decodeAudioData(await blob.arrayBuffer())
  const channel = decoded.getChannelData(0)
  const clips: Blob[] = []
  const times = marks.map((mark) => mark.t)
  for (let index = 0; index < marks.length; index += 1) {
    const { start, end } = clipBounds(index, times, decoded.duration)
    const samples = sliceChannel(channel, decoded.sampleRate, start, end)
    if (!samples.length) {
      clips.push(new Blob())
      continue
    }
    const wav = encodeWavMono(samples, decoded.sampleRate)
    clips.push(new Blob([Uint8Array.from(wav)], { type: 'audio/wav' }))
  }
  await context.close()
  return clips
}
