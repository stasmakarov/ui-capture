import { createReadStream, existsSync, statSync } from 'node:fs';
import { readdir } from 'node:fs/promises';
import http from 'node:http';
import path from 'node:path';
import { chromium } from 'playwright';
import { buildCapture, writeCatalog } from './build.ts';

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
};

function serve(root: string): Promise<{ url: string; close: () => Promise<void> }> {
  const server = http.createServer((request, response) => {
    const raw = decodeURIComponent((request.url ?? '/').split('?')[0] ?? '/');
    const file = path.resolve(root, `.${raw}`);
    if (!file.startsWith(root) || !existsSync(file) || statSync(file).isDirectory()) {
      response.statusCode = 404;
      response.end('not found');
      return;
    }
    response.setHeader('Content-Type', TYPES[path.extname(file)] ?? 'application/octet-stream');
    createReadStream(file).pipe(response);
  });
  return new Promise((resolve) => {
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      const port = typeof address === 'object' && address ? address.port : 0;
      resolve({
        url: `http://127.0.0.1:${port}`,
        close: () => new Promise((done) => server.close(() => done())),
      });
    });
  });
}

const only = process.argv[2];
const capturesRoot = path.resolve('captures');
const entries = await readdir(capturesRoot, { withFileTypes: true });
const dirs = entries
  .filter((entry) => entry.isDirectory() && existsSync(path.join(capturesRoot, entry.name, 'capture.json')))
  .map((entry) => path.join(capturesRoot, entry.name))
  .filter((dir) => !only || path.basename(dir) === only);

if (dirs.length === 0) {
  throw new Error('Нет записей. Сначала запустите npm run capture -- <url>');
}

await import('node:fs/promises').then((fs) => fs.mkdir('lessons', { recursive: true }));
const lessonsRoot = path.resolve('lessons');
const server = await serve(lessonsRoot);
const browser = await chromium.launch({ headless: true });
try {
  for (const dir of dirs) {
    console.log(`\nСборка ${path.basename(dir)}`);
    await buildCapture(dir, browser, server.url);
  }
  await writeCatalog();
  console.log('Каталог уроков обновлён');
} finally {
  await browser.close();
  await server.close();
}
