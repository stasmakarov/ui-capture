import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@fontsource/pt-sans/cyrillic-400.css'
import '@fontsource/pt-sans/cyrillic-700.css'
import '@fontsource/pt-sans/latin-400.css'
import '@fontsource/pt-sans/latin-700.css'
import './index.css'
import { StudioGate } from '../studio/src/StudioGate.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <StudioGate />
  </StrictMode>,
)
