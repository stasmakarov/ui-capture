export function explainOpenError(error: unknown, url: string): string {
  const message = error instanceof Error ? error.message.split('\n')[0] ?? 'неизвестная ошибка' : String(error);
  const tls = /ERR_SSL|ERR_CERT|SSL_PROTOCOL|CERT_|CIPHER|ГОСТ|handshake/i.test(message);
  const lines = [
    `Не удалось открыть ${url}.`,
    message,
  ];
  if (tls) {
    lines.push(
      'Встроенный Chromium не открывает сайты с TLS по ГОСТ (так устроены часть российских порталов, включая zakupki.gov.ru).',
      'Укажите браузер, в котором сайт у вас уже открывается:',
      '  npx ui-capture <url> --channel chrome',
      '  npx ui-capture <url> --executable-path "/Applications/Yandex.app/Contents/MacOS/Yandex"',
    );
  } else {
    lines.push('Проверьте адрес и сеть. Для браузера с ГОСТ-сертификатами добавьте --channel chrome или --executable-path.');
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
    '  npx ui-capture <url> --channel chrome',
    '  npx ui-capture <url> --executable-path "/Applications/Yandex.app/Contents/MacOS/Yandex"',
  ].join('\n');
}
