import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of } from 'rxjs';
import { PortfolioValuationView } from './portfolio.models';

/**
 * Talks to the FD004 read endpoint `GET /api/portfolios/{id}/valuation` (see the OpenAPI contract).
 * Read-only: it only shapes the request and classifies the response. `'not-found'` on a `404`
 * (unknown id or not the current investor's); `null` on any other recoverable HTTP error — the
 * caller then shows "Valuation unavailable" (never a synthesized valuation).
 */
@Injectable({ providedIn: 'root' })
export class PortfolioValuationService {
  private readonly http = inject(HttpClient);

  getValuation(id: string): Observable<PortfolioValuationView | 'not-found' | null> {
    return this.http.get<PortfolioValuationView>(`/api/portfolios/${id}/valuation`).pipe(
      catchError((err: HttpErrorResponse) =>
        of(err.status === 404 ? ('not-found' as const) : null),
      ),
    );
  }
}
