import { NgFor, NgIf } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FieldError, PositionDraft } from './portfolio-creation.models';

/**
 * Renders the positions added to the draft so far, with per-row remove / edit actions (FR-026,
 * FR-027, FR-028). Purely presentational — it emits intent and the page owns the draft state.
 */
@Component({
  selector: 'app-position-draft-list',
  standalone: true,
  imports: [NgFor, NgIf],
  template: `
    <p class="empty" *ngIf="positions.length === 0">No positions added yet.</p>

    <table class="list" *ngIf="positions.length > 0">
      <thead>
        <tr>
          <th>Ticker</th>
          <th>Market</th>
          <th>Quantity</th>
          <th>Currency</th>
          <th class="list__actions-h">Actions</th>
        </tr>
      </thead>
      <tbody>
        <tr *ngFor="let p of positions; let i = index" [attr.data-row]="i">
          <td>{{ p.ticker }}</td>
          <td>{{ p.market }}</td>
          <td>{{ p.quantity }}</td>
          <td>{{ p.currency }}</td>
          <td class="list__actions">
            <button type="button" class="link" (click)="edit.emit(i)">Edit</button>
            <button type="button" class="link link--danger" (click)="remove.emit(i)">Remove</button>
          </td>
        </tr>
      </tbody>
    </table>

    <p class="error" *ngIf="rowError">{{ rowError }}</p>
  `,
  styles: [
    `
      .empty {
        color: var(--color-text-muted);
        font-size: var(--font-size-body);
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
      .list__actions-h {
        text-align: right;
      }
      .list__actions {
        text-align: right;
        white-space: nowrap;
      }
      .link {
        background: none;
        border: none;
        color: var(--color-primary);
        cursor: pointer;
        font-size: var(--font-size-body);
        padding: 0 var(--spacing-sm);
      }
      .link--danger {
        color: var(--color-negative);
      }
      .error {
        color: var(--color-warning);
        font-size: var(--font-size-caption);
      }
    `,
  ],
})
export class PositionDraftListComponent {
  @Input() positions: PositionDraft[] = [];
  /** A form-level error mapped from a `positions` / `AT_LEAST_ONE` API violation. */
  @Input() rowError: string | null = null;

  @Output() readonly remove = new EventEmitter<number>();
  @Output() readonly edit = new EventEmitter<number>();

  /** Convenience for tests / callers that pass the raw errors list. */
  static positionsError(errors: FieldError[]): string | null {
    const match = errors.find((e) => e.field === 'positions');
    return match ? match.message : null;
  }
}
