import { describe, expect, it } from 'vitest';
import { explainLaunchError, explainOpenError } from './errors.ts';

describe('explainOpenError', () => {
  it('подсказывает Яндекс Браузер при ошибке TLS', () => {
    const text = explainOpenError(new Error('net::ERR_SSL_VERSION_OR_CIPHER_MISMATCH'), 'https://zakupki.gov.ru/');
    expect(text).toContain('ГОСТ');
    expect(text).toContain('--executable-path');
    expect(text).toContain('Yandex');
    expect(text).toContain('--channel chrome');
  });
});

describe('explainLaunchError', () => {
  it('подсказывает установку Chromium', () => {
    const text = explainLaunchError(new Error('browserType.launch: Executable doesn\'t exist'));
    expect(text).toContain('playwright install chromium');
    expect(text).toContain('--channel chrome');
  });
});
