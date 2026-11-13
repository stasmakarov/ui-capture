import { rm } from 'node:fs/promises';
import path from 'node:path';
import type { CaptureAction, CaptureScreen } from './types.ts';

export type UndoLastResult = {
  undone: boolean;
  actions: CaptureAction[];
  screens: CaptureScreen[];
  removedScreen: CaptureScreen | null;
};

export function undoLastAction(actions: CaptureAction[], screens: CaptureScreen[]): UndoLastResult {
  if (actions.length === 0) {
    return { undone: false, actions, screens, removedScreen: null };
  }
  const last = actions[actions.length - 1]!;
  const nextActions = actions.slice(0, -1);
  const lastScreen = screens.at(-1) ?? null;
  const openedLast = !!last.nextScreenId && lastScreen !== null && last.nextScreenId === lastScreen.id;
  const othersUse = openedLast && nextActions.some(
    (action) => action.screenId === lastScreen.id || action.nextScreenId === lastScreen.id,
  );
  if (openedLast && !othersUse) {
    return {
      undone: true,
      actions: nextActions,
      screens: screens.slice(0, -1),
      removedScreen: lastScreen,
    };
  }
  return { undone: true, actions: nextActions, screens, removedScreen: null };
}

export function shouldRecaptureAfterUndo(savedPrint: string | undefined, livePrint: string): boolean {
  return savedPrint === undefined || savedPrint !== livePrint;
}

export async function removeScreenFiles(captureDir: string, screen: CaptureScreen): Promise<void> {
  for (const relative of [screen.screenshotFile, screen.rrwebFile, screen.frozenHtmlFile]) {
    await rm(path.join(captureDir, relative), { force: true });
  }
}
