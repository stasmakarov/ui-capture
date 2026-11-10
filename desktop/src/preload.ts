import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('uitrainerDesktop', {
  isDesktop: true,
  getConfig: () => ipcRenderer.invoke('desktop:getConfig'),
  pickDataDir: () => ipcRenderer.invoke('desktop:pickDataDir'),
  saveSettings: (payload: { dataDir: string; port: number; migrate: 'move' | 'switch' | null }) =>
    ipcRenderer.invoke('desktop:saveSettings', payload),
  checkUpdates: () => ipcRenderer.invoke('desktop:checkUpdates'),
  getAppVersion: () => ipcRenderer.invoke('desktop:getAppVersion'),
  onUpdateProgress: (callback: (info: { version: string; percent: number } | null) => void) => {
    const listener = (_event: unknown, info: { version: string; percent: number } | null) => callback(info)
    ipcRenderer.on('desktop:update-progress', listener)
    return () => ipcRenderer.removeListener('desktop:update-progress', listener)
  },
})

contextBridge.exposeInMainWorld('portDialog', {
  submit: (port: number) => ipcRenderer.send('port-chosen', port),
  cancel: () => ipcRenderer.send('port-chosen', null),
})
