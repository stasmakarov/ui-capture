import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { JSDOM } from 'jsdom';
import { createCache, Mirror, rebuild } from 'rrweb-snapshot';
import type { DescribedElement, SavedAsset } from '../capture/types.ts';

const ASSET_ATTRS = ['src', 'href', 'poster'] as const;

export function rebuildRrweb(snapshot: unknown, url: string): string {
  const dom = new JSDOM('<!DOCTYPE html><html><head></head><body></body></html>', { url });
  rebuild(snapshot as Parameters<typeof rebuild>[0], {
    doc: dom.window.document,
    cache: createCache(),
    mirror: new Mirror(),
    hackCss: true,
    UNSAFE_allowUnprotectedRebuild: true,
  });
  return dom.serialize();
}

function stripActiveContent(document: Document) {
  document.querySelectorAll('script, noscript').forEach((node) => node.remove());
  document.querySelectorAll('link[rel="modulepreload"], link[rel="preload"][as="script"]').forEach((node) => node.remove());
  document.querySelectorAll('iframe, frame').forEach((node) => {
    node.removeAttribute('src');
    node.removeAttribute('srcdoc');
  });
  document.querySelectorAll('base').forEach((node) => node.remove());
  const nodes = document.querySelectorAll('*');
  for (const node of nodes) {
    for (const attr of [...node.attributes]) {
      const name = attr.name.toLowerCase();
      if (name.startsWith('on')) node.removeAttribute(attr.name);
      if ((name === 'href' || name === 'src' || name === 'xlink:href') && attr.value.trim().toLowerCase().startsWith('javascript:')) {
        node.setAttribute(attr.name, '#');
      }
    }
  }
  const meta = document.createElement('meta');
  meta.setAttribute('http-equiv', 'Content-Security-Policy');
  meta.setAttribute('content', "script-src 'none'; object-src 'none'; base-uri 'none'");
  document.head?.prepend(meta);
}

function kindOf(el: Element): DescribedElement['kind'] | null {
  const tag = el.tagName;
  if (tag === 'TEXTAREA') return 'textarea';
  if (tag === 'SELECT') return 'select';
  if (tag === 'INPUT') {
    const type = (el.getAttribute('type') || 'text').toLowerCase();
    if (type === 'hidden') return null;
    if (type === 'submit' || type === 'button' || type === 'image' || type === 'reset') return 'button';
    if (type === 'checkbox' || type === 'radio') return 'clickable';
    return 'input';
  }
  if (tag === 'BUTTON') return 'button';
  const role = el.getAttribute('role');
  if (role === 'tab') return 'tab';
  if (tag === 'A') return el.parentElement?.id?.startsWith('ca-') ? 'tab' : 'link';
  if (role === 'button') return 'button';
  return null;
}

export function applyHotspots(html: string, elements: DescribedElement[], pageUrl: string): { html: string; missing: string[] } {
  const dom = new JSDOM(html, { url: pageUrl });
  const document = dom.window.document;
  stripActiveContent(document);
  const missing: string[] = [];
  for (const element of elements) {
    let node: Element | null = null;
    try {
      node = document.querySelector(element.selector);
    } catch {
      node = null;
    }
    if (!node) {
      missing.push(element.selector);
      continue;
    }
    node.setAttribute('data-hotspot', element.hotspotId);
  }
  let extra = 0;
  for (const node of document.querySelectorAll('input, textarea, select, button, a, [role="tab"]')) {
    if (node.hasAttribute('data-hotspot')) continue;
    if (!kindOf(node)) continue;
    const id = `x${extra}`;
    extra += 1;
    node.setAttribute('data-hotspot', id);
  }
  return { html: `<!DOCTYPE html>\n${document.documentElement.outerHTML}`, missing };
}

type AssetIndex = Map<string, SavedAsset>;

function buildIndex(assets: SavedAsset[]): AssetIndex {
  const index: AssetIndex = new Map();
  for (const asset of assets) {
    index.set(asset.url, asset);
    index.set(asset.url.split('#')[0] ?? asset.url, asset);
  }
  return index;
}

async function ensureSource(asset: SavedAsset, captureDir: string): Promise<string> {
  const source = path.join(captureDir, 'net', asset.file);
  try {
    await readFile(source);
    return source;
  } catch {
    const response = await fetch(asset.url);
    if (!response.ok) throw new Error(String(response.status));
    await mkdir(path.dirname(source), { recursive: true });
    await writeFile(source, Buffer.from(await response.arrayBuffer()));
    return source;
  }
}

export async function localizeHtml(options: {
  html: string;
  pageUrl: string;
  captureDir: string;
  assetDir: string;
  assets: SavedAsset[];
}): Promise<{ html: string; missingAssets: string[] }> {
  const index = buildIndex(options.assets);
  const missingAssets = new Set<string>();
  const resolved = new Map<string, string>();
  const publicPath = '../assets/';

  async function localFor(raw: string, base: string): Promise<string | null> {
    if (!raw || raw.startsWith('data:') || raw.startsWith('blob:') || raw.startsWith('#') || raw.startsWith('about:')) {
      return null;
    }
    let absolute: string;
    try {
      absolute = new URL(raw, base).href;
    } catch {
      return null;
    }
    if (resolved.has(absolute)) return resolved.get(absolute) ?? null;
    const known = index.get(absolute) ?? index.get(absolute.split('#')[0] ?? absolute);
    if (!known) {
      if (absolute.startsWith('http')) missingAssets.add(absolute);
      return null;
    }
    const local = `${publicPath}${known.file}`;
    resolved.set(absolute, local);
    try {
      await mkdir(options.assetDir, { recursive: true });
      const source = await ensureSource(known, options.captureDir);
      const target = path.join(options.assetDir, known.file);
      const isCss = known.file.endsWith('.css') || known.contentType.includes('css');
      if (isCss) {
        const css = await readFile(source, 'utf8');
        await writeFile(target, await rewriteCss(css, absolute));
      } else {
        await copyFile(source, target);
      }
      return local;
    } catch {
      resolved.delete(absolute);
      missingAssets.add(absolute);
      return null;
    }
  }

  async function rewriteCss(css: string, base: string): Promise<string> {
    const regexp = /url\(\s*(['"]?)([^'")]+)\1\s*\)/g;
    let out = '';
    let cursor = 0;
    for (const match of css.matchAll(regexp)) {
      const indexAt = match.index ?? 0;
      out += css.slice(cursor, indexAt);
      const raw = match[2]?.trim() ?? '';
      const local = await localFor(raw, base);
      out += local && match[0] ? `url("${local}")` : match[0] ?? '';
      cursor = indexAt + (match[0]?.length ?? 0);
    }
    return out + css.slice(cursor);
  }

  const dom = new JSDOM(options.html, { url: options.pageUrl });
  const document = dom.window.document;
  for (const style of document.querySelectorAll('style')) {
    const base = style.getAttribute('data-trainer-base') || options.pageUrl;
    style.textContent = await rewriteCss(style.textContent ?? '', base);
  }
  for (const node of document.querySelectorAll('[style]')) {
    const value = node.getAttribute('style');
    if (!value?.includes('url(')) continue;
    node.setAttribute('style', await rewriteCss(value, options.pageUrl));
  }
  for (const node of document.querySelectorAll('img, source, video, audio, image, link, use')) {
    for (const attr of ASSET_ATTRS) {
      const value = node.getAttribute(attr);
      if (!value || attr === 'href' && node.tagName === 'A') continue;
      if (attr === 'href' && node.tagName === 'LINK') {
        const rel = (node.getAttribute('rel') || '').toLowerCase();
        if (!rel.includes('stylesheet') && !rel.includes('icon') && !rel.includes('font')) continue;
      }
      const local = await localFor(value, options.pageUrl);
      if (local) node.setAttribute(attr, local);
    }
    const srcset = node.getAttribute('srcset');
    if (srcset) {
      const parts = await Promise.all(
        srcset.split(',').map(async (part) => {
          const bits = part.trim().split(/\s+/);
          const url = bits[0];
          if (!url) return part.trim();
          const local = await localFor(url, options.pageUrl);
          if (!local) return part.trim();
          return [local, ...bits.slice(1)].join(' ');
        }),
      );
      node.setAttribute('srcset', parts.join(', '));
    }
  }
  return { html: `<!DOCTYPE html>\n${document.documentElement.outerHTML}`, missingAssets: [...missingAssets] };
}

export async function readSnapshot(file: string): Promise<unknown> {
  return JSON.parse(await readFile(file, 'utf8')) as unknown;
}
