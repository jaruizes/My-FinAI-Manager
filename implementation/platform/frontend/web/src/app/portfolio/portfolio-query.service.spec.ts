import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PortfolioQueryService } from './portfolio-query.service';
import { PortfolioSummary, PortfolioView } from './portfolio.models';

describe('PortfolioQueryService', () => {
  let service: PortfolioQueryService;
  let http: HttpTestingController;

  const summaries: PortfolioSummary[] = [
    { id: 'p2', name: 'Newer', positionCount: 3 },
    { id: 'p1', name: 'Older', positionCount: 1 },
  ];

  const detail: PortfolioView = {
    id: 'p1',
    name: 'Older',
    status: 'ACTIVE',
    positions: [
      {
        id: 'x1',
        ticker: 'ASML',
        market: 'XAMS',
        quantity: '3',
        currency: 'EUR',
        initialPurchaseDate: null,
        averagePurchasePrice: null,
      },
    ],
    createdAt: '2026-09-01T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PortfolioQueryService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GETs /api/portfolios and returns the summaries in the order received', () => {
    let result: PortfolioSummary[] | null | undefined;
    service.list().subscribe((r) => (result = r));

    const req = http.expectOne('/api/portfolios');
    expect(req.request.method).toBe('GET');
    req.flush(summaries);

    expect(result).toEqual(summaries);
  });

  it('treats a null/absent list body as an empty array', () => {
    let result: PortfolioSummary[] | null | undefined;
    service.list().subscribe((r) => (result = r));
    http.expectOne('/api/portfolios').flush(null);
    expect(result).toEqual([]);
  });

  it('yields null when the list request fails', () => {
    let result: PortfolioSummary[] | null | undefined = undefined;
    service.list().subscribe((r) => (result = r));
    http
      .expectOne('/api/portfolios')
      .flush('boom', { status: 503, statusText: 'Service Unavailable' });
    expect(result).toBeNull();
  });

  it('GETs /api/portfolios/:id and returns the portfolio detail', () => {
    let result: PortfolioView | 'not-found' | null | undefined;
    service.getById('p1').subscribe((r) => (result = r));

    const req = http.expectOne('/api/portfolios/p1');
    expect(req.request.method).toBe('GET');
    req.flush(detail);

    expect(result).toEqual(detail);
  });

  it('maps a 404 on the detail request to "not-found"', () => {
    let result: PortfolioView | 'not-found' | null | undefined;
    service.getById('missing').subscribe((r) => (result = r));

    http.expectOne('/api/portfolios/missing').flush(
      { type: '/problems/portfolio-not-found', title: 'Portfolio not found', status: 404 },
      { status: 404, statusText: 'Not Found' },
    );

    expect(result).toBe('not-found');
  });

  it('maps any other detail error to null', () => {
    let result: PortfolioView | 'not-found' | null | undefined = undefined;
    service.getById('p1').subscribe((r) => (result = r));

    http
      .expectOne('/api/portfolios/p1')
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(result).toBeNull();
  });
});
