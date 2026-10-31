import type { Lesson } from '../shared/lesson.ts'

export type EmbeddedCatalogItem = {
  id: string
  title: string
  description: string
  sourceSite: string
  screens: number
  steps: number
  autoStepRatio: number
}

export type EmbeddedLesson = {
  lesson: Lesson
  documents: Record<string, string>
  screenshots: Record<string, string>
}

export type EmbeddedPack = {
  catalog: EmbeddedCatalogItem[]
  lessons: EmbeddedLesson[]
}

export const embeddedPack: EmbeddedPack | null = null
