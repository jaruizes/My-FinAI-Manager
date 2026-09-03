import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of } from 'rxjs';
import { CatalogListing } from './instrument.models';

/**
 * Talks to `GET /api/financial-instruments` (EN004 — see the OpenAPI contract). Search runs against
 * the platform's own catalog; the frontend never calls an external reference-data provider
 * (FD002 FR-003 / BR-008).
 *
 * The service only shapes the request and the response. Debouncing, min-length gating, and
 * cancelling in-flight requests are the caller's concern (the Add Position dialog) — but a
 * blank/whitespace query is refused here too, because EN004 answers a blank `query` with `400`.
 */
@Injectable({ providedIn: 'root' })
export class InstrumentSearchService {
  private readonly http = inject(HttpClient);

  /**
   * @param query raw user input; trimmed here. A blank query yields an empty result without a
   *        network call. Any HTTP failure yields `null` so the caller can render a recoverable
   *        error state (never a synthesized listing).
   */
  search(query: string): Observable<CatalogListing[] | null> {
    const q = (query ?? '').trim();
    if (q.length === 0) {
      return of([]);
    }
    return this.http
      .get<CatalogListing[]>('/api/financial-instruments', { params: { query: q } })
      .pipe(
        map((listings) => listings ?? []),
        catchError(() => of(null)),
      );
  }
}
