import { snapshot } from 'rrweb-snapshot';

export type Box = { x: number; y: number; width: number; height: number };

export type HotspotKind =
  | 'input'
  | 'textarea'
  | 'select'
  | 'button'
  | 'link'
  | 'tab'
  | 'clickable';

export type DescribedElement = {
  hotspotId: string;
  selector: string;
  kind: HotspotKind;
  label: string;
  box: Box;
  inputType?: string;
  placeholder?: string;
};

function boxOf(el: Element): Box {
  const rect = el.getBoundingClientRect();
  return { x: rect.x, y: rect.y, width: rect.width, height: rect.height };
}

function stableId(id: string): boolean {
  return /^[A-Za-z][\w:.-]{0,80}$/.test(id);
}

function selectorFor(el: Element): string {
  if (el.id && stableId(el.id)) {
    const sel = `#${CSS.escape(el.id)}`;
    if (document.querySelectorAll(sel).length === 1) return sel;
  }
  const testId = el.getAttribute('data-testid');
  if (testId) {
    const sel = `[data-testid="${CSS.escape(testId)}"]`;
    if (document.querySelectorAll(sel).length === 1) return sel;
  }
  const tag = el.tagName.toLowerCase();
  const name = el.getAttribute('name');
  if (name && (tag === 'input' || tag === 'textarea' || tag === 'select')) {
    const sel = `${tag}[name="${CSS.escape(name)}"]`;
    if (document.querySelectorAll(sel).length === 1) return sel;
  }
  for (const className of el.classList) {
    if (!/^[A-Za-z][\w-]{1,80}$/.test(className)) continue;
    const sel = `${tag}.${CSS.escape(className)}`;
    if (document.querySelectorAll(sel).length === 1) return sel;
  }
  const parentId = el.parentElement?.id;
  if (parentId && stableId(parentId) && (tag === 'a' || tag === 'button')) {
    const sel = `#${CSS.escape(parentId)} ${tag}`;
    if (document.querySelectorAll(sel).length === 1) return sel;
  }
  const parts: string[] = [];
  let node: Element | null = el;
  while (node && node !== document.body && node !== document.documentElement) {
    if (node.id && stableId(node.id)) {
      parts.unshift(`#${CSS.escape(node.id)}`);
      break;
    }
    const nodeTag = node.tagName.toLowerCase();
    const parent: HTMLElement | null = node.parentElement;
    if (!parent) {
      parts.unshift(nodeTag);
      break;
    }
    const same = [...parent.children].filter((child) => child.tagName === node?.tagName);
    const part = same.length > 1 ? `${nodeTag}:nth-of-type(${same.indexOf(node) + 1})` : nodeTag;
    parts.unshift(part);
    node = parent;
  }
  return parts.join(' > ');
}

function kindOf(el: Element): HotspotKind | null {
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
  if (tag === 'A') {
    const parentId = el.parentElement?.id ?? '';
    if (parentId.startsWith('ca-')) return 'tab';
    return 'link';
  }
  if (role === 'button') return 'button';
  return null;
}

function clean(value: string): string {
  return value.replace(/\s+/g, ' ').trim().slice(0, 140);
}

function labelOf(el: Element): string {
  const aria = el.getAttribute('aria-label');
  if (aria?.trim()) return clean(aria);
  if (el.id) {
    const lab = document.querySelector(`label[for="${CSS.escape(el.id)}"]`);
    const text = lab?.textContent;
    if (text?.trim()) return clean(text);
  }
  const own = clean(el.textContent || '');
  if (own) return own;
  const placeholder = el.getAttribute('placeholder') || el.getAttribute('title') || el.getAttribute('name');
  if (placeholder) return clean(placeholder);
  if ('value' in el && typeof (el as HTMLInputElement).value === 'string' && (el as HTMLInputElement).value) {
    return clean((el as HTMLInputElement).value);
  }
  return el.tagName.toLowerCase();
}

function hotspotId(selector: string): string {
  let hash = 2166136261;
  for (let i = 0; i < selector.length; i += 1) {
    hash ^= selector.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return `h${(hash >>> 0).toString(16)}`;
}

function describe(el: Element): DescribedElement {
  const selector = selectorFor(el);
  return {
    hotspotId: hotspotId(selector),
    selector,
    kind: kindOf(el) ?? 'clickable',
    label: labelOf(el),
    box: boxOf(el),
    inputType: el.getAttribute('type') ?? undefined,
    placeholder: el.getAttribute('placeholder') ?? undefined,
  };
}

function listInteractive(): DescribedElement[] {
  const nodes = document.querySelectorAll('input, textarea, select, button, a, [role="tab"]');
  const found: DescribedElement[] = [];
  const seen = new Set<string>();
  for (const el of nodes) {
    if (!kindOf(el)) continue;
    const box = boxOf(el);
    if (box.width < 2 || box.height < 2) continue;
    if (box.y + box.height < 0 || box.y > window.innerHeight) continue;
    const item = describe(el);
    if (seen.has(item.hotspotId)) continue;
    seen.add(item.hotspotId);
    found.push(item);
  }
  return found;
}

function bakePair(live: Element, clone: Element) {
  if (live.tagName === 'INPUT' && clone.tagName === 'INPUT') {
    const input = live as HTMLInputElement;
    const type = (input.getAttribute('type') || 'text').toLowerCase();
    if (type === 'checkbox' || type === 'radio') {
      if (input.checked) clone.setAttribute('checked', '');
      else clone.removeAttribute('checked');
    } else if (type !== 'file') {
      clone.setAttribute('value', input.value);
    }
  }
  if (live.tagName === 'TEXTAREA' && clone.tagName === 'TEXTAREA') {
    clone.textContent = (live as HTMLTextAreaElement).value;
  }
  if (live.tagName === 'OPTION' && clone.tagName === 'OPTION') {
    if ((live as HTMLOptionElement).selected) clone.setAttribute('selected', '');
    else clone.removeAttribute('selected');
  }
  const liveChildren = [...live.children];
  const cloneChildren = [...clone.children];
  const count = Math.min(liveChildren.length, cloneChildren.length);
  for (let i = 0; i < count; i += 1) {
    const liveChild = liveChildren[i];
    const cloneChild = cloneChildren[i];
    if (liveChild && cloneChild) bakePair(liveChild, cloneChild);
  }
}

function rulesText(sheet: CSSStyleSheet): string {
  const parts: string[] = [];
  for (const rule of [...sheet.cssRules]) {
    if (rule.type === CSSRule.IMPORT_RULE) {
      const imported = (rule as CSSImportRule).styleSheet;
      if (imported) parts.push(rulesText(imported));
    } else {
      parts.push(rule.cssText);
    }
  }
  return parts.join('\n');
}

function readSheets(): { href: string | null; css: string }[] {
  const styles: { href: string | null; css: string }[] = [];
  for (const sheet of [...document.styleSheets]) {
    try {
      styles.push({ href: sheet.href, css: rulesText(sheet) });
    } catch {
      styles.push({ href: sheet.href, css: '' });
    }
  }
  const adopted = (document as Document & { adoptedStyleSheets?: CSSStyleSheet[] }).adoptedStyleSheets ?? [];
  for (const sheet of adopted) {
    try {
      styles.push({ href: null, css: rulesText(sheet) });
    } catch {
      /* cross-origin or empty */
    }
  }
  return styles;
}

function freeze(): string {
  const styles = readSheets();
  const clone = document.documentElement.cloneNode(true) as HTMLElement;
  clone.querySelectorAll('script').forEach((node) => node.remove());
  const head = clone.querySelector('head');
  for (const item of styles) {
    if (!item.css) continue;
    const style = document.createElement('style');
    if (item.href) style.setAttribute('data-trainer-base', item.href);
    else style.setAttribute('data-trainer-adopted', '');
    style.textContent = item.css.replace(/<\/style/gi, '<\\/style');
    if (item.href) {
      const links = [...clone.querySelectorAll('link[rel~="stylesheet"]')];
      const link = links.find((node) => (node as HTMLLinkElement).href === item.href);
      if (link?.parentNode) {
        link.parentNode.insertBefore(style, link);
        link.remove();
        continue;
      }
    }
    if (!item.href && style.getAttribute('data-trainer-adopted') !== null) {
      head?.appendChild(style);
    }
  }
  bakePair(document.documentElement, clone);
  return `<!DOCTYPE html>\n${clone.outerHTML}`;
}

function takeSnapshot() {
  return snapshot(document, {
    inlineStylesheet: true,
    inlineImages: false,
    slimDOM: { script: true, comment: true },
  });
}

window.__trainer = {
  describe,
  listInteractive,
  freeze,
  takeSnapshot,
};

declare global {
  interface Window {
    __trainer: {
      describe: (el: Element) => DescribedElement;
      listInteractive: () => DescribedElement[];
      freeze: () => string;
      takeSnapshot: () => ReturnType<typeof takeSnapshot>;
    };
  }
}
