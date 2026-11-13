import { useEffect, useState } from 'react'
import type { Lesson, LessonStep } from '../../shared/lesson.ts'
import { selectOptionValues, syncInstructionIfDefault } from '../../shared/lesson.ts'
import { ConfirmDialog } from './ConfirmDialog.tsx'
import { IconAudio, IconDown, IconHint, IconUp } from './icons.tsx'

type ConfirmKind = 'step' | 'audio' | null

export function StepEditor({
  lesson,
  dirty,
  onChange,
  onSave,
  onBack,
  onTranscribe,
}: {
  lesson: Lesson
  dirty: boolean
  onChange: (lesson: Lesson) => void
  onSave: () => void
  onBack: () => void
  onTranscribe: (stepId: string) => void
}) {
  const [selected, setSelected] = useState(0)
  const [confirm, setConfirm] = useState<ConfirmKind>(null)
  const [dragOver, setDragOver] = useState<number | null>(null)
  const [expectedDraft, setExpectedDraft] = useState('')
  const [expectedError, setExpectedError] = useState('')
  const [instructionLocked, setInstructionLocked] = useState(false)
  const [selectHtml, setSelectHtml] = useState('')
  const index = Math.min(selected, Math.max(0, lesson.steps.length - 1))
  const step = lesson.steps[index]

  useEffect(() => {
    if (!dirty) return
    const onLeave = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', onLeave)
    return () => window.removeEventListener('beforeunload', onLeave)
  }, [dirty])

  function commit(steps: LessonStep[]) {
    onChange({ ...lesson, steps: steps.map((item, stepIndex) => ({ ...item, index: stepIndex })) })
  }

  function update(patch: Partial<LessonStep>) {
    if (!step) return
    commit(lesson.steps.map((item, stepIndex) => stepIndex === index ? { ...item, ...patch } : item))
  }

  function move(from: number, to: number) {
    if (to < 0 || to >= lesson.steps.length || from === to) return
    const steps = [...lesson.steps]
    const [item] = steps.splice(from, 1)
    if (!item) return
    steps.splice(to, 0, item)
    commit(steps)
    setSelected(to)
  }

  function removeStep() {
    const steps = lesson.steps.filter((_, stepIndex) => stepIndex !== index)
    commit(steps)
    setSelected(Math.max(0, index - 1))
    setConfirm(null)
  }

  const screen = step ? lesson.screens.find((item) => item.id === step.screenId) : undefined
  const hotspot = screen?.hotspots.find((item) => item.id === step?.hotspotId)
  const viewport = screen?.viewport ?? lesson.viewport
  const showExpected = step?.action === 'input' || step?.action === 'select'
  const selectOptions = step?.action === 'select' && hotspot ? selectOptionValues(selectHtml, hotspot.selector) : []
  const unknownSelect = step?.action === 'select' && expectedDraft.trim() !== '' && selectOptions.length > 0
    && !selectOptions.includes(expectedDraft.trim())

  useEffect(() => {
    if (!step) return
    setExpectedDraft(step.expectedValue)
    setExpectedError('')
    setInstructionLocked(false)
  }, [step?.id, step?.expectedValue])

  useEffect(() => {
    if (!step || step.action !== 'select' || !screen?.html) {
      setSelectHtml('')
      return
    }
    let cancelled = false
    void fetch(`/lessons/${lesson.id}/${screen.html}`).then(async (response) => {
      const html = await response.text()
      if (!cancelled) setSelectHtml(html)
    }).catch(() => {
      if (!cancelled) setSelectHtml('')
    })
    return () => { cancelled = true }
  }, [lesson.id, step?.id, step?.action, screen?.html])

  function applyExpected(next: string) {
    if (!step) return
    setExpectedDraft(next)
    if (!next.trim()) {
      setExpectedError('Укажите ожидаемый ввод')
      return
    }
    setExpectedError('')
    const synced = syncInstructionIfDefault(lesson, step, next.trim())
    setInstructionLocked(synced.instructionLocked)
    update({ expectedValue: next.trim(), instruction: synced.instruction })
  }

  return (
    <section className="editor-screen">
      <div className="toolbar">
        <h1>{lesson.title}</h1>
        {dirty && <p className="dirty">Есть несохранённые изменения</p>}
        <button type="button" className="primary" onClick={onSave}>Сохранить</button>
        <button type="button" className="tertiary" onClick={onBack}>К просмотру</button>
      </div>
      {lesson.steps.length === 0 && <p className="hint">В уроке нет шагов.</p>}
      {step && (
        <div className="editor">
          <ol className="step-list">
            {lesson.steps.map((item, stepIndex) => {
              const thumb = lesson.screens.find((screenItem) => screenItem.id === item.screenId)
              return (
                <li
                  key={item.id}
                  className={stepIndex === index ? 'selected' : dragOver === stepIndex ? 'drag-over' : undefined}
                  draggable
                  onDragStart={(event) => {
                    event.dataTransfer.setData('text/plain', String(stepIndex))
                    event.dataTransfer.effectAllowed = 'move'
                  }}
                  onDragOver={(event) => {
                    event.preventDefault()
                    setDragOver(stepIndex)
                  }}
                  onDragLeave={() => setDragOver((current) => current === stepIndex ? null : current)}
                  onDrop={(event) => {
                    event.preventDefault()
                    setDragOver(null)
                    move(Number(event.dataTransfer.getData('text/plain')), stepIndex)
                  }}
                >
                  <button type="button" className="step-select" onClick={() => setSelected(stepIndex)}>
                    <span className="step-num">{stepIndex + 1}</span>
                    {thumb?.screenshot ? (
                      <img className="thumb" alt="" src={`/lessons/${lesson.id}/${thumb.screenshot}`} />
                    ) : (
                      <span className="thumb empty" />
                    )}
                    <span className="step-text">{item.instruction || 'Без инструкции'}</span>
                    <span className="badges">
                      <span className={item.audio ? 'on' : ''} title={item.audio ? 'Есть аудио' : 'Нет аудио'}>
                        <IconAudio />
                      </span>
                      {!item.hint.trim() && (
                        <span className="warn" title="Нет подсказки">
                          <IconHint />
                        </span>
                      )}
                    </span>
                  </button>
                  <div className="step-move">
                    <button
                      type="button"
                      className="icon"
                      title="Переместить шаг выше"
                      aria-label="Переместить шаг выше"
                      disabled={stepIndex === 0}
                      onClick={() => move(stepIndex, stepIndex - 1)}
                    >
                      <IconUp />
                    </button>
                    <button
                      type="button"
                      className="icon"
                      title="Переместить шаг ниже"
                      aria-label="Переместить шаг ниже"
                      disabled={stepIndex === lesson.steps.length - 1}
                      onClick={() => move(stepIndex, stepIndex + 1)}
                    >
                      <IconDown />
                    </button>
                  </div>
                </li>
              )
            })}
          </ol>
          <div className="panel detail">
            <div className="screen-preview">
              {screen?.screenshot ? (
                <img alt={screen.title} src={`/lessons/${lesson.id}/${screen.screenshot}`} />
              ) : (
                <p className="hint">Нет снимка экрана</p>
              )}
              {hotspot && (
                <span
                  className="hotspot-mark"
                  title={hotspot.label}
                  style={{
                    left: `${(hotspot.box.x / viewport.width) * 100}%`,
                    top: `${(hotspot.box.y / viewport.height) * 100}%`,
                    width: `${(hotspot.box.width / viewport.width) * 100}%`,
                    height: `${(hotspot.box.height / viewport.height) * 100}%`,
                  }}
                />
              )}
            </div>
            {showExpected && (
              <label className="field">
                <span>Ожидаемый ввод</span>
                <input
                  value={expectedDraft}
                  onChange={(event) => applyExpected(event.target.value)}
                />
                {expectedError && <p className="field-error">{expectedError}</p>}
                {instructionLocked && (
                  <p className="hint">Инструкция изменена вручную — обновите текст сами</p>
                )}
                {unknownSelect && (
                  <p className="hint">Значение не совпадает с опциями списка</p>
                )}
                <p className="hint">На следующих экранах урока видно значение, введённое при записи.</p>
              </label>
            )}
            <label className="field">
              <span>Инструкция</span>
              <textarea value={step.instruction} onChange={(event) => update({ instruction: event.target.value })} />
            </label>
            <label className="field">
              <span>Подсказка</span>
              <textarea value={step.hint} onChange={(event) => update({ hint: event.target.value })} />
            </label>
            <label className="field">
              <span>Сообщение об ошибке</span>
              <textarea value={step.errorText} onChange={(event) => update({ errorText: event.target.value })} />
            </label>
            <section className="audio-block">
              <h2>Комментарий (аудио)</h2>
              {step.audio ? (
                <>
                  <audio controls preload="metadata" src={step.audio.startsWith('data:') ? step.audio : `/lessons/${lesson.id}/${step.audio}`} />
                  <div className="audio-actions">
                    <button type="button" className="tertiary" onClick={() => onTranscribe(step.id)}>Распознать ещё раз</button>
                    <button type="button" className="tertiary" onClick={() => setConfirm('audio')}>Удалить аудио</button>
                  </div>
                </>
              ) : (
                <p className="hint">К этому шагу не приложен комментарий.</p>
              )}
            </section>
            <div className="action-bar">
              <button type="button" className="tertiary danger-text" onClick={() => setConfirm('step')}>Удалить шаг</button>
            </div>
          </div>
        </div>
      )}
      {confirm === 'step' && (
        <ConfirmDialog
          title={`Удалить шаг ${index + 1}?`}
          message="Действие нельзя отменить."
          confirmLabel="Удалить"
          tone="danger"
          onConfirm={removeStep}
          onCancel={() => setConfirm(null)}
        />
      )}
      {confirm === 'audio' && (
        <ConfirmDialog
          title={`Удалить аудио шага ${index + 1}?`}
          message="Действие нельзя отменить."
          confirmLabel="Удалить"
          tone="danger"
          onConfirm={() => {
            update({ audio: undefined })
            setConfirm(null)
          }}
          onCancel={() => setConfirm(null)}
        />
      )}
    </section>
  )
}
