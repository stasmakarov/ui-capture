import { startStudio, type StudioOptions } from './server.ts'

export async function startStudioDev(options: StudioOptions) {
  if (options.staticDir || options.dev) return startStudio(options)
  const vite = await (await import('vite')).createServer({
    server: {
      middlewareMode: true,
      watch: {
        ignored: ['**/artifacts/**', '**/platform-backend/**', '**/dist-single/**', '**/dist-template/**', '**/captures/**', '**/lessons/**', '**/.uitrainer/**'],
      },
    },
    appType: 'spa',
  })
  return startStudio({
    ...options,
    dev: {
      middlewares: vite.middlewares,
      close: () => vite.close(),
    },
  })
}
