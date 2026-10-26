import { uiThemeTokens } from '@/theme/design-tokens';

type MermaidInstance = Awaited<typeof import('mermaid')>['default'];
type MermaidRenderResult = Awaited<ReturnType<MermaidInstance['render']>>;

const fontFamily = [
  'alibabaRegular',
  '-apple-system',
  'BlinkMacSystemFont',
  'Segoe UI',
  'Roboto',
  'Helvetica Neue',
  'Arial',
  'Noto Sans',
  'sans-serif',
].join(', ');

const themeVariables = {
  background: uiThemeTokens.surface,
  primaryColor: uiThemeTokens.primarySoft,
  primaryTextColor: uiThemeTokens.textPrimary,
  primaryBorderColor: uiThemeTokens.primary,
  secondaryColor: uiThemeTokens.subtleBackground,
  secondaryTextColor: uiThemeTokens.textSecondary,
  secondaryBorderColor: uiThemeTokens.border,
  tertiaryColor: uiThemeTokens.surface,
  tertiaryTextColor: uiThemeTokens.textPrimary,
  tertiaryBorderColor: uiThemeTokens.border,
  mainBkg: uiThemeTokens.surface,
  nodeBorder: uiThemeTokens.primary,
  clusterBkg: uiThemeTokens.subtleBackground,
  clusterBorder: uiThemeTokens.border,
  edgeLabelBackground: uiThemeTokens.surface,
  lineColor: uiThemeTokens.textMuted,
  textColor: uiThemeTokens.textPrimary,
  titleColor: uiThemeTokens.textPrimary,
  fontFamily,
  fontSize: '14px',
};

let mermaidLoader: Promise<MermaidInstance> | null = null;
let renderQueue: Promise<unknown> = Promise.resolve();
let renderSequence = 0;

export const loadMermaid = (): Promise<MermaidInstance> => {
  if (!mermaidLoader) {
    mermaidLoader = import('mermaid')
      .then(module => {
        const mermaid = module.default;
        mermaid.initialize({
          startOnLoad: false,
          securityLevel: 'strict',
          suppressErrorRendering: true,
          logLevel: 'error',
          theme: 'base',
          fontFamily,
          themeVariables,
          flowchart: {
            htmlLabels: true,
            useMaxWidth: false,
          },
          sequence: { useMaxWidth: false },
          gantt: { useMaxWidth: false },
        });
        return mermaid;
      })
      .catch(error => {
        mermaidLoader = null;
        throw error;
      });
  }
  return mermaidLoader;
};

export const nextMermaidRenderId = (): string => {
  renderSequence += 1;
  return `zenvis-mermaid-${renderSequence}`;
};

/**
 * Mermaid's render call mutates shared document state. Serialize all diagram
 * renders so multiple cards in the same message cannot invalidate each other.
 */
export const renderMermaid = (id: string, source: string): Promise<MermaidRenderResult> => {
  const run = async () => {
    const mermaid = await loadMermaid();
    await mermaid.parse(source);
    return mermaid.render(id, source);
  };
  const task = renderQueue.then(run);
  renderQueue = task.then(
    () => undefined,
    () => undefined,
  );
  return task;
};
