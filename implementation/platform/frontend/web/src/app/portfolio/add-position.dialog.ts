import { NgFor, NgIf } from '@angular/common';
import {
  Component,
  DestroyRef,
  EventEmitter,
  Input,
  OnInit,
  Output,
  inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs';
import { InstrumentSearchService } from './instrument-search.service';
import { CatalogListing, InstrumentSearchState } from './instrument.models';
import { PositionDraft } from './portfolio-creation.models';

/** Group two listings under "the same instrument" (FD002 FR-007 / research D7 / OD-FD002-2). */
function normName(name: string): string {
  return name.trim().toUpperCase();
}

/**
 * Focused dialog for adding (or editing) one draft position.
 *
 * FD002 — the ticker / market / currency of a Position are no longer free text: the Investor
 * searches the platform catalog (`GET /api/financial-instruments`, never an external provider),
 * selects an instrument, and — when that instrument has more than one supported listing — picks the
 * specific listing from a selector constrained to real catalogued combinations. Quantity / dates /
 * price are unchanged FD001 fields. The backend still re-validates every position (FR-011).
 *
 * Emits a {@link PositionDraft} on confirm; its wire fields are identical to FD001.
 */
@Component({
  selector: 'app-add-position-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, NgIf, NgFor],
  template: `
    <div class="backdrop" (click)="cancel()">
      <div
        class="dialog"
        role="dialog"
        aria-modal="true"
        [attr.aria-label]="editing ? 'Edit position' : 'Add position'"
        (click)="$event.stopPropagation()"
      >
        <h2 class="dialog__title">{{ editing ? 'Edit position' : 'Add position' }}</h2>

        <!-- Instrument: search + select -->
        <div class="field" *ngIf="!selected">
          <label class="field__label" [attr.for]="'ap-instrument'">Instrument</label>
          <input
            id="ap-instrument"
            role="combobox"
            aria-autocomplete="list"
            [attr.aria-expanded]="isOpen"
            aria-controls="ap-listbox"
            [attr.aria-activedescendant]="activeOptionId"
            autocomplete="off"
            placeholder="Search by ticker or company name"
            [formControl]="searchControl"
            (keydown)="onSearchKeydown($event)"
          />

          <p class="hint" *ngIf="state.kind === 'idle'">Search by ticker or company name.</p>
          <p class="hint" *ngIf="state.kind === 'searching'" role="status">Searching…</p>
          <p class="empty" *ngIf="state.kind === 'no-results'">
            No matching instrument found.
          </p>
          <p class="error" *ngIf="state.kind === 'error'" role="alert">
            We couldn’t search right now.
            <button type="button" class="link" (click)="retry()">Retry</button>
          </p>

          <ul
            id="ap-listbox"
            role="listbox"
            aria-label="Instrument search results"
            class="results"
            *ngIf="state.kind === 'results'"
          >
            <li
              *ngFor="let l of state.listings; let i = index"
              [id]="'ap-opt-' + i"
              role="option"
              [attr.aria-selected]="i === activeIndex"
              class="results__row"
              [class.results__row--active]="i === activeIndex"
              (click)="choose(l)"
              (mouseenter)="activeIndex = i"
            >
              <span class="results__name">{{ l.name }}</span>
              <span class="results__meta">
                {{ l.ticker }} · {{ l.market }} · {{ l.currency }}<ng-container
                  *ngIf="l.isin"
                >
                  · ISIN {{ l.isin }}</ng-container
                >
              </span>
            </li>
          </ul>
        </div>

        <!-- Instrument: selected -->
        <div class="field selected" *ngIf="selected">
          <span class="field__label">Instrument</span>
          <div class="selected__row">
            <div>
              <div class="selected__name">{{ selected.name }}</div>
              <div class="selected__meta">
                {{ ticker }} · {{ market }} · {{ currency }}
              </div>
            </div>
            <button type="button" class="link" (click)="changeInstrument()">Change</button>
          </div>

          <!-- Constrained listing selector (only when the instrument has 2+ supported listings) -->
          <label class="field__label" *ngIf="listingChoices.length > 1" [attr.for]="'ap-listing'">
            Market / Currency
          </label>
          <select
            id="ap-listing"
            *ngIf="listingChoices.length > 1"
            [value]="selected.id"
            (change)="chooseListingById($any($event.target).value)"
          >
            <option *ngFor="let l of listingChoices" [value]="l.id">
              {{ l.market }} · {{ l.currency }}
            </option>
          </select>
        </div>

        <form [formGroup]="form" (ngSubmit)="confirm()">
          <label class="field">
            <span class="field__label">Quantity</span>
            <input formControlName="quantity" inputmode="decimal" />
            <span class="field__hint" *ngIf="showError('quantity')">Enter a number.</span>
          </label>

          <label class="field">
            <span class="field__label">Initial purchase date <em>(optional)</em></span>
            <input formControlName="initialPurchaseDate" type="date" />
          </label>

          <label class="field">
            <span class="field__label">
              Average purchase price <em>(optional, in {{ currencyLabel() }})</em>
            </span>
            <input formControlName="averagePurchasePrice" inputmode="decimal" />
          </label>

          <div class="dialog__actions">
            <button type="button" class="btn btn--ghost" (click)="cancel()">Cancel</button>
            <button type="submit" class="btn btn--primary" [disabled]="!canConfirm()">
              {{ editing ? 'Save changes' : 'Add position' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [
    `
      .backdrop {
        position: fixed;
        inset: 0;
        background: rgba(0, 0, 0, 0.5);
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 100;
      }
      .dialog {
        background: var(--color-surface-elevated);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-lg);
        padding: var(--spacing-lg);
        width: min(460px, calc(100vw - 2 * var(--spacing-lg)));
        color: var(--color-text-primary);
      }
      .dialog__title {
        margin: 0 0 var(--spacing-md);
        font-size: var(--font-size-section-title);
      }
      .field {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-xs);
        margin-bottom: var(--spacing-md);
      }
      .field__label {
        font-size: var(--font-size-caption);
        color: var(--color-text-secondary);
      }
      .field__label em {
        color: var(--color-text-muted);
        font-style: normal;
      }
      .field input,
      .field select {
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-sm);
        padding: var(--spacing-sm);
        color: var(--color-text-primary);
        font-size: var(--font-size-body);
      }
      .field input:focus,
      .field select:focus,
      .results__row:focus {
        outline: 2px solid var(--color-primary);
        outline-offset: 1px;
      }
      .field__hint {
        font-size: var(--font-size-caption);
        color: var(--color-warning);
      }
      .hint {
        font-size: var(--font-size-caption);
        color: var(--color-text-muted);
        margin: var(--spacing-xs) 0 0;
      }
      .empty {
        font-size: var(--font-size-caption);
        color: var(--color-text-secondary);
        margin: var(--spacing-xs) 0 0;
      }
      .error {
        font-size: var(--font-size-caption);
        color: var(--color-warning);
        margin: var(--spacing-xs) 0 0;
      }
      .results {
        list-style: none;
        margin: var(--spacing-xs) 0 0;
        padding: 0;
        border: 1px solid var(--color-border);
        border-radius: var(--radius-sm);
        max-height: 15rem;
        overflow-y: auto;
      }
      .results__row {
        display: flex;
        flex-direction: column;
        gap: 2px;
        padding: var(--spacing-sm);
        cursor: pointer;
        border-bottom: 1px solid var(--color-border);
      }
      .results__row:last-child {
        border-bottom: none;
      }
      .results__row--active {
        background: var(--color-surface);
      }
      .results__name {
        font-size: var(--font-size-body);
      }
      .results__meta {
        font-size: var(--font-size-caption);
        color: var(--color-text-secondary);
      }
      .selected__row {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: var(--spacing-sm);
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-sm);
        padding: var(--spacing-sm);
      }
      .selected__name {
        font-size: var(--font-size-body);
      }
      .selected__meta {
        font-size: var(--font-size-caption);
        color: var(--color-text-secondary);
      }
      .link {
        background: none;
        border: none;
        color: var(--color-primary);
        cursor: pointer;
        font-size: var(--font-size-caption);
        padding: 0;
      }
      .dialog__actions {
        display: flex;
        justify-content: flex-end;
        gap: var(--spacing-sm);
        margin-top: var(--spacing-md);
      }
      .btn {
        border-radius: var(--radius-sm);
        padding: var(--spacing-sm) var(--spacing-md);
        font-size: var(--font-size-body);
        cursor: pointer;
        border: 1px solid transparent;
      }
      .btn--primary {
        background: var(--color-primary);
        color: #fff;
      }
      .btn--primary:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }
      .btn--ghost {
        background: transparent;
        border-color: var(--color-border);
        color: var(--color-text-secondary);
      }
    `,
  ],
})
export class AddPositionDialogComponent implements OnInit {
  /** When set, the dialog opens pre-filled and confirms as an edit (FD001 US5). */
  @Input() initial: PositionDraft | null = null;

  @Output() readonly confirmed = new EventEmitter<PositionDraft>();
  @Output() readonly cancelled = new EventEmitter<void>();

  private readonly fb = new FormBuilder();
  private readonly search = inject(InstrumentSearchService);
  private readonly destroyRef = inject(DestroyRef);

  readonly searchControl = new FormControl('', { nonNullable: true });

  form: FormGroup = this.fb.group({
    quantity: ['', [Validators.required, Validators.pattern(/^-?\d+(\.\d+)?$/)]],
    initialPurchaseDate: [''],
    averagePurchasePrice: ['', [Validators.pattern(/^-?\d+(\.\d+)?$/)]],
  });

  state: InstrumentSearchState = { kind: 'idle' };
  activeIndex = 0;
  /** All results from the last search — used to build the constrained listing selector. */
  private lastResults: CatalogListing[] = [];
  /** The chosen instrument's listing (single) or the currently chosen one (multi). */
  selected: CatalogListing | null = null;

  private readonly retry$ = new Subject<string>();

  get editing(): boolean {
    return this.initial !== null;
  }

  get isOpen(): boolean {
    return this.state.kind === 'results';
  }

  get activeOptionId(): string | null {
    return this.state.kind === 'results' ? `ap-opt-${this.activeIndex}` : null;
  }

  get ticker(): string {
    return this.selected?.ticker ?? '';
  }

  get market(): string {
    return this.selected?.market ?? '';
  }

  get currency(): string {
    return this.selected?.currency ?? '';
  }

  /** The listings the Investor may pick between for the selected instrument (FR-007). */
  get listingChoices(): CatalogListing[] {
    if (!this.selected) {
      return [];
    }
    const same = this.lastResults.filter(
      (l) => normName(l.name) === normName(this.selected!.name),
    );
    return same.length > 1 ? same : [this.selected];
  }

  ngOnInit(): void {
    if (this.initial) {
      this.form.patchValue({
        quantity: this.initial.quantity,
        initialPurchaseDate: this.initial.initialPurchaseDate ?? '',
        averagePurchasePrice: this.initial.averagePurchasePrice ?? '',
      });
      this.selected = {
        id: `${this.initial.ticker}|${this.initial.market}`,
        name: this.initial.instrumentName ?? this.initial.ticker,
        ticker: this.initial.ticker,
        market: this.initial.market,
        currency: (this.initial.currency as 'EUR' | 'USD') ?? 'EUR',
        active: true,
      };
    }

    this.searchControl.valueChanges
      .pipe(
        map((q) => q.trim()),
        debounceTime(250),
        distinctUntilChanged(),
        map((q) => {
          if (q.length === 0) {
            this.state = { kind: 'idle' };
          } else {
            this.state = { kind: 'searching' };
          }
          return q;
        }),
        switchMap((q) => (q.length === 0 ? [null] : this.runSearch(q))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();

    this.retry$
      .pipe(
        switchMap((q) => this.runSearch(q)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  private runSearch(query: string) {
    this.state = { kind: 'searching' };
    return this.search.search(query).pipe(
      map((listings) => {
        if (listings === null) {
          this.state = { kind: 'error' };
        } else if (listings.length === 0) {
          this.lastResults = [];
          this.state = { kind: 'no-results', query };
        } else {
          this.lastResults = listings;
          this.activeIndex = 0;
          this.state = { kind: 'results', listings };
        }
        return listings;
      }),
    );
  }

  onSearchKeydown(event: KeyboardEvent): void {
    if (this.state.kind !== 'results') {
      return;
    }
    const n = this.state.listings.length;
    if (event.key === 'ArrowDown') {
      event.preventDefault();
      this.activeIndex = (this.activeIndex + 1) % n;
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.activeIndex = (this.activeIndex - 1 + n) % n;
    } else if (event.key === 'Enter') {
      event.preventDefault();
      this.choose(this.state.listings[this.activeIndex]);
    } else if (event.key === 'Escape') {
      this.state = { kind: 'idle' };
    }
  }

  choose(listing: CatalogListing): void {
    this.selected = listing;
    this.state = { kind: 'idle' };
  }

  chooseListingById(id: string): void {
    const match = this.listingChoices.find((l) => l.id === id);
    if (match) {
      this.selected = match;
    }
  }

  changeInstrument(): void {
    this.selected = null;
    this.lastResults = [];
    this.searchControl.setValue('');
    this.state = { kind: 'idle' };
  }

  retry(): void {
    const q = this.searchControl.value.trim();
    if (q.length > 0) {
      this.retry$.next(q);
    }
  }

  currencyLabel(): string {
    return this.currency || 'the position currency';
  }

  showError(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  canConfirm(): boolean {
    return this.selected !== null && this.form.valid;
  }

  confirm(): void {
    if (!this.canConfirm()) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.value as Record<string, string>;
    const draft: PositionDraft = {
      ticker: this.ticker,
      market: this.market,
      currency: this.currency,
      quantity: raw['quantity'].trim(),
      instrumentName: this.selected!.name,
    };
    if (raw['initialPurchaseDate']) {
      draft.initialPurchaseDate = raw['initialPurchaseDate'];
    }
    if (raw['averagePurchasePrice'] && raw['averagePurchasePrice'].trim()) {
      draft.averagePurchasePrice = raw['averagePurchasePrice'].trim();
    }
    this.confirmed.emit(draft);
  }

  cancel(): void {
    this.cancelled.emit();
  }
}
