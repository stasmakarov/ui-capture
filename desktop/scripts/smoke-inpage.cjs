const fs = require('node:fs')
const path = require('node:path')

const resources = process.resourcesPath
if (!resources) {
  console.error('smoke inpage: нет process.resourcesPath — запускайте через ELECTRON_RUN_AS_NODE из win-unpacked')
  process.exit(1)
}
const inpage = path.join(resources, 'inpage.js')
if (!fs.existsSync(inpage)) {
  console.error(`smoke inpage: нет ${inpage}`)
  process.exit(1)
}
const serverFile = path.join(resources, 'app.asar', 'dist', 'server.cjs')
if (!fs.existsSync(serverFile)) {
  console.error(`smoke inpage: нет ${serverFile}`)
  process.exit(1)
}
const server = require(serverFile)
if (typeof server.loadInpageScript !== 'function') {
  console.error('smoke inpage: server.cjs не экспортирует loadInpageScript')
  process.exit(1)
}
server.loadInpageScript().then((script) => {
  if (typeof script !== 'string' || script.length < 50) {
    console.error('smoke inpage: скрипт снимка пустой')
    process.exit(1)
  }
  console.log(`smoke inpage ok (${script.length} bytes)`)
}).catch((error) => {
  console.error(error)
  process.exit(1)
})
