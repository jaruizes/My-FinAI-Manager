import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { PortfolioApiService } from './portfolio-api.service';
import { PortfolioDraft } from './portfolio-creation.models';

describe('PortfolioApiService', () => {
  let service: PortfolioApiService;
  let http: HttpTestingController;

  const draft: PortfolioDraft = {
    name: 'Long-Term Growth',
    positions: [{ ticker: 'ASML', market: 'XAMS', quantity: '12', currency: 'EUR' }],
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PortfolioApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('POSTs to /api/portfolios with an Idempotency-Key header and returns the created portfolio', () => {
    let outcome: unknown;
    service.createPortfolio(draft, 'key-123').subscribe((o) => (outcome = o));

    const req = http.expectOne('/api/portfolios');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-123');
    expect(req.request.body.positions[0].ticker).toBe('ASML');
    expect('initialPurchaseDate' in req.request.body.positions[0]).toBeFalse();

    req.flush({ id: 'p1', name: 'Long-Term Growth', status: 'ACTIVE', positions: [], createdAt: 'now' });
    expect(outcome).toEqual(
      jasmine.objectContaining({ kind: 'created' }) as unknown as object,
    );
  });

  it('generates an Idempotency-Key when the caller does not supply one', () => {
    service.createPortfolio(draft).subscribe();
    const req = http.expectOne('/api/portfolios');
    expect(req.request.headers.get('Idempotency-Key')).toMatch(/[0-9a-f-]{36}/);
    req.flush({ id: 'p1', name: 'x', status: 'ACTIVE', positions: [], createdAt: 'now' });
  });

  it('maps a 400 response to an "invalid" outcome carrying errors[]', () => {
    let outcome: { kind: string; errors?: unknown } | undefined;
    service.createPortfolio(draft, 'k').subscribe((o) => (outcome = o as typeof outcome));

    http.expectOne('/api/portfolios').flush(
      { type: '/problems/portfolio-validation', errors: [{ field: 'name', code: 'REQUIRED', message: 'Name required' }] },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(outcome?.kind).toBe('invalid');
    expect(outcome?.errors).toEqual([{ field: 'name', code: 'REQUIRED', message: 'Name required' }]);
  });

  it('maps a 503 response to a "not-saved" outcome', () => {
    let outcome: { kind: string } | undefined;
    service.createPortfolio(draft, 'k').subscribe((o) => (outcome = o as typeof outcome));

    http.expectOne('/api/portfolios').flush(
      { type: '/problems/portfolio-not-saved' },
      { status: 503, statusText: 'Service Unavailable' },
    );

    expect(outcome?.kind).toBe('not-saved');
  });

  it('sends averagePurchasePrice as a decimal string when provided', () => {
    const withPrice: PortfolioDraft = {
      name: 'P',
      positions: [
        { ticker: 'ASML', market: 'XAMS', quantity: '3', currency: 'EUR', averagePurchasePrice: '812.50' },
      ],
    };
    service.createPortfolio(withPrice, 'k').subscribe();
    const req = http.expectOne('/api/portfolios');
    expect(req.request.body.positions[0].averagePurchasePrice).toBe('812.50');
    req.flush({ id: 'p', name: 'P', status: 'ACTIVE', positions: [], createdAt: 'now' });
  });
});
