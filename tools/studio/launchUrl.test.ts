import { describe, expect, it } from 'vitest'
import { parseLaunchUrl } from './launchUrl.ts'

describe('uicapture://', () => {
  it('разбирает одноразовое задание из админки', () => {
    const task = parseLaunchUrl('uicapture://new?course=eis-1&server=https%3A%2F%2Flearn.example&ticket=abc')
    expect(task).toEqual({ course: 'eis-1', server: 'https://learn.example', ticket: 'abc' })
  })

  it('отклоняет ссылку без ticket', () => {
    expect(() => parseLaunchUrl('uicapture://new?course=eis-1&server=https://learn.example')).toThrow(/ticket/)
  })
})
