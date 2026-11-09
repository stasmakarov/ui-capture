import { describe, expect, it } from 'vitest'
import { isApiCompatible, STUDIO_API_VERSION } from './apiVersion.ts'

describe('isApiCompatible', () => {
  it('принимает то же major, что у студии', () => {
    expect(isApiCompatible(STUDIO_API_VERSION)).toBe(true)
    expect(isApiCompatible(1)).toBe(true)
  })

  it('отклоняет другое major', () => {
    expect(isApiCompatible(2)).toBe(false)
    expect(isApiCompatible(0)).toBe(false)
  })

  it('без версии платформы считает совместимым', () => {
    expect(isApiCompatible(undefined)).toBe(true)
    expect(isApiCompatible(null)).toBe(true)
  })
})
