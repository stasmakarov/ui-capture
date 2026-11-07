import { describe, expect, it } from 'vitest'
import { parseLaunchUrl } from './launchUrl.ts'

describe('uitrainer://', () => {
  it('разбирает одноразовое задание из админки', () => {
    const task = parseLaunchUrl('uitrainer://new?course=eis-1&server=https%3A%2F%2Flearn.example&ticket=abc')
    expect(task).toEqual({ course: 'eis-1', server: 'https://learn.example', ticket: 'abc' })
  })

  it('отклоняет схему uicapture://', () => {
    expect(() => parseLaunchUrl('uicapture://new?course=eis-1&server=https%3A%2F%2Flearn.example&ticket=abc')).toThrow(/uitrainer/)
  })

  it('отклоняет ссылку без ticket', () => {
    expect(() => parseLaunchUrl('uitrainer://new?course=eis-1&server=https://learn.example')).toThrow(/ticket/)
  })
})
