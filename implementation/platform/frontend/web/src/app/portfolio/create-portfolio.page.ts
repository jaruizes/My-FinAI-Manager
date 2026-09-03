import { NgFor, NgIf } from '@angular/common';
import { Component, computed, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { AddPositionDialogComponent } from './add-position.dialog';
import { newIdempotencyKey } from './idempotency-key';
import {
  FieldError,
  PortfolioDraft,
  PositionDraft,
} from './portfolio-creation.models';
import { PortfolioApiService } from './portfolio-api.service';
import { PositionDraftListComponent } from './position-draft-list.component';

/**
 * Create Portfolio screen (FD001, US1–US5). Collects a name and one or more draft positions,
 * submits them in a single request, and surfaces the backend's validation feedback against the
 * relevant fields. The screen stays usable after a successful create (no navigation — spec A9).
 */
@Component({
  selector: 'app-create-portfolio-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    NgIf,
    NgFor,
    AddPositionDialogComponent,
    PositionDraftListComponent,
  ],
  template: `
    <section class="page">
      <h1 class="page__title">Create portfolio</h1>

      <form [formGroup]="form" (ngSubmit)="save()">
        <label class="field">
          <span class="field__label">Portfolio name</span>
          <input formControlName="name" autocomplete="off" />
          <span class="field__error" *ngFor="let m of nameErrors()">{{ m }}</span>
        </label>

        <div class="positions">
          <div class="positions__header">
            <h2 class="positions__title">Positions</h2>
            <button type="button" class="btn btn--ghost" (click)="openAddDialog()">
              Add position
            </button>
          </div>

          <app-position-draft-list
            [positions]="positions()"
            [rowError]="positionsListError()"
            (edit)="openEditDialog($event)"
            (remove)="removePosition($event)"
          />

          <ul class="positions__errors" *ngIf="positionErrors().length > 0">
            <li *ngFor="let e of positionErrors()">{{ e }}</li>
          </ul>
        </div>

        <p class="notice notice--success" *ngIf="successMessage()">{{ successMessage() }}</p>
        <p class="notice notice--warning" *ngIf="notSavedMessage()">
          {{ notSavedMessage() }}
          <button type="button" class="link" (click)="save()">Try again</button>
        </p>

        <div class="page__actions">
          <button type="submit" class="btn btn--primary" [disabled]="!canSave()">
            {{ submitting() ? 'Saving…' : 'Save' }}
          </button>
        </div>
      </form>

      <app-add-position-dialog
        *ngIf="dialogOpen()"
        [initial]="editingPosition()"
        (confirmed)="onDialogConfirmed($event)"
        (cancelled)="closeDialog()"
      />
    </section>
  `,
  styles: [
    `
      .page {
        padding: var(--spacing-lg);
        max-width: 720px;
      }
      .page__title {
        font-size: var(--font-size-page-title);
        margin: 0 0 var(--spacing-lg);
      }
      .field {
        display: flex;
        flex-direction: column;
        gap: var(--spacing-xs);
        margin-bottom: var(--spacing-lg);
      }
      .field__label {
        font-size: var(--font-size-caption);
        color: var(--color-text-secondary);
      }
      .field input {
        background: var(--color-surface);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-sm);
        padding: var(--spacing-sm);
        color: var(--color-text-primary);
        font-size: var(--font-size-body);
      }
      .field__error,
      .positions__errors {
        color: var(--color-warning);
        font-size: var(--font-size-caption);
      }
      .positions {
        margin-bottom: var(--spacing-lg);
      }
      .positions__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: var(--spacing-sm);
      }
      .positions__title {
        font-size: var(--font-size-section-title);
        margin: 0;
      }
      .positions__errors {
        margin: var(--spacing-sm) 0 0;
        padding-left: var(--spacing-md);
      }
      .notice {
        padding: var(--spacing-sm) var(--spacing-md);
        border-radius: var(--radius-sm);
        font-size: var(--font-size-body);
      }
      .notice--success {
        background: rgba(34, 197, 94, 0.12);
        color: var(--color-positive);
      }
      .notice--warning {
        background: rgba(245, 158, 11, 0.12);
        color: var(--color-warning);
      }
      .page__actions {
        margin-top: var(--spacing-lg);
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
      .link {
        background: none;
        border: none;
        color: var(--color-primary);
        cursor: pointer;
        font-size: inherit;
        text-decoration: underline;
      }
    `,
  ],
})
export class CreatePortfolioPageComponent {
  readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  /** The form's validity as a signal, so {@link canSave} stays reactive to name edits. */
  private readonly formStatus = toSignal(this.form.statusChanges, {
    initialValue: this.form.status,
  });

  readonly positions = signal<PositionDraft[]>([]);
  readonly submitting = signal(false);
  readonly successMessage = signal<string | null>(null);
  readonly notSavedMessage = signal<string | null>(null);

  private readonly apiErrors = signal<FieldError[]>([]);
  readonly dialogOpen = signal(false);
  private readonly editingIndex = signal<number | null>(null);

  /** Generated once per Save attempt and reused while that attempt is being retried (FR-031a). */
  private idempotencyKey: string | null = null;

  constructor(private readonly api: PortfolioApiService) {}

  readonly editingPosition = computed(() => {
    const i = this.editingIndex();
    return i === null ? null : (this.positions()[i] ?? null);
  });

  readonly nameErrors = computed(() =>
    this.apiErrors()
      .filter((e) => e.field === 'name')
      .map((e) => e.message),
  );

  readonly positionsListError = computed(() => {
    const match = this.apiErrors().find((e) => e.field === 'positions');
    return match ? match.message : null;
  });

  readonly positionErrors = computed(() =>
    this.apiErrors()
      .filter((e) => /^positions\[\d+]/.test(e.field))
      .map((e) => `${e.field}: ${e.message}`),
  );

  private readonly hasBlockingErrors = computed(() => this.apiErrors().length > 0);

  readonly canSave = computed(() => {
    const submitting = this.submitting();
    const positionCount = this.positions().length;
    const blocking = this.hasBlockingErrors();
    const valid = this.formStatus() === 'VALID';
    return !submitting && valid && positionCount > 0 && !blocking;
  });

  openAddDialog(): void {
    this.editingIndex.set(null);
    this.dialogOpen.set(true);
  }

  openEditDialog(index: number): void {
    this.editingIndex.set(index);
    this.dialogOpen.set(true);
  }

  closeDialog(): void {
    this.dialogOpen.set(false);
    this.editingIndex.set(null);
  }

  onDialogConfirmed(position: PositionDraft): void {
    const index = this.editingIndex();
    this.positions.update((list) => {
      const next = [...list];
      if (index === null) {
        next.push(position);
      } else {
        next[index] = position;
      }
      return next;
    });
    this.clearFeedback();
    this.closeDialog();
  }

  removePosition(index: number): void {
    this.positions.update((list) => list.filter((_, i) => i !== index));
    this.clearFeedback();
  }

  save(): void {
    if (!this.form.valid || this.positions().length === 0 || this.submitting()) {
      return;
    }
    this.successMessage.set(null);
    this.notSavedMessage.set(null);
    this.apiErrors.set([]);
    this.submitting.set(true);

    if (this.idempotencyKey === null) {
      this.idempotencyKey = newIdempotencyKey();
    }

    const draft: PortfolioDraft = {
      name: this.form.getRawValue().name.trim(),
      positions: this.positions(),
    };

    this.api.createPortfolio(draft, this.idempotencyKey).subscribe((outcome) => {
      this.submitting.set(false);
      switch (outcome.kind) {
        case 'created':
          this.idempotencyKey = null;
          this.successMessage.set('Portfolio created successfully.');
          this.resetDraft();
          break;
        case 'invalid':
          this.idempotencyKey = null;
          this.apiErrors.set(outcome.errors);
          break;
        case 'not-saved':
          // keep the draft and the idempotency key so "Try again" is a safe retry (FR-023a)
          this.notSavedMessage.set(
            "We couldn't save your portfolio right now. Please try again.",
          );
          break;
      }
    });
  }

  private clearFeedback(): void {
    this.apiErrors.set([]);
    this.notSavedMessage.set(null);
  }

  private resetDraft(): void {
    this.form.reset({ name: '' });
    this.positions.set([]);
    this.apiErrors.set([]);
  }
}
