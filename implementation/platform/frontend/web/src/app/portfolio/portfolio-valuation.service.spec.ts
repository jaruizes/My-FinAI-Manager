import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PortfolioValuationService } from './portfolio-valuation.service';
import { PortfolioValuationView } from './portfolio.models';

describe('PortfolioValuationService', () => {
  let service: PortfolioValuationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PortfolioValuationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('returns the valuation body on 200', () => {
    const body: PortfolioValuationView = {
      portfolioId: 'p1',
      status: 'PARTIAL',
      calculatedAt: '2026-09-04T12:00:00Z',
      totalValueEUR: '2100',
      totalValueUSD: null,
      marketDataAsOf: null,
      fxDataAsOf: null,
      positions: [],
      sectors: [],
    };
    let result: unknown;
    service.getValuation('p1').subscribe((r) => (result = r));

    const req = http.expectOne('/api/portfolios/p1/valuation');
    expect(req.request.method).toBe('GET');
    req.flush(body);

    expect(result).toEqual(body);
  });

  it('maps a 404 to "not-found"', () => {
    let result: unknown;
    service.getValuation('missing').subscribe((r) => (result = r));
    http
      .expectOne('/api/portfolios/missing/valuation')
      .flush(null, { status: 404, statusText: 'Not Found' });
    expect(result).toBe('not-found');
  });

  it('maps any other error to null', () => {
    let result: unknown = 'unset';
    service.getValuation('p1').subscribe((r) => (result = r));
    http
      .expectOne('/api/portfolios/p1/valuation')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    expect(result).toBeNull();
  });

  it('does not throw for an unexpected HttpErrorResponse shape', () => {
    let result: unknown = 'unset';
    service.getValuation('p1').subscribe((r) => (result = r));
    const req = http.expectOne('/api/portfolios/p1/valuation');
    req.error(new ProgressEvent('network error') as unknown as ErrorEvent);
    expect(result).toBeNull();
  });
});
