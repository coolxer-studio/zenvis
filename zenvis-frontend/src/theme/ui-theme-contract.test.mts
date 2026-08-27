import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { describe, test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { uiChartPalette, uiThemeTokens } from './design-tokens.ts';
import {
  buildUiThemeContractV1,
  isUiThemeReadyEvent,
  isUiThemeReadyPayload,
  resolveUiNavigationEvent,
  resolveUiNavigationPayload,
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

  test('accepts only same-frame internal navigation messages', () => {
    assert.equal(
      resolveUiNavigationPayload({
        type: 'zenvis:navigate',
        to: '/service/low-code-app/com.coolxer.plugin.onesoc.app?page=%2Fasset%2Foverview',
      }),
      '/service/low-code-app/com.coolxer.plugin.onesoc.app?page=%2Fasset%2Foverview',
    );
    assert.equal(
      resolveUiNavigationPayload({ type: 'zenvis:navigate', to: '//evil.example.com' }),
      null,
    );
    assert.equal(
      resolveUiNavigationPayload({ type: 'zenvis:navigate', to: '/service\\evil' }),
      null,
    );
    assert.equal(resolveUiNavigationPayload({ type: 'zenvis:navigate', to: 'javascript:alert(1)' }), null);
    assert.equal(resolveUiNavigationPayload({ type: 'different', to: '/dashboard/index' }), null);

    const frameWindow = {};
    const event = {
      source: frameWindow,
      origin: 'https://zenvis.example.com',
      data: { type: 'zenvis:navigate', to: '/service/low-code-page/plugin.detail?id=1' },
    };
    assert.equal(
      resolveUiNavigationEvent(event, frameWindow, 'https://zenvis.example.com'),
      '/service/low-code-page/plugin.detail?id=1',
    );
    assert.equal(resolveUiNavigationEvent(event, {}, 'https://zenvis.example.com'), null);
    assert.equal(resolveUiNavigationEvent(event, frameWindow, 'https://evil.example.com'), null);
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
