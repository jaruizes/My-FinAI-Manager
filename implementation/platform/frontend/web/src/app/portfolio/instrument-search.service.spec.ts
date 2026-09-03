import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CatalogListing } from './instrument.models';
import { InstrumentSearchService } from './instrument-search.service';

describe('InstrumentSearchService', () => {
  let service: InstrumentSearchService;
  let http: HttpTestingController;

  const apple: CatalogListing = {
    id: 'a1',
    name: 'Apple Inc.',
    ticker: 'AAPL',
    market: 'XNAS',
    currency: 'USD',
    active: true,
    isin: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(InstrumentSearchService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('GETs /api/financial-instruments with a trimmed query and maps the response', () => {
    let result: CatalogListing[] | null | undefined;
    service.search('  aapl  ').subscribe((r) => (result = r));

    const req = http.expectOne((r) => r.url === '/api/financial-instruments');
    expect(req.request.method).toBe('GET');
    expect(req.request.params.get('query')).toBe('aapl');

    req.flush([apple]);
    expect(result).toEqual([apple]);
  });

  it('does NOT issue a request for an empty or whitespace-only query', () => {
    let a: CatalogListing[] | null | undefined;
    let b: CatalogListing[] | null | undefined;
    service.search('').subscribe((r) => (a = r));
    service.search('   ').subscribe((r) => (b = r));

    http.expectNone('/api/financial-instruments');
    expect(a).toEqual([]);
    expect(b).toEqual([]);
  });

  it('yields null on an HTTP error so the caller can show a recoverable error', () => {
    let result: CatalogListing[] | null | undefined = undefined;
    service.search('aapl').subscribe((r) => (result = r));

    http
      .expectOne((r) => r.url === '/api/financial-instruments')
      .flush('boom', { status: 503, statusText: 'Service Unavailable' });

    expect(result).toBeNull();
  });

  it('treats a null/absent body as an empty result', () => {
    let result: CatalogListing[] | null | undefined;
    service.search('nope').subscribe((r) => (result = r));
    http.expectOne((r) => r.url === '/api/financial-instruments').flush(null);
    expect(result).toEqual([]);
  });
});
