import { NgFor, NgIf } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PortfolioQueryService } from './portfolio-query.service';
import { DetailState } from './portfolio.models';

/**
 * A single portfolio's detail view (FD003 US3): the portfolio name and a read-only table of its
 * positions — ticker, market, quantity, currency always; initial purchase date and average purchase
 * price only when the position has them. Values are shown exactly as received (no reformatting —
 * SC-004). Loads by the route `:id`; a `404` shows a "not found" state with a link back to Home.
 * There is deliberately no control to edit, add, or remove a position (FR-012 / BR-009).
 */
@Component({
  selector: 'app-portfolio-detail-page',
  standalone: true,
  imports: [NgIf, NgFor, RouterLink],
  template: `
    <section class="page">
      <a class="back" routerLink="/">← All portfolios</a>

      <p class="state" *ngIf="state().kind === 'loading'" role="status">Loading portfolio…</p>

      <p class="state state--error" *ngIf="state().kind === 'error'" role="alert">
        We couldn't load this portfolio right now.
        <button type="button" class="link" (click)="load()">Retry</button>
      </p>

      <div class="state" *ngIf="state().kind === 'not-found'">
        <p>This portfolio doesn't exist.</p>
        <a class="link" routerLink="/">Back to your portfolios</a>
      </div>

      <ng-container *ngIf="portfolio() as p">
        <h1 class="page__title">{{ p.name }}</h1>

        <table class="list">
          <thead>
            <tr>
              <th scope="col">Ticker</th>
              <th scope="col">Market</th>
              <th scope="col" class="list__num">Quantity</th>
              <th scope="col">Currency</th>
              <th scope="col">Initial purchase date</th>
              <th scope="col" class="list__num">Average purchase price</th>
            </tr>
          </thead>
          <tbody>
            <tr *ngFor="let pos of p.positions">
              <td>{{ pos.ticker }}</td>
              <td>{{ pos.market }}</td>
              <td class="list__num">{{ pos.quantity }}</td>
              <td>{{ pos.currency }}</td>
              <td>{{ pos.initialPurchaseDate ?? '—' }}</td>
              <td class="list__num">{{ pos.averagePurchasePrice ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
      </ng-container>
    </section>
  `,
  styles: [
    `
      .page {
        padding: var(--spacing-lg);
        max-width: var(--content-max-width, 960px);
      }
      .back {
        display: inline-block;
        margin-bottom: var(--spacing-md);
        color: var(--color-text-secondary);
        text-decoration: none;
        font-size: var(--font-size-body);
      }
      .back:hover {
        color: var(--color-text-primary);
      }
      .page__title {
        font-size: var(--font-size-page-title);
        margin: 0 0 var(--spacing-lg);
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
export class PortfolioDetailPageComponent implements OnInit {
  private readonly state_ = signal<DetailState>({ kind: 'loading' });
  readonly state = this.state_.asReadonly();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly api: PortfolioQueryService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.state_.set({ kind: 'loading' });
    this.api.getById(id).subscribe((result) => {
      if (result === 'not-found') {
        this.state_.set({ kind: 'not-found' });
      } else if (result === null) {
        this.state_.set({ kind: 'error' });
      } else {
        this.state_.set({ kind: 'loaded', portfolio: result });
      }
    });
  }

  /** The loaded portfolio, or `null` in any non-loaded state. */
  portfolio() {
    const s = this.state();
    return s.kind === 'loaded' ? s.portfolio : null;
  }
}
