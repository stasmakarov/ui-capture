import { useEffect, useState } from 'react'
import App from '../../player/src/App.tsx'
import { StudioApp } from './StudioApp.tsx'

export function StudioGate() {
  const play = new URLSearchParams(window.location.search).get('play')
  const [mode, setMode] = useState<'loading' | 'studio' | 'player'>(play ? 'player' : 'loading')

  useEffect(() => {
    if (play) return
    let cancelled = false
    fetch('/api/health')
      .then((response) => {
        if (!cancelled) setMode(response.ok ? 'studio' : 'player')
      })
      .catch(() => {
        if (!cancelled) setMode('player')
      })
    return () => {
      cancelled = true
    }
  }, [play])

  if (mode === 'loading') return <p className="muted">Загрузка студии…</p>
  if (mode === 'player') return <App />
  return <StudioApp />
}
