import { cpSync, createReadStream, existsSync, statSync } from 'node:fs'
import path from 'node:path'
import react from '@vitejs/plugin-react'
import { viteSingleFile } from 'vite-plugin-singlefile'
import { defineConfig, type Plugin } from 'vite'

const TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.webp': 'image/webp',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf',
  '.otf': 'font/otf',
}

function lessonsPlugin(): Plugin {
  const root = path.resolve('lessons')
  return {
    name: 'lessons-static',
    configureServer(server) {
      server.middlewares.use('/lessons', (request, response, next) => {
        const raw = decodeURIComponent((request.url ?? '/').split('?')[0] ?? '/')
        const file = path.resolve(root, `.${raw}`)
        if (!file.startsWith(root) || !existsSync(file) || statSync(file).isDirectory()) {
          next()
          return
        }
        response.setHeader('Content-Type', TYPES[path.extname(file)] ?? 'application/octet-stream')
        createReadStream(file).pipe(response)
      })
    },
    closeBundle() {
      if (existsSync(root)) cpSync(root, path.resolve('dist/lessons'), { recursive: true })
    },
  }
}

export default defineConfig(({ mode }) => ({
  plugins: [
    react(),
    mode === 'singlefile' ? viteSingleFile({ removeViteModuleLoader: true }) : lessonsPlugin(),
  ],
  resolve: {
    alias: {
      '@embedded': path.resolve(mode === 'singlefile' ? 'src/embedded-from-script.ts' : 'src/embedded-stub.ts'),
    },
  },
  build: mode === 'singlefile'
    ? {
        outDir: 'dist-template',
        emptyOutDir: true,
        rollupOptions: { input: path.resolve('player.html') },
      }
    : undefined,
  server: { host: '127.0.0.1', port: 43127, strictPort: true },
  preview: { host: '127.0.0.1', port: 43127, strictPort: true },
}))
