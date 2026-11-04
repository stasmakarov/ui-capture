import { describe, expect, it } from 'vitest'
import { encodeWavMono, sliceChannel } from './wav.ts'

describe('wav', () => {
  it('режет канал и пишет заголовок RIFF', () => {
    const rate = 16000
    const samples = new Float32Array(rate)
    for (let i = 0; i < samples.length; i += 1) samples[i] = Math.sin((i / rate) * Math.PI * 2 * 440)
    const clip = sliceChannel(samples, rate, 0.25, 0.5)
    expect(clip.length).toBe(4000)
    const wav = encodeWavMono(clip, rate)
    expect(String.fromCharCode(...wav.slice(0, 4))).toBe('RIFF')
    expect(String.fromCharCode(...wav.slice(8, 12))).toBe('WAVE')
    expect(wav.length).toBe(44 + clip.length * 2)
  })
})
