import { NgIf } from '@angular/common';
import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { PositionDraft } from './portfolio-creation.models';

/**
 * Focused dialog for adding (or editing) one draft position (FD001 US4/US5, design-system
 * "focused dialog"). Client-side checks are format/required only — the backend is authoritative
 * for the business rules (AR-013). Emits a {@link PositionDraft} on confirm.
 */
@Component({
  selector: 'app-add-position-dialog',
  standalone: true,
  imports: [ReactiveFormsModule, NgIf],
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

        <form [formGroup]="form" (ngSubmit)="confirm()">
          <label class="field">
            <span class="field__label">Ticker</span>
            <input formControlName="ticker" autocomplete="off" />
          </label>

          <label class="field">
            <span class="field__label">Market</span>
            <input formControlName="market" autocomplete="off" />
          </label>

          <label class="field">
            <span class="field__label">Quantity</span>
            <input formControlName="quantity" inputmode="decimal" />
            <span class="field__hint" *ngIf="showError('quantity')">Enter a number.</span>
          </label>

          <label class="field">
            <span class="field__label">Currency</span>
            <input formControlName="currency" maxlength="3" />
            <span class="field__hint" *ngIf="showError('currency')">Use a 3-letter code, e.g. EUR.</span>
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
            <button type="submit" class="btn btn--primary" [disabled]="form.invalid">
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
        width: min(420px, calc(100vw - 2 * var(--spacing-lg)));
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
      .field input {
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-sm);
        padding: var(--spacing-sm);
        color: var(--color-text-primary);
        font-size: var(--font-size-body);
      }
      .field__hint {
        font-size: var(--font-size-caption);
        color: var(--color-warning);
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
  /** When set, the dialog opens pre-filled and confirms as an edit (US5). */
  @Input() initial: PositionDraft | null = null;

  @Output() readonly confirmed = new EventEmitter<PositionDraft>();
  @Output() readonly cancelled = new EventEmitter<void>();

  private readonly fb = new FormBuilder();

  form: FormGroup = this.fb.group({
    ticker: ['', [Validators.required]],
    market: ['', [Validators.required]],
    quantity: ['', [Validators.required, Validators.pattern(/^-?\d+(\.\d+)?$/)]],
    currency: ['', [Validators.required, Validators.pattern(/^[A-Za-z]{3}$/)]],
    initialPurchaseDate: [''],
    averagePurchasePrice: ['', [Validators.pattern(/^-?\d+(\.\d+)?$/)]],
  });

  get editing(): boolean {
    return this.initial !== null;
  }

  ngOnInit(): void {
    if (this.initial) {
      this.form.patchValue({
        ticker: this.initial.ticker,
        market: this.initial.market,
        quantity: this.initial.quantity,
        currency: this.initial.currency,
        initialPurchaseDate: this.initial.initialPurchaseDate ?? '',
        averagePurchasePrice: this.initial.averagePurchasePrice ?? '',
      });
    }
  }

  currencyLabel(): string {
    const value = (this.form.value.currency ?? '').toString().toUpperCase();
    return value.length === 3 ? value : 'the position currency';
  }

  showError(control: string): boolean {
    const c = this.form.get(control);
    return !!c && c.invalid && (c.dirty || c.touched);
  }

  confirm(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.value as Record<string, string>;
    const draft: PositionDraft = {
      ticker: raw['ticker'].trim().toUpperCase(),
      market: raw['market'].trim().toUpperCase(),
      quantity: raw['quantity'].trim(),
      currency: raw['currency'].trim().toUpperCase(),
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
