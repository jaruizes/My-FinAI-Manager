import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { PortfolioAnalysisView, RequestedPortfolioAnalysisView } from './portfolio-analysis.models';
import { PortfolioAnalysisService } from './portfolio-analysis.service';

describe('PortfolioAnalysisService', () => {
  let service: PortfolioAnalysisService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PortfolioAnalysisService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('returns the analysis body on 200', () => {
    const body: PortfolioAnalysisView = {
      status: 'COMPLETED',
      requestedAt: '2026-09-05T20:00:00Z',
      completedAt: '2026-09-05T20:00:07Z',
      overallDiversification: { level: 'MODERATE', explanation: 'Concentrated in Technology.' },
      keyInsights: [{ type: 'SECTOR_EXPOSURE', message: 'Technology is 76%.' }],
      risks: [
        {
          type: 'SECTOR_CONCENTRATION',
          severity: 'HIGH',
          title: 'Sector concentration',
          explanation: '...',
        },
      ],
    };
    let result: unknown;
    service.getLatest('p1').subscribe((r) => (result = r));

    const req = http.expectOne('/api/portfolios/p1/analysis/latest');
    expect(req.request.method).toBe('GET');
    req.flush(body);

    expect(result).toEqual(body);
  });

  it('maps a 404 to "not-found"', () => {
    let result: unknown;
    service.getLatest('missing').subscribe((r) => (result = r));
    http
      .expectOne('/api/portfolios/missing/analysis/latest')
      .flush(null, { status: 404, statusText: 'Not Found' });
    expect(result).toBe('not-found');
  });

  it('maps any other error to null', () => {
    let result: unknown = 'unset';
    service.getLatest('p1').subscribe((r) => (result = r));
    http
      .expectOne('/api/portfolios/p1/analysis/latest')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    expect(result).toBeNull();
  });

  it('does not throw for an unexpected HttpErrorResponse shape', () => {
    let result: unknown = 'unset';
    service.getLatest('p1').subscribe((r) => (result = r));
    const req = http.expectOne('/api/portfolios/p1/analysis/latest');
    req.error(new ProgressEvent('network error') as unknown as ErrorEvent);
    expect(result).toBeNull();
  });

  /* ---- FD005 US4 — requestNew ("Run analysis again") ---- */

  it('requestNew posts and returns the accepted body on 202', () => {
    const body: RequestedPortfolioAnalysisView = {
      analysisId: 'a1',
      status: 'PENDING',
      requestedAt: '2026-09-05T20:00:00Z',
    };
    let result: unknown;
    service.requestNew('p1').subscribe((r) => (result = r));

    const req = http.expectOne('/api/portfolios/p1/analysis');
    expect(req.request.method).toBe('POST');
    req.flush(body, { status: 202, statusText: 'Accepted' });

    expect(result).toEqual(body);
  });

  it('requestNew maps a 409 to "conflict"', () => {
    let result: unknown;
    service.requestNew('p1').subscribe((r) => (result = r));
    http.expectOne('/api/portfolios/p1/analysis').flush(null, { status: 409, statusText: 'Conflict' });
    expect(result).toBe('conflict');
  });

  it('requestNew maps a 404 to "not-found"', () => {
    let result: unknown;
    service.requestNew('missing').subscribe((r) => (result = r));
    http
      .expectOne('/api/portfolios/missing/analysis')
      .flush(null, { status: 404, statusText: 'Not Found' });
    expect(result).toBe('not-found');
  });

  it('requestNew maps any other error to null', () => {
    let result: unknown = 'unset';
    service.requestNew('p1').subscribe((r) => (result = r));
    http
      .expectOne('/api/portfolios/p1/analysis')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });
    expect(result).toBeNull();
  });
});
