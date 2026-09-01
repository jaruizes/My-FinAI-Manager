import { Component } from '@angular/core';

/**
 * Placeholder landing view for the default route (EN001). Deliberately empty of product content —
 * it only proves the shell renders a non-blank content area. FD001 replaces / adds real views.
 */
@Component({
  selector: 'app-home',
  standalone: true,
  template: `
    <section class="home-placeholder">
      <h1>My-FinAI-Manager</h1>
      <p>Platform baseline is running. No features are available yet.</p>
    </section>
  `,
  styles: [
    `
      .home-placeholder {
        padding: var(--spacing-lg);
        color: var(--color-text-secondary);
      }
      h1 {
        margin: 0 0 var(--spacing-sm);
        font-size: var(--font-size-page-title);
        color: var(--color-text-primary);
      }
    `,
  ],
})
export class HomeComponent {}
