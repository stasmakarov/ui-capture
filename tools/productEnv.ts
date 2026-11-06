/** Первая заданная переменная. Новое имя стоит первым, старые — запасные. Пустая строка считается заданной. */
export function productEnv(primary: string, ...legacy: string[]): string | undefined {
  for (const name of [primary, ...legacy]) {
    if (process.env[name] !== undefined) return process.env[name]
  }
  return undefined
}
