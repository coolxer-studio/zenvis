declare module 'turndown-plugin-gfm' {
  import type { Plugin } from 'turndown';

  export const gfm: Plugin;
  export const tables: Plugin;
  export const strikethrough: Plugin;
  export const taskListItems: Plugin;
}

declare module 'turndown' {
  export type Plugin = (service: TurndownService) => void;

  export type Options = {
    headingStyle?: 'setext' | 'atx';
    bulletListMarker?: '-' | '+' | '*';
    codeBlockStyle?: 'indented' | 'fenced';
    emDelimiter?: '_' | '*';
    strongDelimiter?: '__' | '**';
  };

  export default class TurndownService {
    constructor(options?: Options);
    use(plugin: Plugin | Plugin[]): this;
    keep(tags: string | string[]): this;
    turndown(input: string | Node): string;
  }
}
