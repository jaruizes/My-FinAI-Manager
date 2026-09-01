import { Component } from '@angular/core';
import { SidebarComponent } from './sidebar.component';
import { TopBarComponent } from './top-bar.component';

/**
 * Application shell: the dark three-area layout from `product/ux/design-system.md`
 * (top application bar + persistent left sidebar + main content). Structure only — projected
 * content (`<ng-content>`) comes from the router outlet. No product behaviour.
 */
@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [SidebarComponent, TopBarComponent],
  template: `
    <div class="shell">
      <app-sidebar class="shell__sidebar" />
      <div class="shell__main">
        <app-top-bar />
        <main class="shell__content">
          <ng-content />
        </main>
      </div>
    </div>
  `,
  styles: [
    `
      .shell {
        display: flex;
        height: 100vh;
        width: 100vw;
        background: var(--color-background);
        color: var(--color-text-primary);
      }
      .shell__main {
        display: flex;
        flex-direction: column;
        flex: 1;
        min-width: 0;
      }
      .shell__content {
        flex: 1;
        overflow: auto;
      }
    `,
  ],
})
export class AppShellComponent {}
