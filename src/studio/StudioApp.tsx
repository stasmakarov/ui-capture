import { useEffect, useRef, useState } from 'react'
import type { Lesson } from '../../shared/lesson.ts'
import { sliceRecording, type StepMark } from './sliceAudio.ts'
import './studio.css'

type View = 'catalog' | 'new' | 'recording' | 'preview' | 'editor'
type CaptureMode = 'auto' | 'dom' | 'screenshot'
type CatalogItem = { id: string; title: string; description: string; screens: number; steps: number }
type LiveStep = { id: string; label: string; kind: string; value: string; at: number }
type Whisper = { ready: boolean; model: string; hint: string }

export function StudioApp() {
  const [view, setView] = useState<View>('catalog')
  const [catalog, setCatalog] = useState<CatalogItem[]>([])
  const [error, setError] = useState<string | null>(null)
  const [whisper, setWhisper] = useState<Whisper | null>(null)
  const [previewId, setPreviewId] = useState<string | null>(null)
  const [editor, setEditor] = useState<Lesson | null>(null)
  const [selected, setSelected] = useState<string[]>([])

  async function reload() {
    const response = await fetch('/api/lessons')
    setCatalog(await response.json() as CatalogItem[])
  }

  useEffect(() => {
    void reload()
    void fetch('/api/whisper').then(async (response) => setWhisper(await response.json() as Whisper))
  }, [])

  return (
    <div className="app">
      <header className="banner">
        <div className="banner-mark" aria-hidden="true" />
        <div>
          <strong>Учебный тренажёр</strong>
          <span>Студия записи</span>
        </div>
      </header>
      <main className="studio">
        {error && <p className="error" role="alert">{error}</p>}
        {view === 'catalog' && (
          <Catalog
            items={catalog}
            selected={selected}
            whisper={whisper}
            onSelect={setSelected}
            onNew={() => { setError(null); setView('new') }}
            onOpen={(id) => { setPreviewId(id); setView('preview') }}
            onEdit={async (id) => {
              const response = await fetch(`/api/lessons/${id}`)
              setEditor(await response.json() as Lesson)
              setView('editor')
            }}
            onDelete={async (id) => {
              await fetch(`/api/lessons/${id}`, { method: 'DELETE' })
              await reload()
            }}
            onDownload={async () => {
              setError(null)
              const response = await fetch('/api/export', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ids: selected.length ? selected : catalog.map((item) => item.id) }),
              })
              const payload = await response.json() as { error?: string; file?: string }
              if (!response.ok) { setError(payload.error ?? 'Не удалось собрать файл'); return }
              window.location.href = `/lessons/${payload.file}`
            }}
          />
        )}
        {view === 'new' && (
          <NewLesson
            whisper={whisper}
            onCancel={() => setView('catalog')}
            onError={setError}
            onWhisper={setWhisper}
            onStarted={() => { setError(null); setView('recording') }}
          />
        )}
        {view === 'recording' && (
          <Recording
            onError={setError}
            onStopped={async (id, marks, blob) => {
              if (blob && marks.length) {
                const clips = await sliceRecording(blob, marks)
                const response = await fetch(`/api/lessons/${id}/audio`, {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/json' },
                  body: JSON.stringify({ clips: clips.map((wavBase64, index) => ({ stepId: `step-${index + 1}`, wavBase64 })) }),
                })
                const payload = await response.json() as { warnings?: string[] }
                if (payload.warnings?.length) setError(payload.warnings[0] ?? null)
              }
              setPreviewId(id)
              setView('preview')
              await reload()
            }}
          />
        )}
        {view === 'preview' && previewId && (
          <section>
            <div className="row">
              <h1>Просмотр урока</h1>
              <button type="button" onClick={() => setView('catalog')}>К каталогу</button>
              <button type="button" onClick={async () => {
                const response = await fetch(`/api/lessons/${previewId}`)
                setEditor(await response.json() as Lesson)
                setView('editor')
              }}>Править шаги</button>
            </div>
            <iframe className="preview-frame" title="Просмотр" src={`/?play=${previewId}`} />
          </section>
        )}
        {view === 'editor' && editor && (
          <Editor
            lesson={editor}
            onChange={setEditor}
            onCancel={() => { setPreviewId(editor.id); setView('preview') }}
            onSave={async () => {
              const response = await fetch(`/api/lessons/${editor.id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(editor),
              })
              if (!response.ok) {
                const payload = await response.json() as { error?: string }
                setError(payload.error ?? 'Не удалось сохранить')
                return
              }
              setEditor(await response.json() as Lesson)
              await reload()
            }}
            onTranscribe={async (stepId) => {
              const response = await fetch(`/api/lessons/${editor.id}/steps/${encodeURIComponent(stepId)}/transcribe`, { method: 'POST' })
              const payload = await response.json() as Lesson & { error?: string }
              if (!response.ok) { setError(payload.error ?? 'Не удалось распознать'); return }
              setEditor(payload)
            }}
          />
        )}
      </main>
    </div>
  )
}

function Catalog({ items, selected, whisper, onSelect, onNew, onOpen, onEdit, onDelete, onDownload }: {
  items: CatalogItem[]
  selected: string[]
  whisper: Whisper | null
  onSelect: (ids: string[]) => void
  onNew: () => void
  onOpen: (id: string) => void
  onEdit: (id: string) => void
  onDelete: (id: string) => void
  onDownload: () => void
}) {
  return (
    <section>
      <div className="row">
        <h1>Уроки</h1>
        <button type="button" onClick={onNew}>Новый урок</button>
        <button type="button" className="ghost" onClick={onDownload}>Скачать trainer.html</button>
      </div>
      {whisper && <p className="whisper">{whisper.hint}</p>}
      {items.length === 0 && <p className="muted">Записей пока нет. Создайте первый урок.</p>}
      <div className="cards">
        {items.map((item) => (
          <article key={item.id} className="card">
            <label className="row">
              <input
                type="checkbox"
                checked={selected.includes(item.id)}
                onChange={(event) => onSelect(event.target.checked ? [...selected, item.id] : selected.filter((id) => id !== item.id))}
              />
              В экспорт
            </label>
            <h2>{item.title}</h2>
            <p>{item.description}</p>
            <p className="meta">{item.screens} экр. · {item.steps} шаг.</p>
            <div className="row">
              <button type="button" onClick={() => onOpen(item.id)}>Открыть</button>
              <button type="button" className="ghost" onClick={() => onEdit(item.id)}>Править</button>
              <button type="button" className="ghost" onClick={() => onDelete(item.id)}>Удалить</button>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

function NewLesson({ whisper, onCancel, onError, onWhisper, onStarted }: {
  whisper: Whisper | null
  onCancel: () => void
  onError: (message: string) => void
  onWhisper: (value: Whisper) => void
  onStarted: () => void
}) {
  const [url, setUrl] = useState('https://zakupki.gov.ru/epz/main/public/home.html')
  const [lessonName, setLessonName] = useState('Найти извещение')
  const [mode, setMode] = useState<CaptureMode>('auto')
  const [channel, setChannel] = useState('')
  const [executablePath, setExecutablePath] = useState('')
  const [mics, setMics] = useState<MediaDeviceInfo[]>([])
  const [micId, setMicId] = useState('')
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    void navigator.mediaDevices?.enumerateDevices().then((devices) => {
      const audio = devices.filter((device) => device.kind === 'audioinput')
      setMics(audio)
      if (audio[0]) setMicId(audio[0].deviceId)
    }).catch(() => undefined)
    void fetch('/api/settings').then(async (response) => {
      const settings = await response.json() as { channel: string; executablePath: string }
      setChannel(settings.channel)
      setExecutablePath(settings.executablePath)
    })
  }, [])

  return (
    <section>
      <h1>Новый урок</h1>
      <form className="form" onSubmit={async (event) => {
        event.preventDefault()
        setBusy(true)
        onError('')
        const response = await fetch('/api/record/start', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ url, lessonName, mode, channel, executablePath, micId }),
        })
        const payload = await response.json() as { error?: string }
        setBusy(false)
        if (!response.ok) { onError(payload.error ?? 'Не удалось начать запись'); return }
        sessionStorage.setItem('ui-capture-mic', micId)
        onStarted()
      }}>
        <label>Стартовая страница
          <input name="url" value={url} onChange={(event) => setUrl(event.target.value)} required />
        </label>
        <label>Название урока
          <input name="lessonName" value={lessonName} onChange={(event) => setLessonName(event.target.value)} required />
        </label>
        <label>Режим захвата
          <select name="mode" value={mode} onChange={(event) => setMode(event.target.value as CaptureMode)}>
            <option value="auto">Авто</option>
            <option value="dom">Разметка DOM</option>
            <option value="screenshot">Только скриншоты</option>
          </select>
        </label>
        <label>Микрофон
          <select name="mic" value={micId} onChange={(event) => setMicId(event.target.value)}>
            {mics.length === 0 && <option value="">Микрофон не найден</option>}
            {mics.map((mic) => <option key={mic.deviceId} value={mic.deviceId}>{mic.label || 'Микрофон'}</option>)}
          </select>
        </label>
        <label>Канал браузера (--channel), для Chrome укажите chrome
          <input name="channel" value={channel} onChange={(event) => setChannel(event.target.value)} placeholder="пусто или chrome" />
        </label>
        <label>Путь к браузеру с ГОСТ TLS (--executable-path)
          <input name="executablePath" value={executablePath} onChange={(event) => setExecutablePath(event.target.value)} placeholder="/Applications/Yandex.app/Contents/MacOS/Yandex" />
        </label>
        {whisper && <p className="whisper">{whisper.hint}</p>}
        {whisper && !whisper.ready && (
          <button type="button" className="ghost" onClick={async () => {
            const response = await fetch('/api/whisper/download', { method: 'POST' })
            const payload = await response.json() as Whisper & { error?: string }
            if (!response.ok) { onError(payload.error ?? 'Не удалось скачать модель'); return }
            onWhisper({ ready: true, model: whisper.model, hint: payload.hint })
          }}>Скачать модель речи</button>
        )}
        <div className="row">
          <button type="submit" disabled={busy}>{busy ? 'Открываю браузер…' : 'Начать запись'}</button>
          <button type="button" className="ghost" onClick={onCancel}>Отмена</button>
        </div>
      </form>
    </section>
  )
}

function Recording({ onError, onStopped }: {
  onError: (message: string) => void
  onStopped: (id: string, marks: StepMark[], blob: Blob | null) => Promise<void>
}) {
  const [steps, setSteps] = useState<LiveStep[]>([])
  const [paused, setPaused] = useState(false)
  const [seconds, setSeconds] = useState(0)
  const [level, setLevel] = useState(0)
  const [micOn, setMicOn] = useState(false)
  const marks = useRef<StepMark[]>([])
  const started = useRef(performance.now())
  const recorder = useRef<MediaRecorder | null>(null)
  const chunks = useRef<Blob[]>([])

  useEffect(() => {
    const timer = window.setInterval(() => setSeconds(Math.floor((performance.now() - started.current) / 1000)), 500)
    const source = new EventSource('/api/record/events')
    source.onmessage = (event) => {
      const payload = JSON.parse(event.data) as { type: string; step?: LiveStep; steps?: LiveStep[] }
      if (payload.type === 'ready' && payload.steps) {
        setSteps(payload.steps)
        marks.current = payload.steps.map(() => ({ t: (performance.now() - started.current) / 1000 }))
      }
      if (payload.type === 'step' && payload.step) {
        marks.current.push({ t: (performance.now() - started.current) / 1000 })
        setSteps((current) => [...current, payload.step!])
      }
      if (payload.type === 'paused') setPaused(true)
      if (payload.type === 'resumed') setPaused(false)
    }
    const micId = sessionStorage.getItem('ui-capture-mic') ?? ''
    void navigator.mediaDevices.getUserMedia({ audio: micId ? { deviceId: { exact: micId } } : true }).then((stream) => {
      setMicOn(true)
      const context = new AudioContext()
      const node = context.createMediaStreamSource(stream)
      const analyser = context.createAnalyser()
      node.connect(analyser)
      const data = new Uint8Array(analyser.fftSize)
      let frame = 0
      const tick = () => {
        analyser.getByteTimeDomainData(data)
        let sum = 0
        for (const value of data) sum += Math.abs(value - 128)
        setLevel(Math.min(100, sum / data.length))
        frame = requestAnimationFrame(tick)
      }
      tick()
      const media = new MediaRecorder(stream)
      chunks.current = []
      media.ondataavailable = (event) => { if (event.data.size) chunks.current.push(event.data) }
      media.start()
      recorder.current = media
      return () => { cancelAnimationFrame(frame); void context.close() }
    }).catch(() => onError('Микрофон недоступен. Запись шагов продолжится без голоса.'))
    return () => { window.clearInterval(timer); source.close() }
  }, [onError])

  const clock = `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
  return (
    <section>
      <div className="row">
        <h1>Запись</h1>
        <p className="timer">{clock}</p>
        <p className={micOn ? 'mic on' : 'mic'}><i />{micOn ? 'Микрофон' : 'Без микрофона'}</p>
        <div className="level" aria-hidden="true"><span style={{ width: `${level}%` }} /></div>
      </div>
      <p>Сайт открыт в отдельном окне. Комментируйте действия вслух. После последнего шага подождите секунду.</p>
      <ol className="live-steps">
        {steps.length === 0 && <li>Шагов пока нет — действуйте в окне сайта.</li>}
        {steps.map((step, index) => (
          <li key={`${step.id}-${index}`}>{index + 1}. {step.kind === 'fill' ? `Ввод «${step.value}»` : 'Нажатие'} — {step.label}</li>
        ))}
      </ol>
      <div className="row">
        <button type="button" onClick={async () => {
          await fetch(paused ? '/api/record/resume' : '/api/record/pause', { method: 'POST' })
          if (paused) recorder.current?.resume()
          else recorder.current?.pause()
        }}>{paused ? 'Продолжить' : 'Пауза'}</button>
        <button type="button" onClick={async () => {
          const blob = await stopRecorder(recorder.current, chunks.current)
          const response = await fetch('/api/record/stop', { method: 'POST' })
          const payload = await response.json() as { id?: string; steps?: Array<{ id: string }>; error?: string }
          if (!response.ok || !payload.id) { onError(payload.error ?? 'Не удалось остановить запись'); return }
          await onStopped(payload.id, marks.current.slice(0, payload.steps?.length ?? marks.current.length), blob)
        }}>Стоп</button>
      </div>
    </section>
  )
}

function stopRecorder(media: MediaRecorder | null, chunks: Blob[]): Promise<Blob | null> {
  if (!media || media.state === 'inactive') return Promise.resolve(chunks.length ? new Blob(chunks, { type: media?.mimeType || 'audio/webm' }) : null)
  return new Promise((resolve) => {
    media.onstop = () => resolve(new Blob(chunks, { type: media.mimeType || 'audio/webm' }))
    media.stop()
  })
}

function Editor({ lesson, onChange, onCancel, onSave, onTranscribe }: {
  lesson: Lesson
  onChange: (lesson: Lesson) => void
  onCancel: () => void
  onSave: () => void
  onTranscribe: (stepId: string) => void
}) {
  function update(index: number, patch: Partial<Lesson['steps'][number]>) {
    const steps = lesson.steps.map((step, stepIndex) => stepIndex === index ? { ...step, ...patch } : step)
    onChange({ ...lesson, steps })
  }
  function move(index: number, direction: -1 | 1) {
    const next = index + direction
    if (next < 0 || next >= lesson.steps.length) return
    const steps = [...lesson.steps]
    const current = steps[index]
    const other = steps[next]
    if (!current || !other) return
    steps[index] = other
    steps[next] = current
    onChange({ ...lesson, steps: steps.map((step, stepIndex) => ({ ...step, index: stepIndex })) })
  }
  return (
    <section>
      <div className="row">
        <h1>Шаги: {lesson.title}</h1>
        <button type="button" onClick={onSave}>Сохранить</button>
        <button type="button" className="ghost" onClick={onCancel}>К просмотру</button>
      </div>
      <div className="live-steps">
        {lesson.steps.map((step, index) => (
          <article key={step.id} className="editor-step">
            <strong>Шаг {index + 1}</strong>
            <label>Инструкция
              <textarea value={step.instruction} onChange={(event) => update(index, { instruction: event.target.value })} />
            </label>
            <label>Подсказка
              <textarea value={step.hint} onChange={(event) => update(index, { hint: event.target.value })} />
            </label>
            <label>Сообщение об ошибке
              <textarea value={step.errorText} onChange={(event) => update(index, { errorText: event.target.value })} />
            </label>
            <div className="row">
              <button type="button" className="ghost" onClick={() => move(index, -1)}>Выше</button>
              <button type="button" className="ghost" onClick={() => move(index, 1)}>Ниже</button>
              <button type="button" className="ghost" onClick={() => onChange({ ...lesson, steps: lesson.steps.filter((_, stepIndex) => stepIndex !== index).map((item, stepIndex) => ({ ...item, index: stepIndex })) })}>Удалить шаг</button>
              {step.audio && <audio controls src={step.audio.startsWith('data:') ? step.audio : `/lessons/${lesson.id}/${step.audio}`} />}
              {step.audio && <button type="button" className="ghost" onClick={() => update(index, { audio: undefined })}>Удалить аудио</button>}
              {step.audio && <button type="button" className="ghost" onClick={() => onTranscribe(step.id)}>Распознать ещё раз</button>}
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}
