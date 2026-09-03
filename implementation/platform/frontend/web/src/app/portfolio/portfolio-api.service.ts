import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of } from 'rxjs';
import { catchError, map } from 'rxjs/operators';
import { newIdempotencyKey } from './idempotency-key';
import {
  CreatePortfolioOutcome,
  FieldError,
  PortfolioDraft,
  PortfolioView,
} from './portfolio-creation.models';

/**
 * Talks to `POST /api/portfolios` (FD001 — see the OpenAPI contract). The backend is the single
 * source of truth for validation (AR-013); this service only shapes the request and classifies
 * the response into a {@link CreatePortfolioOutcome} the page can react to.
 */
@Injectable({ providedIn: 'root' })
export class PortfolioApiService {
  private readonly http = inject(HttpClient);

  /**
   * Create a portfolio from a draft.
   *
   * @param draft the portfolio + positions to create
   * @param idempotencyKey a value generated once per Save attempt and reused on retry (FR-031a);
   *        defaults to a fresh key when the caller does not supply one
   */
  createPortfolio(
    draft: PortfolioDraft,
    idempotencyKey: string = newIdempotencyKey(),
  ): Observable<CreatePortfolioOutcome> {
    return this.http
      .post<PortfolioView>('/api/portfolios', toRequestBody(draft), {
        headers: { 'Idempotency-Key': idempotencyKey },
      })
      .pipe(
        // 200 (replay) and 201 (created) both arrive here; the body is the Portfolio either way.
        map((portfolio): CreatePortfolioOutcome => ({ kind: 'created', portfolio, replayed: false })),
        catchError((err: HttpErrorResponse) => of(classifyError(err))),
      );
  }
}

function toRequestBody(draft: PortfolioDraft): unknown {
  return {
    name: draft.name,
    positions: draft.positions.map((p) => {
      const position: Record<string, string> = {
        ticker: p.ticker,
        market: p.market,
        quantity: p.quantity,
        currency: p.currency,
      };
      if (p.initialPurchaseDate) {
        position['initialPurchaseDate'] = p.initialPurchaseDate;
      }
      if (p.averagePurchasePrice) {
        position['averagePurchasePrice'] = p.averagePurchasePrice;
      }
      return position;
    }),
  };
}

function classifyError(err: HttpErrorResponse): CreatePortfolioOutcome {
  if (err.status === 400) {
    const errors = (err.error?.errors ?? []) as FieldError[];
    return { kind: 'invalid', errors };
  }
  // 503 (transient, keep the draft) — and any other failure is surfaced the same way so the
  // investor can retry with the same idempotency key.
  return { kind: 'not-saved' };
}
