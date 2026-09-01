import { Component } from '@angular/core';

/**
 * Persistent left navigation (EN001: structure only).
 *
 * Per `product/ux/design-system.md`, the sidebar MUST NOT expose navigation for capabilities that
 * do not exist yet. EN001 has no product capabilities, so this renders the app identity and an
 * empty navigation region. FD001 adds the first entry (e.g. "Portfolios").
 */
@Component({
  selector: 'app-sidebar',
  standalone: true,
  template: `
    <aside class="sidebar">
      <div class="sidebar__brand">My-FinAI-Manager</div>
      <nav class="sidebar__nav" aria-label="Primary">
        <!-- No navigation items yet. Feature Definitions add entries here. -->
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
      }
    `,
  ],
})
export class SidebarComponent {}
