import { encodeWavMono, sliceChannel } from '../../shared/wav.ts'

export type StepMark = { t: number }

export async function sliceRecording(blob: Blob, marks: StepMark[]): Promise<string[]> {
  if (!blob.size || marks.length === 0) return []
  const context = new AudioContext()
  const decoded = await context.decodeAudioData(await blob.arrayBuffer())
  const channel = decoded.getChannelData(0)
  const clips: string[] = []
  for (let index = 0; index < marks.length; index += 1) {
    const end = (marks[index]?.t ?? 0) + 0.35
    const start = index === 0 ? 0 : Math.max(0, (marks[index - 1]?.t ?? 0) - 0.05)
    const samples = sliceChannel(channel, decoded.sampleRate, start, Math.max(end, start + 0.3))
    if (!samples.length) continue
    const wav = encodeWavMono(samples, decoded.sampleRate)
    let binary = ''
    for (let i = 0; i < wav.length; i += 1) binary += String.fromCharCode(wav[i] ?? 0)
    clips.push(btoa(binary))
  }
  await context.close()
  return clips
}
