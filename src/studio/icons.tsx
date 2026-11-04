type IconProps = { title?: string }

export function IconUp({ title }: IconProps) {
  return (
    <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden={title ? undefined : true}>
      {title && <title>{title}</title>}
      <path d="M8 3.5 3.5 8.2l1.1 1.1L7.2 6.7V13h1.6V6.7l2.6 2.6 1.1-1.1z" fill="currentColor" />
    </svg>
  )
}

export function IconDown({ title }: IconProps) {
  return (
    <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden={title ? undefined : true}>
      {title && <title>{title}</title>}
      <path d="M8 12.5 12.5 7.8l-1.1-1.1L8.8 9.3V3H7.2v6.3L4.6 6.7 3.5 7.8z" fill="currentColor" />
    </svg>
  )
}

export function IconAudio() {
  return (
    <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
      <path d="M8 2.2a2.2 2.2 0 0 0-2.2 2.2v3.2a2.2 2.2 0 0 0 4.4 0V4.4A2.2 2.2 0 0 0 8 2.2zM4.2 7.2v.4a3.8 3.8 0 0 0 3.2 3.75V13H6v1.2h4V13H8.6v-1.65A3.8 3.8 0 0 0 11.8 7.6v-.4h-1.2v.4a2.6 2.6 0 0 1-5.2 0v-.4z" fill="currentColor" />
    </svg>
  )
}

export function IconHint() {
  return (
    <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
      <path d="M8 1.8a4.2 4.2 0 0 0-2.3 7.7c.3.2.5.6.5 1v.3h3.6v-.3c0-.4.2-.8.5-1A4.2 4.2 0 0 0 8 1.8zM6.4 12.2h3.2V13H6.4zm.5 1.6h2.2v1H6.9z" fill="currentColor" />
    </svg>
  )
}
