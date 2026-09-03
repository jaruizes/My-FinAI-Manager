import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/**
 * Persistent left navigation.
 *
 * Per `product/ux/design-system.md`, the sidebar MUST NOT expose navigation for capabilities that
 * do not exist yet. FD001 adds the first product capability — creating a portfolio — so the
 * "Portfolios" entry appears now.
 */
@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <aside class="sidebar">
      <div class="sidebar__brand">My-FinAI-Manager</div>
      <nav class="sidebar__nav" aria-label="Primary">
        <a
          class="sidebar__link"
          routerLink="/portfolios/new"
          routerLinkActive="sidebar__link--active"
        >
          Portfolios
        </a>
      </nav>
    </aside>
  `,
  styles: [
    `
      .sidebar {
        width: var(--sidebar-width);
        min-width: var(--sidebar-width);
        height: 100%;
        background: var(--color-sidebar);
        border-right: 1px solid var(--color-border);
        display: flex;
        flex-direction: column;
        padding: var(--spacing-md);
        box-sizing: border-box;
      }
      .sidebar__brand {
        font-weight: var(--font-weight-semibold);
        color: var(--color-text-primary);
        margin-bottom: var(--spacing-lg);
      }
      .sidebar__nav {
        flex: 1;
        display: flex;
        flex-direction: column;
        gap: var(--spacing-xs);
      }
      .sidebar__link {
        display: block;
        padding: var(--spacing-sm) var(--spacing-md);
        border-radius: var(--radius-sm);
        color: var(--color-text-secondary);
        text-decoration: none;
        font-size: var(--font-size-body);
      }
      .sidebar__link:hover {
        background: var(--color-surface);
        color: var(--color-text-primary);
      }
      .sidebar__link--active {
        background: var(--color-surface-elevated);
        color: var(--color-text-primary);
      }
    `,
  ],
})
export class SidebarComponent {}
