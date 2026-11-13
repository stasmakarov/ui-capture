import { describe, expect, it } from 'vitest'
import type { CaptureAction, CaptureScreen } from './types.ts'
import { shouldRecaptureAfterUndo, undoLastAction } from './undo.ts'

function screen(id: string): CaptureScreen {
  return {
    id,
    title: id,
    url: 'https://example.test',
    pageTitle: id,
    viewport: { width: 1280, height: 800 },
    screenshotFile: `screens/${id}.original.png`,
    rrwebFile: `screens/${id}.rrweb.json`,
    frozenHtmlFile: `screens/${id}.freeze.html`,
    elements: [],
  }
}

function action(partial: Partial<CaptureAction> & Pick<CaptureAction, 'screenId'>): CaptureAction {
  return {
    kind: 'click',
    confirm: null,
    value: '',
    hotspotId: 'h',
    selector: '#x',
    label: 'Кнопка',
    box: { x: 0, y: 0, width: 10, height: 10 },
    nextScreenId: null,
    prelude: false,
    ...partial,
  }
}

describe('undoLastAction', () => {
  it('ничего не делает на пустом списке', () => {
    const result = undoLastAction([], [screen('s1')])
    expect(result.undone).toBe(false)
    expect(result.removedScreen).toBeNull()
    expect(result.screens).toHaveLength(1)
  })

  it('убирает действие без нового экрана', () => {
    const screens = [screen('s1')]
    const actions = [
      action({ screenId: 's1', kind: 'fill', value: 'a' }),
      action({ screenId: 's1', kind: 'fill', value: 'b' }),
    ]
    const result = undoLastAction(actions, screens)
    expect(result.undone).toBe(true)
    expect(result.actions).toHaveLength(1)
    expect(result.actions[0]?.value).toBe('a')
    expect(result.removedScreen).toBeNull()
    expect(result.screens).toHaveLength(1)
  })

  it('удаляет экран, который открыло последнее действие', () => {
    const screens = [screen('s1'), screen('s2')]
    const actions = [
      action({ screenId: 's1', nextScreenId: 's2' }),
    ]
    const result = undoLastAction(actions, screens)
    expect(result.undone).toBe(true)
    expect(result.actions).toHaveLength(0)
    expect(result.removedScreen?.id).toBe('s2')
    expect(result.screens.map((item) => item.id)).toEqual(['s1'])
  })

  it('просит новый снимок, если живая страница не совпала с отпечатком предыдущего экрана', () => {
    expect(shouldRecaptureAfterUndo('s1-print', 's2-still-open')).toBe(true)
    expect(shouldRecaptureAfterUndo('s1-print', 's1-print')).toBe(false)
    expect(shouldRecaptureAfterUndo(undefined, 'live')).toBe(true)
  })
})
