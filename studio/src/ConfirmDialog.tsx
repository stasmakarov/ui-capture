import { useEffect } from 'react'

export function ConfirmDialog({
  title,
  message,
  confirmLabel,
  tone,
  onConfirm,
  onCancel,
}: {
  title: string
  message: string
  confirmLabel: string
  tone: 'danger' | 'primary'
  onConfirm: () => void
  onCancel: () => void
}) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onCancel()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onCancel])

  return (
    <div className="dialog-backdrop" onMouseDown={onCancel}>
      <div
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
        onMouseDown={(event) => event.stopPropagation()}
      >
        <h2 id="confirm-title">{title}</h2>
        <p>{message}</p>
        <div className="dialog-actions">
          <button type="button" className="tertiary" onClick={onCancel}>Отмена</button>
          <button type="button" className={tone} onClick={onConfirm}>{confirmLabel}</button>
        </div>
      </div>
    </div>
  )
}
