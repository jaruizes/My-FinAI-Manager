import { NgFor, NgIf } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PieChartComponent } from './pie-chart.component';
import { PortfolioQueryService } from './portfolio-query.service';
import { PortfolioValuationService } from './portfolio-valuation.service';
import {
  AllocationSlice,
  DetailState,
  PortfolioValuationView,
  PositionValuationView,
} from './portfolio.models';
import { decimal2, money, percent } from './valuation-format';

type ValuationState = 'loading' | 'unavailable' | PortfolioValuationView;

/**
 * A single portfolio's detail view (FD003 US3 + FD004 US5): the portfolio name, a read-only table
 * of its positions, and — layered on top (FD004) — the total value in EUR and USD, per-position
 * market price (with its native currency) / EUR value / USD value / portfolio weight (each shown
 * only when the position could be valued), the two mandatory allocation pie charts, and an explicit
 * valuation-state line. Sector percentages live in the Allocation by Sector chart legend, not a
 * separate list (FR-028). Values are formatted to 2 decimals (FR-031); a missing valuation figure
 * is `—`, never `0` (FR-019). There is deliberately no control to edit any value (FR-012 / FR-032).
 */
@Component({
  selector: 'app-portfolio-detail-page',
  standalone: true,
  imports: [NgIf, NgFor, RouterLink, PieChartComponent],
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

        <div class="valuation" *ngIf="valuationReady()">
          <div class="totals" *ngIf="hasTotals()">
            <div class="totals__item">
              <span class="totals__label">Total value (EUR)</span>
              <span class="totals__value">{{ totalEUR() }}</span>
            </div>
            <div class="totals__item">
              <span class="totals__label">Total value (USD)</span>
              <span class="totals__value">{{ totalUSD() }}</span>
            </div>
          </div>
          <p class="valuation__state" role="status">{{ valuationStateText() }}</p>
        </div>
        <p class="valuation__state" *ngIf="!valuationReady()" role="status">
          {{ valuationStateText() }}
        </p>

        <div class="charts" *ngIf="showCharts()">
          <app-pie-chart [slices]="tickerSlices()" title="Allocation by Ticker"></app-pie-chart>
          <app-pie-chart [slices]="sectorSlices()" title="Allocation by Sector"></app-pie-chart>
        </div>

        <table class="list">
          <thead>
            <tr>
              <th scope="col">Ticker</th>
              <th scope="col">Market</th>
              <th scope="col" class="list__num">Quantity</th>
              <th scope="col">Currency</th>
              <th scope="col">Initial purchase date</th>
              <th scope="col" class="list__num">Average purchase price</th>
              <ng-container *ngIf="showValuationColumns()">
                <th scope="col" class="list__num">Market price</th>
                <th scope="col" class="list__num">Value (EUR)</th>
                <th scope="col" class="list__num">Value (USD)</th>
                <th scope="col" class="list__num">Weight</th>
                <th scope="col">Sector</th>
              </ng-container>
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
              <ng-container *ngIf="showValuationColumns()">
                <td class="list__num">{{ priceOf(pos.ticker, pos.market) }}</td>
                <td class="list__num">{{ eurValueOf(pos.ticker, pos.market) }}</td>
                <td class="list__num">{{ usdValueOf(pos.ticker, pos.market) }}</td>
                <td class="list__num">{{ weightOf(pos.ticker, pos.market) }}</td>
                <td>{{ sectorOf(pos.ticker, pos.market) }}</td>
              </ng-container>
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
      .totals {
        display: flex;
        gap: var(--spacing-lg);
        margin-bottom: var(--spacing-sm);
      }
      .totals__item {
        display: flex;
        flex-direction: column;
      }
      .totals__label {
        color: var(--color-text-secondary);
        font-size: var(--font-size-caption, 0.85rem);
      }
      .totals__value {
        font-size: var(--font-size-section-title, 1.25rem);
        font-variant-numeric: tabular-nums;
        color: var(--color-text-primary);
      }
      .valuation__state {
        color: var(--color-text-secondary);
        font-size: var(--font-size-body);
        margin: 0 0 var(--spacing-lg);
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
      .charts {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
        gap: var(--spacing-md);
        margin-bottom: var(--spacing-lg);
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

  private readonly valuation_ = signal<ValuationState>('loading');
  readonly valuation = this.valuation_.asReadonly();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly api: PortfolioQueryService,
    private readonly valuationApi: PortfolioValuationService,
  ) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.state_.set({ kind: 'loading' });
    this.valuation_.set('loading');
    this.api.getById(id).subscribe((result) => {
      if (result === 'not-found') {
        this.state_.set({ kind: 'not-found' });
      } else if (result === null) {
        this.state_.set({ kind: 'error' });
      } else {
        this.state_.set({ kind: 'loaded', portfolio: result });
        this.loadValuation(id);
      }
    });
  }

  private loadValuation(id: string): void {
    this.valuationApi.getValuation(id).subscribe((result) => {
      this.valuation_.set(
        result === null || result === 'not-found' ? 'unavailable' : result,
      );
    });
  }

  /** The loaded portfolio, or `null` in any non-loaded state. */
  portfolio() {
    const s = this.state();
    return s.kind === 'loaded' ? s.portfolio : null;
  }

  private currentValuation(): PortfolioValuationView | null {
    const v = this.valuation();
    return v === 'loading' || v === 'unavailable' ? null : v;
  }

  valuationReady(): boolean {
    const v = this.currentValuation();
    return v !== null && v.status !== 'PENDING';
  }

  showValuationColumns(): boolean {
    const v = this.currentValuation();
    return v !== null && (v.status === 'COMPLETED' || v.status === 'PARTIAL');
  }

  hasTotals(): boolean {
    const v = this.currentValuation();
    return !!v && (v.totalValueEUR !== null || v.totalValueUSD !== null);
  }

  totalEUR(): string {
    return money(this.currentValuation()?.totalValueEUR ?? null, 'EUR');
  }

  totalUSD(): string {
    return money(this.currentValuation()?.totalValueUSD ?? null, 'USD');
  }

  sectors() {
    return this.currentValuation()?.sectors ?? [];
  }

  /* ---- FD004 §17 — the two mandatory allocation pie charts ---- */

  /** One slice per valued Position, sized by the backend normalised-EUR weight (BR-014/BR-016). */
  tickerSlices(): AllocationSlice[] {
    return (this.currentValuation()?.positions ?? [])
      .filter((p) => p.valued && p.portfolioWeight !== null)
      .map((p) => ({ label: p.ticker, fraction: Number(p.portfolioWeight) }))
      .sort((a, b) => b.fraction - a.fraction);
  }

  /** One slice per sector, sized by the backend `sectorWeight` (BR-015/BR-016). */
  sectorSlices(): AllocationSlice[] {
    return this.sectors()
      .map((s) => ({ label: s.sector, fraction: Number(s.sectorWeight) }))
      .sort((a, b) => b.fraction - a.fraction);
  }

  /**
   * Both charts render iff there is a valued snapshot with a positive EUR basis. Otherwise
   * neither is drawn — the valuation-state line is the sole allocation signal (FR-048).
   */
  showCharts(): boolean {
    const v = this.currentValuation();
    return (
      v !== null &&
      (v.status === 'COMPLETED' || v.status === 'PARTIAL') &&
      v.totalValueEUR !== null &&
      Number(v.totalValueEUR) > 0 &&
      this.tickerSlices().length > 0
    );
  }

  valuationStateText(): string {
    const v = this.valuation();
    if (v === 'loading') {
      return 'Loading valuation…';
    }
    if (v === 'unavailable') {
      return 'Valuation unavailable';
    }
    switch (v.status) {
      case 'PENDING':
        return 'Valuation pending';
      case 'COMPLETED':
        return `Valued at ${this.formatWhen(v.calculatedAt)}`;
      case 'PARTIAL': {
        const missing = v.positions.filter((p) => !p.valued).length;
        return missing > 0
          ? `Partial valuation — market data unavailable for ${missing} position${missing === 1 ? '' : 's'}`
          : 'Partial valuation — some market data was unavailable';
      }
      case 'FAILED':
      default:
        return 'Valuation unavailable';
    }
  }

  private matching(ticker: string, market: string): PositionValuationView | undefined {
    return this.currentValuation()?.positions.find(
      (p) => p.ticker === ticker && p.market === market,
    );
  }

  /** Market price shown with the Position's native currency, e.g. `200.00 USD` (FR-029). */
  priceOf(ticker: string, market: string): string {
    const p = this.matching(ticker, market);
    if (!p || p.marketPrice === null) {
      return decimal2(null);
    }
    return `${decimal2(p.marketPrice)} ${p.nativeCurrency}`;
  }

  eurValueOf(ticker: string, market: string): string {
    return money(this.matching(ticker, market)?.valueInEUR ?? null, 'EUR');
  }

  usdValueOf(ticker: string, market: string): string {
    return money(this.matching(ticker, market)?.valueInUSD ?? null, 'USD');
  }

  weightOf(ticker: string, market: string): string {
    return percent(this.matching(ticker, market)?.portfolioWeight ?? null);
  }

  sectorOf(ticker: string, market: string): string {
    return this.matching(ticker, market)?.sector ?? '—';
  }

  private formatWhen(iso: string | null): string {
    if (!iso) {
      return 'an unknown time';
    }
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? iso : d.toLocaleString();
  }
}
