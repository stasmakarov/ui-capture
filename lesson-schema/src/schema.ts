import { LESSON_SCHEMA_VERSION } from './lesson.ts'

const hotspotKinds = ['input', 'textarea', 'select', 'button', 'link', 'tab', 'clickable']
const stepActions = ['input', 'click', 'select', 'key']
const confirms = ['match', 'enter']

const box = {
  type: 'object',
  additionalProperties: false,
  required: ['x', 'y', 'width', 'height'],
  properties: {
    x: { type: 'number' },
    y: { type: 'number' },
    width: { type: 'number' },
    height: { type: 'number' },
  },
}

const hotspot = {
  type: 'object',
  additionalProperties: false,
  required: ['id', 'kind', 'label', 'selector', 'box'],
  properties: {
    id: { type: 'string' },
    kind: { type: 'string', enum: hotspotKinds },
    label: { type: 'string' },
    selector: { type: 'string' },
    box,
    inputType: { type: 'string' },
    placeholder: { type: 'string' },
  },
}

const screen = {
  type: 'object',
  additionalProperties: false,
  required: ['id', 'title', 'url', 'html', 'screenshot', 'hotspots', 'viewport'],
  properties: {
    id: { type: 'string' },
    title: { type: 'string' },
    url: { type: 'string' },
    html: { type: 'string', description: 'Относительный путь к HTML экрана внутри папки урока, обычно screens/<id>.html' },
    screenshot: { type: 'string', description: 'Относительный путь к снимку, обычно original/<id>.png' },
    hotspots: { type: 'array', items: hotspot },
    viewport: {
      type: 'object',
      additionalProperties: false,
      required: ['width', 'height'],
      properties: { width: { type: 'number' }, height: { type: 'number' } },
    },
  },
}

const step = {
  type: 'object',
  additionalProperties: false,
  required: ['id', 'index', 'screenId', 'action', 'hotspotId', 'expectedValue', 'confirm', 'nextScreenId', 'instruction', 'hint', 'errorText'],
  properties: {
    id: { type: 'string' },
    index: { type: 'integer' },
    screenId: { type: 'string' },
    action: { type: 'string', enum: stepActions },
    hotspotId: { type: 'string' },
    expectedValue: { type: 'string' },
    confirm: { anyOf: [{ type: 'string', enum: confirms }, { type: 'null' }] },
    nextScreenId: { anyOf: [{ type: 'string' }, { type: 'null' }] },
    instruction: { type: 'string' },
    hint: { type: 'string' },
    errorText: { type: 'string' },
    audio: { type: 'string', description: 'Относительный путь к WAV, обычно audio/<stepId>.wav, либо data:' },
    narration: { type: 'string' },
  },
}

export const lessonJsonSchema = {
  $schema: 'https://json-schema.org/draft/2020-12/schema',
  $id: 'https://cursor.com/codebase/stanislav-makarov/ui-capture/lesson.schema.json',
  title: 'Lesson',
  type: 'object',
  additionalProperties: false,
  required: ['schemaVersion', 'id', 'title', 'description', 'sourceSite', 'viewport', 'screens', 'steps', 'meta'],
  properties: {
    schemaVersion: { type: 'integer', const: LESSON_SCHEMA_VERSION },
    id: { type: 'string' },
    title: { type: 'string' },
    description: { type: 'string' },
    sourceSite: { type: 'string' },
    viewport: {
      type: 'object',
      additionalProperties: false,
      required: ['width', 'height'],
      properties: { width: { type: 'number' }, height: { type: 'number' } },
    },
    screens: { type: 'array', minItems: 1, items: screen },
    steps: { type: 'array', items: step },
    meta: {
      type: 'object',
      additionalProperties: false,
      required: ['generatedAt', 'generator', 'stepCount', 'manuallyEditedSteps', 'autoStepRatio', 'fidelity', 'warnings', 'defaultRenderMode'],
      properties: {
        generatedAt: { type: 'string' },
        generator: { type: 'string' },
        stepCount: { type: 'integer' },
        manuallyEditedSteps: { type: 'integer' },
        autoStepRatio: { type: 'number' },
        fidelity: {
          type: 'array',
          items: {
            type: 'object',
            additionalProperties: false,
            required: ['screenId', 'chosen', 'rrwebSimilarity', 'freezeSimilarity', 'chosenSimilarity'],
            properties: {
              screenId: { type: 'string' },
              chosen: { type: 'string', enum: ['rrweb', 'freeze'] },
              rrwebSimilarity: { anyOf: [{ type: 'number' }, { type: 'null' }] },
              freezeSimilarity: { anyOf: [{ type: 'number' }, { type: 'null' }] },
              chosenSimilarity: { type: 'number' },
            },
          },
        },
        warnings: { type: 'array', items: { type: 'string' } },
        defaultRenderMode: { type: 'string', enum: ['dom', 'screenshot'] },
      },
    },
  },
} as const

export function renderLessonSchema(): string {
  return `${JSON.stringify(lessonJsonSchema, null, 2)}\n`
}
