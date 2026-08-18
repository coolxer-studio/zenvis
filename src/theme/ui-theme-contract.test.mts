import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { describe, test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { uiChartPalette, uiThemeTokens } from './design-tokens.ts';
import {
  buildUiThemeContractV1,
  isUiThemeReadyEvent,
  isUiThemeReadyPayload,
  resolveUiTargetOrigin,
  UI_THEME_CONTRACT_VERSION,
} from './plugin-ui-contract.ts';

describe('ZenVis UI theme contract', () => {
  test('builds an immutable versioned light compact payload', () => {
    const payload = buildUiThemeContractV1({
      locale: 'zh-CN',
      timezone: 'Asia/Shanghai',
      reducedMotion: true,
    });

    assert.equal(payload.type, 'zenvis:ui');
    assert.equal(payload.contractVersion, UI_THEME_CONTRACT_VERSION);
    assert.equal(payload.themeId, 'zenvis-light');
    assert.equal(payload.colorScheme, 'light');
    assert.equal(payload.density, 'compact');
    assert.equal(payload.locale, 'zh-CN');
    assert.equal(payload.timezone, 'Asia/Shanghai');
    assert.equal(payload.reducedMotion, true);
    assert.strictEqual(payload.tokens, uiThemeTokens);
    assert.strictEqual(payload.chartPalette, uiChartPalette);
    assert.ok(Object.isFrozen(payload));
    assert.ok(Object.isFrozen(payload.tokens));
    assert.ok(Object.isFrozen(payload.chartPalette));
  });

  test('validates iframe target origins without wildcard fallbacks', () => {
    const appOrigin = 'https://zenvis.example.com';
    assert.equal(resolveUiTargetOrigin('/amis/page.html', appOrigin), appOrigin);
    assert.equal(
      resolveUiTargetOrigin('https://plugin.example.com/view', appOrigin),
      'https://plugin.example.com',
    );
    assert.equal(resolveUiTargetOrigin('javascript:alert(1)', appOrigin), null);
    assert.equal(resolveUiTargetOrigin('data:text/html,hello', appOrigin), null);
    assert.equal(resolveUiTargetOrigin('', appOrigin), null);
  });

  test('accepts only compatible ready handshakes', () => {
    assert.equal(isUiThemeReadyPayload({ type: 'zenvis:ui:ready' }), true);
    assert.equal(
      isUiThemeReadyPayload({
        type: 'zenvis:ui:ready',
        contractVersion: UI_THEME_CONTRACT_VERSION,
      }),
      true,
    );
    assert.equal(
      isUiThemeReadyPayload({ type: 'zenvis:ui:ready', contractVersion: '2.0.0' }),
      false,
    );
    assert.equal(isUiThemeReadyPayload({ type: 'zenvis:ui' }), false);

    const frameWindow = {};
    const event = {
      source: frameWindow,
      origin: 'https://plugin.example.com',
      data: { type: 'zenvis:ui:ready', contractVersion: UI_THEME_CONTRACT_VERSION },
    };
    assert.equal(isUiThemeReadyEvent(event, frameWindow, 'https://plugin.example.com'), true);
    assert.equal(isUiThemeReadyEvent(event, {}, 'https://plugin.example.com'), false);
    assert.equal(isUiThemeReadyEvent(event, frameWindow, 'https://evil.example.com'), false);
  });

  test('keeps the CSS custom properties aligned with public token values', async () => {
    const cssPath = fileURLToPath(new URL('../assets/styles/tokens.scss', import.meta.url));
    const css = await readFile(cssPath, 'utf8');
    const requiredValues = [
      ...Object.values(uiThemeTokens).filter(value => value.startsWith('#')),
      ...uiChartPalette,
    ];

    for (const value of new Set(requiredValues)) {
      assert.match(css, new RegExp(value.replace('#', '\\#'), 'i'));
    }

    assert.match(css, /--el-color-primary:\s*var\(--zv-primary\)/);
    assert.match(css, /--el-text-color-primary:\s*var\(--zv-text-primary\)/);
    assert.match(css, /--el-border-radius-base:\s*var\(--zv-radius-sm\)/);
  });
});
