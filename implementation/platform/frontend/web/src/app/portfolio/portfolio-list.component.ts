import { NgFor, NgIf } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PortfolioQueryService } from './portfolio-query.service';
import { ListState } from './portfolio.models';

/**
 * The Home page content (FD003 US1 / US2): the current investor's saved portfolios in a compact
 * table — one row per portfolio, name + position count. Each row is a keyboard-operable link to the
 * portfolio detail (`/portfolios/:id`). Renders four states per the design system:
 * loading, results, empty ("no portfolios yet" + a Create call-to-action), and a recoverable error
 * with Retry. Read-only — there is no edit / delete control anywhere.
 */
@Component({
  selector: 'app-portfolio-list',
  standalone: true,
  imports: [NgIf, NgFor, RouterLink],
  template: `
    <section class="page">
      <header class="page__header">
        <h1 class="page__title">Portfolios</h1>
        <a class="btn btn--primary" routerLink="/portfolios/new">Create portfolio</a>
      </header>

      <p class="state" *ngIf="state().kind === 'loading'" role="status">Loading portfolios…</p>

      <p class="state state--error" *ngIf="state().kind === 'error'" role="alert">
        We couldn't load your portfolios right now.
        <button type="button" class="link" (click)="load()">Retry</button>
      </p>

      <ng-container *ngIf="state().kind === 'loaded'">
        <ng-container *ngIf="portfolios().length > 0; else empty">
          <table class="list">
            <thead>
              <tr>
                <th scope="col">Name</th>
                <th scope="col" class="list__num">Positions</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let p of portfolios()">
                <td>
                  <a class="list__link" [routerLink]="['/portfolios', p.id]">{{ p.name }}</a>
                </td>
                <td class="list__num">{{ p.positionCount }}</td>
              </tr>
            </tbody>
          </table>
        </ng-container>

        <ng-template #empty>
          <div class="empty">
            <p class="empty__title">You do not have any portfolios yet.</p>
            <p class="empty__hint">Create your first portfolio to start tracking your investments.</p>
            <a class="btn btn--primary" routerLink="/portfolios/new">Create portfolio</a>
          </div>
        </ng-template>
      </ng-container>
    </section>
  `,
  styles: [
    `
      .page {
        padding: var(--spacing-lg);
        max-width: var(--content-max-width, 960px);
      }
      .page__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: var(--spacing-lg);
      }
      .page__title {
        font-size: var(--font-size-page-title);
        margin: 0;
      }
      .state {
        color: var(--color-text-secondary);
        font-size: var(--font-size-body);
      }
      .state--error {
        color: var(--color-warning);
      }
      .list {
        width: 100%;
        border-collapse: collapse;
        font-size: var(--font-size-body);
      }
      .list th,
      .list td {
        text-align: left;
        padding: var(--spacing-sm);
        border-bottom: 1px solid var(--color-border);
        color: var(--color-text-primary);
      }
      .list th {
        color: var(--color-text-secondary);
        font-weight: var(--font-weight-medium);
      }
      .list__num {
        text-align: right;
        font-variant-numeric: tabular-nums;
      }
      .list__link {
        color: var(--color-primary);
        text-decoration: none;
      }
      .list__link:hover {
        text-decoration: underline;
      }
      .list__link:focus-visible {
        outline: var(--focus-ring, 2px solid var(--color-primary));
        outline-offset: 2px;
      }
      .empty {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-sm);
        align-items: flex-start;
        color: var(--color-text-secondary);
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
        padding: var(--spacing-lg);
      }
      .empty__title {
        margin: 0;
        color: var(--color-text-primary);
        font-size: var(--font-size-section-title);
      }
      .empty__hint {
        margin: 0;
      }
      .btn {
        border-radius: var(--radius-sm);
        padding: var(--spacing-sm) var(--spacing-md);
        font-size: var(--font-size-body);
        cursor: pointer;
        border: 1px solid transparent;
        text-decoration: none;
        display: inline-block;
      }
      .btn--primary {
        background: var(--color-primary);
        color: #fff;
      }
      .link {
        background: none;
        border: none;
        color: var(--color-primary);
        cursor: pointer;
        font-size: inherit;
        text-decoration: underline;
        padding: 0 var(--spacing-xs);
      }
    `,
  ],
})
export class PortfolioListComponent implements OnInit {
  private readonly state_ = signal<ListState>({ kind: 'loading' });
  readonly state = this.state_.asReadonly();

  constructor(private readonly api: PortfolioQueryService) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.state_.set({ kind: 'loading' });
    this.api.list().subscribe((portfolios) => {
      this.state_.set(
        portfolios === null ? { kind: 'error' } : { kind: 'loaded', portfolios },
      );
    });
  }

  /** The loaded portfolios, or `[]` in any non-loaded state (keeps the template simple). */
  portfolios() {
    const s = this.state();
    return s.kind === 'loaded' ? s.portfolios : [];
  }
}
