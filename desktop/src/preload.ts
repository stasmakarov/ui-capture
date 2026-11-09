import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('uitrainerDesktop', {
  isDesktop: true,
  getConfig: () => ipcRenderer.invoke('desktop:getConfig'),
  pickDataDir: () => ipcRenderer.invoke('desktop:pickDataDir'),
  saveSettings: (payload: { dataDir: string; port: number; migrate: 'move' | 'switch' | null }) =>
    ipcRenderer.invoke('desktop:saveSettings', payload),
  checkUpdates: () => ipcRenderer.invoke('desktop:checkUpdates'),
  getAppVersion: () => ipcRenderer.invoke('desktop:getAppVersion'),
})

contextBridge.exposeInMainWorld('portDialog', {
  submit: (port: number) => ipcRenderer.send('port-chosen', port),
  cancel: () => ipcRenderer.send('port-chosen', null),
})
