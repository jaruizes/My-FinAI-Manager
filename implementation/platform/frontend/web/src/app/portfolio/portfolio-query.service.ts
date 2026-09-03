import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';
import { PortfolioSummary, PortfolioView } from './portfolio.models';

/**
 * Talks to the FD003 read endpoints (`GET /api/portfolios` and `GET /api/portfolios/{id}` — see the
 * OpenAPI contract). Read-only: it only shapes the request and classifies the response. A recoverable
 * HTTP failure yields `null` so the caller can render an error state with Retry (never a synthesized
 * portfolio).
 */
@Injectable({ providedIn: 'root' })
export class PortfolioQueryService {
  private readonly http = inject(HttpClient);

  /** The current investor's portfolios, newest first. `null` on a recoverable HTTP error. */
  list(): Observable<PortfolioSummary[] | null> {
    return this.http.get<PortfolioSummary[]>('/api/portfolios').pipe(
      map((summaries) => summaries ?? []),
      catchError(() => of(null)),
    );
  }

  /**
   * One portfolio with its positions. `'not-found'` on a `404` (unknown id or not the current
   * investor's); `null` on any other recoverable HTTP error.
   */
  getById(id: string): Observable<PortfolioView | 'not-found' | null> {
    return this.http.get<PortfolioView>(`/api/portfolios/${id}`).pipe(
      catchError((err: HttpErrorResponse) =>
        of(err.status === 404 ? ('not-found' as const) : null),
      ),
    );
  }
}
