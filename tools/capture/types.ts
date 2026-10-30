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

export type CaptureScreen = {
  id: string;
  title: string;
  url: string;
  pageTitle: string;
  viewport: { width: number; height: number };
  screenshotFile: string;
  rrwebFile: string;
  frozenHtmlFile: string;
  elements: DescribedElement[];
};

export type CaptureAction = {
  screenId: string;
  kind: 'fill' | 'click' | 'press' | 'select';
  confirm: 'match' | 'enter' | null;
  value: string;
  hotspotId: string;
  selector: string;
  label: string;
  box: Box;
  nextScreenId: string | null;
  prelude: boolean;
};

export type SavedAsset = {
  url: string;
  file: string;
  contentType: string;
};

export type CaptureBundle = {
  id: string;
  title: string;
  description: string;
  sourceSite: string;
  startUrl: string;
  viewport: { width: number; height: number };
  capturedAt: string;
  screens: CaptureScreen[];
  actions: CaptureAction[];
  assets: SavedAsset[];
};
