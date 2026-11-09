!include "nsDialogs.nsh"
!include "LogicLib.nsh"

!ifndef BUILD_UNINSTALLER
; Страница и переменные нужны только установщику: в проходе сборки деинсталлятора
; они не используются, а предупреждения NSIS electron-builder считает ошибками.
Var StudioDialog
Var DataDirCtl
Var PortCtl
Var DataDir
Var Port

; electron-builder (assistedInstaller.nsh) вставляет customPageAfterChangeDir вне блока
; allowToChangeInstallationDirectory, сразу перед MUI_PAGE_INSTFILES, поэтому страница
; показывается и при allowToChangeInstallationDirectory: false. При обновлении её пропускает
; Abort ниже: DataDir уже есть в реестре.
!macro customPageAfterChangeDir
  Page custom StudioSettingsPageCreate StudioSettingsPageLeave
!macroend

Function StudioSettingsPageCreate
  ReadRegStr $0 HKCU "Software\uitrainer Studio" "DataDir"
  ${If} $0 != ""
    Abort
  ${EndIf}
  nsDialogs::Create 1018
  Pop $StudioDialog
  ${If} $StudioDialog == error
    Abort
  ${EndIf}
  ${NSD_CreateLabel} 0 0 100% 12u "Папка данных"
  Pop $0
  ${NSD_CreateText} 0 14u 78% 12u "$DOCUMENTS\uitrainer"
  Pop $DataDirCtl
  ${NSD_CreateButton} 80% 13u 20% 14u "Обзор…"
  Pop $R1
  ${NSD_OnClick} $R1 StudioBrowseFolder
  ${NSD_CreateLabel} 0 36u 100% 12u "Порт студии"
  Pop $0
  ${NSD_CreateText} 0 50u 40% 12u "44731"
  Pop $PortCtl
  nsDialogs::Show
FunctionEnd

Function StudioBrowseFolder
  nsDialogs::SelectFolderDialog "Папка данных" "$DOCUMENTS"
  Pop $0
  ${If} $0 != "error"
    ${If} $0 != ""
      ${NSD_SetText} $DataDirCtl $0
    ${EndIf}
  ${EndIf}
FunctionEnd

Function StudioSettingsPageLeave
  ${NSD_GetText} $DataDirCtl $DataDir
  ${NSD_GetText} $PortCtl $Port
  ${If} $DataDir == ""
    MessageBox MB_ICONEXCLAMATION "Укажите папку данных"
    Abort
  ${EndIf}
  IntCmp $Port 1024 0 portBad portOkLow
  portOkLow:
  IntCmp $Port 65535 portOk 0 portBad
  Goto portOk
  portBad:
    MessageBox MB_ICONEXCLAMATION "Порт должен быть от 1024 до 65535"
    Abort
  portOk:
FunctionEnd
!endif

!macro customInstall
  ${If} $DataDir == ""
    ReadRegStr $DataDir HKCU "Software\uitrainer Studio" "DataDir"
  ${EndIf}
  ${If} $DataDir == ""
    StrCpy $DataDir "$DOCUMENTS\uitrainer"
  ${EndIf}
  ${If} $Port == ""
    ReadRegStr $Port HKCU "Software\uitrainer Studio" "Port"
  ${EndIf}
  ${If} $Port == ""
    StrCpy $Port "44731"
  ${EndIf}
  ReadRegStr $0 HKCU "Software\uitrainer Studio" "DataDir"
  ${If} $0 == ""
    CreateDirectory "$DataDir"
    WriteRegStr HKCU "Software\uitrainer Studio" "DataDir" "$DataDir"
    WriteRegStr HKCU "Software\uitrainer Studio" "Port" "$Port"
  ${EndIf}
!macroend

!macro customUnInstall
!macroend
