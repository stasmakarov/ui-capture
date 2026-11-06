import { gostBrowserHint } from '../studio/browsers.ts'

export function explainOpenError(error: unknown, url: string): string {
  const message = error instanceof Error ? error.message.split('\n')[0] ?? 'неизвестная ошибка' : String(error);
  const tls = /ERR_SSL|ERR_CERT|SSL_PROTOCOL|CERT_|CIPHER|ГОСТ|handshake/i.test(message);
  const lines = [
    `Не удалось открыть ${url}.`,
    message,
  ];
  if (tls) {
    lines.push(
      'Сайт не открылся из-за сертификата (российские сертификаты ГОСТ). Встроенный Chromium их не понимает.',
      gostBrowserHint,
      'В студии откройте «Браузер для записи», выберите Яндекс Браузер и начните запись снова.',
      'Из терминала:',
      '  npx uitrainer <url> --channel chrome',
      '  npx uitrainer <url> --executable-path "/Applications/Yandex.app/Contents/MacOS/Yandex"',
    );
  } else {
    lines.push('Проверьте адрес и сеть. Если сайт не открывается из-за сертификата, в студии выберите Яндекс Браузер в списке «Браузер для записи».');
  }
  return lines.join('\n');
}

export function explainLaunchError(error: unknown): string {
  const message = error instanceof Error ? error.message.split('\n')[0] ?? 'неизвестная ошибка' : String(error);
  return [
    'Не удалось запустить браузер.',
    message,
    'Поставьте Chromium командой npx playwright install chromium.',
    'Либо укажите уже установленный Chrome или Яндекс Браузер:',
    '  npx uitrainer <url> --channel chrome',
    '  npx uitrainer <url> --executable-path "/Applications/Yandex.app/Contents/MacOS/Yandex"',
  ].join('\n');
}
