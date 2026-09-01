import { Component } from '@angular/core';

/**
 * Top application bar (EN001: structure only). Secondary/global actions land here as features
 * introduce them (search, notifications, user menu). Empty for now.
 */
@Component({
  selector: 'app-top-bar',
  standalone: true,
  template: `
    <header class="top-bar">
      <span class="top-bar__title">Platform</span>
      <span class="top-bar__spacer"></span>
    </header>
  `,
  styles: [
    `
      .top-bar {
        height: var(--topbar-height);
        min-height: var(--topbar-height);
        background: var(--color-surface);
        border-bottom: 1px solid var(--color-border);
        display: flex;
        align-items: center;
        padding: 0 var(--spacing-lg);
        box-sizing: border-box;
      }
      .top-bar__title {
        color: var(--color-text-secondary);
        font-size: var(--font-size-body);
      }
      .top-bar__spacer {
        flex: 1;
      }
    `,
  ],
})
export class TopBarComponent {}
