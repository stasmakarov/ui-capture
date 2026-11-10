import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import type { Lesson } from '../../shared/lesson.ts'
import { sliceRecording, type StepMark } from './sliceAudio.ts'
import { downloadLabel } from '../../tools/studio/downloadProgress.ts'
import { ConfirmDialog } from './ConfirmDialog.tsx'
import { StepEditor } from './StepEditor.tsx'
import './studio.css'

type View = 'catalog' | 'new' | 'recording' | 'preview' | 'editor'
type CaptureMode = 'auto' | 'dom' | 'screenshot'
type CatalogItem = { id: string; title: string; description: string; screens: number; steps: number }
type LiveStep = { id: string; label: string; kind: string; value: string; at: number }
type WhisperPhase = 'idle' | 'downloading' | 'ready' | 'error'
type Whisper = {
  ready: boolean
  model: string
  hint: string
  phase: WhisperPhase
  percent: number
  loadedBytes: number
  totalBytes: number
  file: string
  error: string
  modelBytes: number
}
type BrowserChoice = 'chromium' | 'chrome' | 'yandex' | 'custom'
type BrowserOption = { id: BrowserChoice; label: string; available: boolean }
type ChromiumStatus = { phase: 'idle' | 'installing' | 'ready' | 'error'; installed: boolean; error: string }
type BrowsersPayload = { options: BrowserOption[]; hint: string; chromium: ChromiumStatus }

const MIC_KEY = 'uitrainer-mic'
const MIC_READY = 'uitrainer-mic-ready'
const GOST_HINT = 'Если сайт не открывается из-за сертификата (российские сертификаты ГОСТ), выберите Яндекс Браузер.'

export function StudioApp() {
  const [view, setView] = useState<View>('catalog')
  const [bannerHost, setBannerHost] = useState<HTMLDivElement | null>(null)
  const [catalog, setCatalog] = useState<CatalogItem[]>([])
  const [error, setError] = useState<string | null>(null)
  const [whisper, setWhisper] = useState<Whisper | null>(null)
  const [previewId, setPreviewId] = useState<string | null>(null)
  const [editor, setEditor] = useState<Lesson | null>(null)
  const [baseline, setBaseline] = useState('')
  const [leaveOpen, setLeaveOpen] = useState(false)
  const [pendingDelete, setPendingDelete] = useState<CatalogItem | null>(null)
  const [selected, setSelected] = useState<string[]>([])
  const [platform, setPlatform] = useState<PlatformState>({ connected: false })
  const [notice, setNotice] = useState<string | null>(null)
  const [publishing, setPublishing] = useState<string | null>(null)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [aboutOpen, setAboutOpen] = useState(false)
  const [studioInfo, setStudioInfo] = useState<StudioInfo | null>(null)
  const [updateProgress, setUpdateProgress] = useState<{ version: string; percent: number } | null>(null)
  const [browsersInfo, setBrowsersInfo] = useState<BrowsersPayload | null>(null)
  const dirty = !!editor && JSON.stringify(editor) !== baseline
  const desktop = window.uitrainerDesktop

  async function reloadPlatform() {
    const response = await fetch('/api/platform')
    setPlatform(await response.json() as PlatformState)
  }

  async function reload() {
    const response = await fetch('/api/lessons')
    setCatalog(await response.json() as CatalogItem[])
  }

  useEffect(() => {
    void reload()
    void (async () => {
      // Ссылка из админки: ?server=…&ticket=…&course=… (схема uitrainer:// или ручной переход)
      const params = new URLSearchParams(window.location.search)
      const server = params.get('server')
      const ticket = params.get('ticket')
      if (server && ticket) {
        const response = await fetch('/api/platform/link', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ server, ticket, course: params.get('course') }),
        })
        const payload = await response.json() as { error?: string }
        if (!response.ok) setError(payload.error ?? 'Не удалось связать студию с платформой')
        window.history.replaceState(null, '', '/')
      }
      await reloadPlatform()
      const infoResponse = await fetch('/api/studio-info')
      if (infoResponse.ok) setStudioInfo(await infoResponse.json() as StudioInfo)
    })()
    const source = new EventSource('/api/whisper/events')
    source.onmessage = (event) => setWhisper(JSON.parse(event.data) as Whisper)
    void fetch('/api/browsers').then(async (response) => {
      setBrowsersInfo(await response.json() as BrowsersPayload)
    }).catch(() => undefined)
    const stopUpdates = desktop?.onUpdateProgress(setUpdateProgress)
    return () => {
      source.close()
      stopUpdates?.()
    }
  }, [desktop])

  return (
    <div className="app">
      <header className="banner">
        <div className="banner-mark" aria-hidden="true" />
        <div>
          <strong>Учебный тренажёр</strong>
          <span>Студия записи</span>
        </div>
        <button type="button" className="tertiary" onClick={() => setSettingsOpen(true)}>Настройки</button>
        <button type="button" className="tertiary" onClick={() => setAboutOpen(true)}>О программе</button>
        <div className="banner-end">
          {updateProgress && (
            <span className="banner-update">Скачиваю обновление {updateProgress.version}… {updateProgress.percent}%</span>
          )}
          {platform.connected && (
            <div className="banner-user" data-testid="studio-user">
              <span className="banner-avatar" aria-hidden="true">{initial(platform.displayName || platform.username)}</span>
              <span>{platform.displayName || platform.username}</span>
            </div>
          )}
          <div className="banner-actions" ref={setBannerHost} />
        </div>
      </header>
      <main className="studio">
        {error && <p className="error" role="alert">{error}</p>}
        {studioInfo?.platform.connected && studioInfo.platform.compatible === false && (
          <p className="error" role="alert">
            Платформа требует более новую студию, обновитесь
            {desktop && (
              <button type="button" className="tertiary" onClick={() => { void desktop.checkUpdates() }}>Проверить обновления</button>
            )}
          </p>
        )}
        {notice && <p className="installed" role="status" data-testid="platform-notice">{notice}</p>}
        <PlatformBar platform={platform} onForget={async () => {
          await fetch('/api/platform', { method: 'DELETE' })
          await reloadPlatform()
        }} />
        {view === 'catalog' && (
          <Catalog
            items={catalog}
            selected={selected}
            whisper={whisper}
            browsers={browsersInfo}
            onWhisper={setWhisper}
            onError={setError}
            onBrowsers={setBrowsersInfo}
            onSelect={setSelected}
            onNew={() => { setError(null); setView('new') }}
            onOpen={(id) => { setPreviewId(id); setView('preview') }}
            onEdit={async (id) => {
              const response = await fetch(`/api/lessons/${id}`)
              const lesson = await response.json() as Lesson
              setEditor(lesson)
              setBaseline(JSON.stringify(lesson))
              setView('editor')
            }}
            onDelete={(item) => setPendingDelete(item)}
            onPackage={(id) => { window.location.href = `/api/lessons/${id}/package` }}
            platformConnected={platform.connected}
            publishing={publishing}
            onPublish={async (id) => {
              setError(null)
              setNotice(null)
              setPublishing(id)
              try {
                const response = await fetch(`/api/lessons/${id}/publish`, { method: 'POST' })
                const payload = await response.json() as { error?: string; title?: string; versionNo?: number; adminUrl?: string }
                if (!response.ok) { setError(payload.error ?? 'Не удалось опубликовать'); return }
                const course = platform.connected && platform.courseTitle ? ` Курс: ${platform.courseTitle}.` : ''
                setNotice(`«${payload.title}» отправлен на платформу черновиком, версия ${payload.versionNo}.${course} ${payload.adminUrl ?? ''}`)
              } finally {
                setPublishing(null)
              }
            }}
            onImport={async (file) => {
              setError(null)
              const response = await fetch('/api/import', {
                method: 'POST',
                headers: { 'Content-Type': 'application/zip' },
                body: await file.arrayBuffer(),
              })
              const payload = await response.json() as { error?: string; title?: string }
              if (!response.ok) { setError(payload.error ?? 'Не удалось импортировать пакет'); return }
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
            courseTitle={platform.connected ? platform.courseTitle : null}
            onCancel={() => setView('catalog')}
            onError={setError}
            onWhisper={setWhisper}
            onStarted={() => { setError(null); setView('recording') }}
          />
        )}
        {view === 'recording' && (
          <Recording
            bannerHost={bannerHost}
            onError={setError}
            onStopped={async (id, marks, blob) => {
              if (blob && marks.length) {
                try {
                  const clips = await sliceRecording(blob, marks)
                  const response = await fetch(`/api/lessons/${id}/audio`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ clips: clips.map((wavBase64, index) => ({ stepId: `step-${index + 1}`, wavBase64 })) }),
                  })
                  const payload = await response.json() as { warnings?: string[] }
                  if (payload.warnings?.length) setError(payload.warnings[0] ?? null)
                } catch {
                  setError('Не удалось разобрать запись микрофона. Шаги сохранены без звука.')
                }
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
              <button type="button" className="tertiary" onClick={() => setView('catalog')}>К каталогу</button>
              <button type="button" className="primary" onClick={async () => {
                const response = await fetch(`/api/lessons/${previewId}`)
                const lesson = await response.json() as Lesson
                setEditor(lesson)
                setBaseline(JSON.stringify(lesson))
                setView('editor')
              }}>Править шаги</button>
            </div>
            <iframe className="preview-frame" title="Просмотр" src={`/?play=${previewId}`} />
          </section>
        )}
        {view === 'editor' && editor && (
          <StepEditor
            lesson={editor}
            dirty={dirty}
            onChange={setEditor}
            onBack={() => {
              if (dirty) setLeaveOpen(true)
              else { setPreviewId(editor.id); setView('preview') }
            }}
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
              const saved = await response.json() as Lesson
              setEditor(saved)
              setBaseline(JSON.stringify(saved))
              await reload()
            }}
            onTranscribe={async (stepId) => {
              const response = await fetch(`/api/lessons/${editor.id}/steps/${encodeURIComponent(stepId)}/transcribe`, { method: 'POST' })
              const payload = await response.json() as Lesson & { error?: string }
              if (!response.ok) { setError(payload.error ?? 'Не удалось распознать'); return }
              setEditor((current) => {
                if (!current) return payload
                return {
                  ...current,
                  steps: current.steps.map((step) => {
                    const next = payload.steps.find((item) => item.id === step.id)
                    return step.id === stepId && next ? { ...step, hint: next.hint, narration: next.narration } : step
                  }),
                }
              })
            }}
          />
        )}
        {pendingDelete && (
          <ConfirmDialog
            title={`Удалить урок «${pendingDelete.title}»?`}
            message="Действие нельзя отменить."
            confirmLabel="Удалить"
            tone="danger"
            onCancel={() => setPendingDelete(null)}
            onConfirm={async () => {
              await fetch(`/api/lessons/${pendingDelete.id}`, { method: 'DELETE' })
              setPendingDelete(null)
              await reload()
            }}
          />
        )}
        {leaveOpen && editor && (
          <ConfirmDialog
            title="Есть несохранённые изменения"
            message="Уйти без сохранения? Правки урока пропадут."
            confirmLabel="Уйти"
            tone="danger"
            onCancel={() => setLeaveOpen(false)}
            onConfirm={() => {
              setLeaveOpen(false)
              setPreviewId(editor.id)
              setView('preview')
            }}
          />
        )}
        {settingsOpen && (
          <SettingsDialog
            desktop={desktop}
            info={studioInfo}
            onClose={() => setSettingsOpen(false)}
          />
        )}
        {aboutOpen && (
          <AboutDialog
            info={studioInfo ?? { studioVersion: '0.6.0', studioApiVersion: 1, port: 44731, dataDir: '', platform: { connected: false } }}
            desktop={desktop}
            onClose={() => setAboutOpen(false)}
          />
        )}
      </main>
    </div>
  )
}

function Catalog({ items, selected, whisper, browsers, onWhisper, onError, onBrowsers, onSelect, onNew, onOpen, onEdit, onDelete, onDownload, onPackage, onImport, platformConnected, publishing, onPublish }: {
  items: CatalogItem[]
  selected: string[]
  whisper: Whisper | null
  browsers: BrowsersPayload | null
  onWhisper: (value: Whisper) => void
  onError: (message: string) => void
  onBrowsers: (value: BrowsersPayload) => void
  onSelect: (ids: string[]) => void
  onNew: () => void
  onOpen: (id: string) => void
  onEdit: (id: string) => void
  onDelete: (item: CatalogItem) => void
  onDownload: () => void
  onPackage: (id: string) => void
  onImport: (file: File) => void
  platformConnected: boolean
  publishing: string | null
  onPublish: (id: string) => void
}) {
  const noBrowser = browsers != null && !browsers.chromium.installed && !browsers.options.some((item) => (item.id === 'chrome' || item.id === 'yandex') && item.available)
  return (
    <section>
      <div className="toolbar">
        <h1>Уроки</h1>
        <button type="button" className="primary" onClick={onNew}>Новый урок</button>
        <button type="button" className="tertiary" onClick={onDownload}>Скачать trainer.html</button>
        <label className="tertiary file-button">
          Импорт .lesson.zip
          <input
            type="file"
            accept=".zip,application/zip"
            onChange={(event) => {
              const file = event.target.files?.[0]
              event.target.value = ''
              if (file) onImport(file)
            }}
          />
        </label>
      </div>
      {whisper?.ready && <p className="installed">Распознавание речи установлено</p>}
      {whisper && !whisper.ready && (
        <div className="whisper">
          <p>{whisper.hint}</p>
          <SpeechInstallButton whisper={whisper} onWhisper={onWhisper} onError={onError} />
        </div>
      )}
      {noBrowser && (
        <div className="whisper">
          <p>Не найден ни Chromium, ни поддерживаемый системный браузер.</p>
          <ChromiumInstall browsers={browsers} onBrowsers={onBrowsers} />
        </div>
      )}
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
              <button type="button" className="tertiary" onClick={() => onOpen(item.id)}>Открыть</button>
              <button type="button" className="tertiary" onClick={() => onPackage(item.id)}>Пакет</button>
              {platformConnected && (
                <button type="button" className="primary" data-testid={`publish-${item.id}`} disabled={publishing !== null} onClick={() => onPublish(item.id)}>
                  {publishing === item.id ? 'Отправляю…' : 'Опубликовать на сервер'}
                </button>
              )}
              <button type="button" className="tertiary" onClick={() => onEdit(item.id)}>Править</button>
              <button type="button" className="tertiary danger-text" onClick={() => onDelete(item)}>Удалить</button>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

function NewLesson({ whisper, courseTitle, onCancel, onError, onWhisper, onStarted }: {
  whisper: Whisper | null
  courseTitle: string | null
  onCancel: () => void
  onError: (message: string) => void
  onWhisper: (value: Whisper) => void
  onStarted: () => void
}) {
  const [url, setUrl] = useState('')
  const [lessonName, setLessonName] = useState('')
  const [mode, setMode] = useState<CaptureMode>('auto')
  const [browsers, setBrowsers] = useState<BrowserOption[]>([])
  const [chromium, setChromium] = useState<ChromiumStatus | null>(null)
  const [browser, setBrowser] = useState<BrowserChoice>('chromium')
  const [customPath, setCustomPath] = useState('')
  const [browserHint, setBrowserHint] = useState(GOST_HINT)
  const [mics, setMics] = useState<MediaDeviceInfo[]>([])
  const [micId, setMicId] = useState('')
  const [micGranted, setMicGranted] = useState(false)
  const [level, setLevel] = useState(0)
  const [busy, setBusy] = useState(false)

  async function refreshMics(prefer?: string) {
    const devices = await navigator.mediaDevices.enumerateDevices()
    const audio = devices.filter((device) => device.kind === 'audioinput')
    setMics(audio)
    const saved = prefer ?? localStorage.getItem(MIC_KEY) ?? ''
    const next = audio.find((device) => device.deviceId === saved)?.deviceId ?? audio[0]?.deviceId ?? ''
    setMicId(next)
    if (next) localStorage.setItem(MIC_KEY, next)
    return audio
  }

  useEffect(() => {
    void refreshMics().then((audio) => {
      const remembered = localStorage.getItem(MIC_READY) === '1'
      if (remembered && audio.some((device) => device.label)) setMicGranted(true)
    }).catch(() => undefined)
    const onChange = () => { void refreshMics().catch(() => undefined) }
    navigator.mediaDevices?.addEventListener('devicechange', onChange)
    void fetch('/api/browsers').then(async (response) => {
      const payload = await response.json() as BrowsersPayload
      setBrowsers(payload.options)
      setChromium(payload.chromium)
      if (payload.hint) setBrowserHint(payload.hint)
    }).catch(() => undefined)
    void fetch('/api/settings').then(async (response) => {
      const settings = await response.json() as { browser: BrowserChoice; executablePath: string }
      setBrowser(settings.browser)
      setCustomPath(settings.executablePath)
    }).catch(() => undefined)
    return () => navigator.mediaDevices?.removeEventListener('devicechange', onChange)
  }, [])

  useEffect(() => {
    if (!micGranted || !micId) return
    let cancelled = false
    let frame = 0
    let context: AudioContext | null = null
    let stream: MediaStream | null = null
    void navigator.mediaDevices.getUserMedia({ audio: { deviceId: { exact: micId } } }).then(async (next) => {
      if (cancelled) {
        next.getTracks().forEach((track) => track.stop())
        return
      }
      stream = next
      context = new AudioContext()
      await context.resume()
      if (cancelled) {
        next.getTracks().forEach((track) => track.stop())
        await context.close()
        return
      }
      const analyser = context.createAnalyser()
      context.createMediaStreamSource(next).connect(analyser)
      const data = new Uint8Array(analyser.fftSize)
      const tick = () => {
        analyser.getByteTimeDomainData(data)
        let sum = 0
        for (const value of data) sum += Math.abs(value - 128)
        setLevel(Math.min(100, sum / data.length))
        frame = requestAnimationFrame(tick)
      }
      tick()
    }).catch(() => onError('Не удалось открыть выбранный микрофон. Выберите другой или нажмите «Разрешить микрофон» ещё раз.'))
    return () => {
      cancelled = true
      cancelAnimationFrame(frame)
      stream?.getTracks().forEach((track) => track.stop())
      void context?.close()
    }
  }, [micGranted, micId, onError])

  async function allowMic() {
    onError('')
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      stream.getTracks().forEach((track) => track.stop())
      const audio = await refreshMics()
      localStorage.setItem(MIC_READY, '1')
      setMicGranted(true)
      if (!audio.some((device) => device.label)) onError('Браузер не сообщил названия микрофонов. Разрешите доступ и обновите список.')
    } catch {
      onError('Нет доступа к микрофону. Разрешите его в браузере и нажмите «Разрешить микрофон» ещё раз.')
    }
  }

  const missing: string[] = []
  if (!lessonName.trim()) missing.push('название урока')
  if (!url.trim()) missing.push('адрес стартовой страницы')
  return (
    <section>
      <h1>Новый урок</h1>
      {courseTitle && <p className="meta">Курс: {courseTitle}</p>}
      <form className="form lesson-form" onSubmit={async (event) => {
        event.preventDefault()
        setBusy(true)
        onError('')
        const response = await fetch('/api/record/start', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            url,
            lessonName,
            mode,
            browser,
            executablePath: browser === 'custom' ? customPath : '',
          }),
        })
        const payload = await response.json() as { error?: string }
        setBusy(false)
        if (!response.ok) { onError(payload.error ?? 'Не удалось начать запись'); return }
        if (micId) localStorage.setItem(MIC_KEY, micId)
        onStarted()
      }}>
        <label>Название урока
          <input name="lessonName" value={lessonName} onChange={(event) => setLessonName(event.target.value)} placeholder="Например: Поиск документа" />
        </label>
        <label>Стартовая страница
          <input name="url" value={url} onChange={(event) => setUrl(event.target.value)} placeholder="Например: https://…" />
        </label>
        <label>Режим захвата
          <select name="mode" value={mode} onChange={(event) => setMode(event.target.value as CaptureMode)}>
            <option value="auto">Авто</option>
            <option value="dom">Разметка DOM</option>
            <option value="screenshot">Только скриншоты</option>
          </select>
        </label>
        <label>Браузер для записи
          <select name="browser" value={browser} onChange={(event) => setBrowser(event.target.value as BrowserChoice)}>
            {browsers.map((item) => (
              <option key={item.id} value={item.id} disabled={!item.available}>
                {item.label}{item.available ? '' : ' (не найден)'}
              </option>
            ))}
          </select>
        </label>
        {browser === 'custom' && (
          <label className="wide">Путь к браузеру
            <input name="executablePath" value={customPath} onChange={(event) => setCustomPath(event.target.value)} placeholder="/Applications/Yandex.app/Contents/MacOS/Yandex" />
          </label>
        )}
        <p className="hint wide">{browserHint}</p>
        <div className="wide">
          {chromium && (
            <ChromiumInstall
              browsers={{ options: browsers, hint: browserHint, chromium }}
              onBrowsers={(payload) => {
                setBrowsers(payload.options)
                setChromium(payload.chromium)
              }}
            />
          )}
        </div>
        {!micGranted && (
          <div className="field wide">
            <span>Микрофон</span>
            <button type="button" className="primary" onClick={() => void allowMic()}>Разрешить микрофон</button>
          </div>
        )}
        {micGranted && (
          <label className="wide">Микрофон
            <select name="mic" value={micId} onChange={(event) => {
              setMicId(event.target.value)
              localStorage.setItem(MIC_KEY, event.target.value)
            }}>
              {mics.length === 0 && <option value="">Микрофон не найден</option>}
              {mics.map((mic) => <option key={mic.deviceId} value={mic.deviceId}>{mic.label || 'Микрофон без названия'}</option>)}
            </select>
          </label>
        )}
        {micGranted && (
          <div className="wide">
            <div
              className="level"
              role="meter"
              aria-label="Уровень сигнала"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={Math.round(level)}
              data-level={level}
            >
              <span style={{ width: `${Math.min(100, level)}%` }} />
            </div>
            <p className="hint">Уровень выбранного микрофона. Говорите — полоска должна двигаться до начала записи.</p>
          </div>
        )}
        {whisper?.ready && <p className="installed wide">Распознавание речи установлено</p>}
        {whisper && !whisper.ready && (
          <div className="whisper wide">
            <p>{whisper.hint}</p>
            <SpeechInstallButton whisper={whisper} onWhisper={onWhisper} onError={onError} />
          </div>
        )}
        {whisper?.error && <p className="error wide" role="alert">{whisper.error}</p>}
        <div className="action-bar">
          <button type="submit" className="primary" disabled={busy || missing.length > 0}>{busy ? 'Открываю браузер…' : 'Начать запись'}</button>
          <button type="button" className="tertiary" onClick={onCancel}>Отмена</button>
          {missing.length > 0 && <p className="hint">Заполните: {missing.join(', ')}</p>}
        </div>
      </form>
    </section>
  )
}

function Recording({ bannerHost, onError, onStopped }: {
  bannerHost: HTMLElement | null
  onError: (message: string) => void
  onStopped: (id: string, marks: StepMark[], blob: Blob | null) => Promise<void>
}) {
  const [steps, setSteps] = useState<LiveStep[]>([])
  const [paused, setPaused] = useState(false)
  const [stopping, setStopping] = useState(false)
  const [seconds, setSeconds] = useState(0)
  const [level, setLevel] = useState(0)
  const [micOn, setMicOn] = useState(false)
  const marks = useRef<StepMark[]>([])
  const started = useRef(performance.now())
  const audioOrigin = useRef(performance.now())
  const recorder = useRef<MediaRecorder | null>(null)
  const chunks = useRef<Blob[]>([])

  useEffect(() => {
    const timer = window.setInterval(() => setSeconds(Math.floor((performance.now() - started.current) / 1000)), 500)
    const source = new EventSource('/api/record/events')
    source.onmessage = (event) => {
      const payload = JSON.parse(event.data) as { type: string; step?: LiveStep; steps?: LiveStep[] }
      if (payload.type === 'ready' && payload.steps) {
        setSteps(payload.steps)
        marks.current = payload.steps.map(() => ({ t: Math.max(0, (performance.now() - audioOrigin.current) / 1000) }))
      }
      if (payload.type === 'step' && payload.step) {
        marks.current.push({ t: Math.max(0, (performance.now() - audioOrigin.current) / 1000) })
        setSteps((current) => [...current, payload.step!])
      }
      if (payload.type === 'paused') setPaused(true)
      if (payload.type === 'resumed') setPaused(false)
    }
    const micId = localStorage.getItem(MIC_KEY) ?? ''
    void navigator.mediaDevices.getUserMedia({ audio: micId ? { deviceId: { exact: micId } } : true }).then(async (stream) => {
      setMicOn(true)
      const context = new AudioContext()
      await context.resume()
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
      const delay = (performance.now() - audioOrigin.current) / 1000
      audioOrigin.current = performance.now()
      marks.current = marks.current.map((mark) => ({ t: Math.max(0, mark.t - delay) }))
      const media = new MediaRecorder(stream)
      chunks.current = []
      media.ondataavailable = (event) => { if (event.data.size) chunks.current.push(event.data) }
      media.start(1000)
      recorder.current = media
      return () => { cancelAnimationFrame(frame); void context.close() }
    }).catch(() => onError('Микрофон недоступен. Запись шагов продолжится без голоса.'))
    return () => { window.clearInterval(timer); source.close() }
  }, [onError])

  const clock = `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`

  async function stopRecording() {
    if (stopping) return
    setStopping(true)
    try {
      const blob = await stopRecorder(recorder.current, chunks.current)
      const response = await fetch('/api/record/stop', { method: 'POST' })
      const payload = await response.json() as { id?: string; steps?: Array<{ id: string }>; error?: string }
      if (!response.ok || !payload.id) {
        onError(payload.error ?? 'Не удалось остановить запись')
        setStopping(false)
        return
      }
      await onStopped(payload.id, marks.current.slice(0, payload.steps?.length ?? marks.current.length), blob)
    } catch {
      onError('Не удалось остановить запись')
      setStopping(false)
    }
  }

  const stopButton = (
    <button type="button" className="primary" disabled={stopping} onClick={() => void stopRecording()}>
      {stopping ? 'Собираю урок…' : 'Стоп'}
    </button>
  )

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
      {bannerHost && createPortal(stopButton, bannerHost)}
      <div className="row">
        <button type="button" className="tertiary" disabled={stopping} onClick={async () => {
          await fetch(paused ? '/api/record/resume' : '/api/record/pause', { method: 'POST' })
          if (paused) recorder.current?.resume()
          else recorder.current?.pause()
        }}>{paused ? 'Продолжить' : 'Пауза'}</button>
        {!bannerHost && stopButton}
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


type StudioInfo = {
  studioVersion: string
  studioApiVersion: number
  port: number
  dataDir: string
  platform: { connected: false } | { connected: true; server: string; platformVersion: string | null; apiVersion: number | null; compatible: boolean }
}

function SettingsDialog({ desktop, info, onClose }: {
  desktop?: Window['uitrainerDesktop']
  info: StudioInfo | null
  onClose: () => void
}) {
  const [dataDir, setDataDir] = useState(info?.dataDir ?? '')
  const [port, setPort] = useState(String(info?.port ?? 44731))
  const [error, setError] = useState<string | null>(null)
  const [pendingMigrate, setPendingMigrate] = useState(false)

  async function apply(migrate: 'move' | 'switch' | null) {
    if (!desktop) return
    const nextPort = Number(port)
    if (!Number.isInteger(nextPort) || nextPort < 1024 || nextPort > 65535) {
      setError('Порт должен быть от 1024 до 65535')
      return
    }
    const result = await desktop.saveSettings({ dataDir, port: nextPort, migrate })
    if ('error' in result) {
      setError(result.error)
      return
    }
    onClose()
  }

  return (
    <div className="dialog-backdrop" onMouseDown={onClose}>
      <div className="dialog form" role="dialog" aria-modal="true" aria-labelledby="settings-title" onMouseDown={(event) => event.stopPropagation()}>
        <h2 id="settings-title">Настройки</h2>
        {error && <p className="error" role="alert">{error}</p>}
        <label>Папка данных
          <input value={dataDir} onChange={(event) => setDataDir(event.target.value)} readOnly={!desktop} />
        </label>
        {desktop && (
          <button type="button" className="tertiary" onClick={async () => {
            const picked = await desktop.pickDataDir()
            if (picked) setDataDir(picked)
          }}>Выбрать…</button>
        )}
        <label>Порт студии
          <input type="number" min={1024} max={65535} value={port} onChange={(event) => setPort(event.target.value)} />
        </label>
        <p className="hint">Изменения применятся после перезапуска студии.</p>
        {pendingMigrate ? (
          <div className="dialog-actions">
            <button type="button" className="tertiary" onClick={() => void apply('switch')}>Просто переключиться</button>
            <button type="button" className="primary" onClick={() => void apply('move')}>Перенести данные</button>
          </div>
        ) : (
          <div className="dialog-actions">
            <button type="button" className="tertiary" onClick={onClose}>Закрыть</button>
            {desktop && (
              <button type="button" className="primary" onClick={() => {
                if (dataDir && info && dataDir !== info.dataDir) setPendingMigrate(true)
                else void apply(null)
              }}>Сохранить</button>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function AboutDialog({ info, desktop, onClose }: { info: StudioInfo; desktop?: Window['uitrainerDesktop']; onClose: () => void }) {
  const [appVersion, setAppVersion] = useState(info.studioVersion)
  useEffect(() => {
    if (!desktop) return
    void desktop.getAppVersion().then(setAppVersion)
  }, [desktop])
  return (
    <div className="dialog-backdrop" onMouseDown={onClose}>
      <div className="dialog" role="dialog" aria-modal="true" aria-labelledby="about-title" onMouseDown={(event) => event.stopPropagation()}>
        <h2 id="about-title">О программе</h2>
        <p>Студия {appVersion}</p>
        <p>API студии {info.studioApiVersion}</p>
        {info.platform.connected ? (
          <p>
            Платформа {info.platform.server}
            {info.platform.platformVersion ? ` · ${info.platform.platformVersion}` : ''}
            {info.platform.apiVersion != null ? ` · API ${info.platform.apiVersion}` : ''}
          </p>
        ) : (
          <p>Платформа не подключена</p>
        )}
        <div className="dialog-actions">
          <button type="button" className="primary" onClick={onClose}>Закрыть</button>
        </div>
      </div>
    </div>
  )
}

type PlatformState =
  | { connected: false }
  | { connected: true; server: string; username: string; displayName?: string | null; courseId: string | null; courseTitle: string | null; expiresAt: string | null }

function initial(name: string): string {
  const ch = name.trim().charAt(0)
  return ch ? ch.toLocaleUpperCase('ru-RU') : '?'
}

function speechInstallLabel(whisper: Whisper): string {
  if (whisper.phase === 'downloading') return downloadLabel(whisper.percent, whisper.loadedBytes, whisper.totalBytes)
  if (whisper.modelBytes > 0) return `Установить распознавание речи (≈${Math.round(whisper.modelBytes / (1024 * 1024))} МБ)`
  return 'Установить распознавание речи'
}

function SpeechInstallButton({ whisper, onWhisper, onError }: {
  whisper: Whisper
  onWhisper: (value: Whisper) => void
  onError: (message: string) => void
}) {
  const downloading = whisper.phase === 'downloading'
  return (
    <button type="button" className="tertiary" disabled={downloading} onClick={async () => {
      onError('')
      const response = await fetch('/api/whisper/download', { method: 'POST' })
      const payload = await response.json() as Whisper & { error?: string }
      if (!response.ok) {
        onError(payload.error ?? 'Не удалось установить распознавание речи. Проверьте интернет и повторите.')
        return
      }
      onWhisper(payload)
    }}>{speechInstallLabel(whisper)}</button>
  )
}

function ChromiumInstall({ browsers, onBrowsers }: {
  browsers: BrowsersPayload
  onBrowsers: (value: BrowsersPayload) => void
}) {
  const status = browsers.chromium
  useEffect(() => {
    if (status.phase !== 'installing') return
    const timer = window.setInterval(() => {
      void fetch('/api/browsers').then(async (response) => {
        onBrowsers(await response.json() as BrowsersPayload)
      }).catch(() => undefined)
    }, 2000)
    return () => window.clearInterval(timer)
  }, [status.phase])

  if (status.installed || status.phase === 'ready') {
    return <p className="installed">Chromium установлен</p>
  }
  return (
    <div>
      <button type="button" className="tertiary" disabled={status.phase === 'installing'} onClick={async () => {
        const response = await fetch('/api/browsers/install-chromium', { method: 'POST' })
        onBrowsers(await response.json() as BrowsersPayload)
      }}>{status.phase === 'installing' ? 'Устанавливаю…' : 'Установить Chromium'}</button>
      {status.phase === 'error' && <p className="error" role="alert">{status.error}</p>}
    </div>
  )
}

function PlatformBar({ platform, onForget }: { platform: PlatformState; onForget: () => void }) {
  if (!platform.connected) {
    return (
      <p className="muted" data-testid="platform-status">
        Платформа не подключена. В платформе нажмите «Записать в студии» — студия подключится сама.
      </p>
    )
  }
  return (
    <p className="meta" data-testid="platform-status">
      Платформа: <a href={platform.server} target="_blank" rel="noreferrer">{platform.server}</a>{' '}
      <button type="button" className="tertiary" onClick={onForget}>Отключить</button>
    </p>
  )
}
