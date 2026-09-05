import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of } from 'rxjs';
import { PortfolioAnalysisView, RequestedPortfolioAnalysisView } from './portfolio-analysis.models';

/**
 * Talks to the FD005 endpoints `GET /api/portfolios/{id}/analysis/latest` and
 * `POST /api/portfolios/{id}/analysis` (see the OpenAPI contract). `'not-found'` on a `404`
 * (unknown id or not the current investor's); `'conflict'` on a `409` (US4 — an analysis is
 * already `PENDING`/`RUNNING`, `requestNew` only); `null` on any other recoverable HTTP error — the
 * caller then shows an "unavailable" state, never a synthesized analysis.
 */
@Injectable({ providedIn: 'root' })
export class PortfolioAnalysisService {
  private readonly http = inject(HttpClient);

  getLatest(id: string): Observable<PortfolioAnalysisView | 'not-found' | null> {
    return this.http.get<PortfolioAnalysisView>(`/api/portfolios/${id}/analysis/latest`).pipe(
      catchError((err: HttpErrorResponse) =>
        of(err.status === 404 ? ('not-found' as const) : null),
      ),
    );
  }

  /** US4 — "Run analysis again". Never sent while the latest is PENDING/RUNNING (caller's job). */
  requestNew(
    id: string,
  ): Observable<RequestedPortfolioAnalysisView | 'not-found' | 'conflict' | null> {
    return this.http.post<RequestedPortfolioAnalysisView>(`/api/portfolios/${id}/analysis`, null).pipe(
      catchError((err: HttpErrorResponse) => {
        if (err.status === 404) {
          return of('not-found' as const);
        }
        if (err.status === 409) {
          return of('conflict' as const);
        }
        return of(null);
      }),
    );
  }
}
