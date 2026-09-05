import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { PortfolioAnalysisView } from './portfolio-analysis.models';
import { PortfolioAnalysisService } from './portfolio-analysis.service';
import { PortfolioDetailPageComponent } from './portfolio-detail.page';
import { PortfolioQueryService } from './portfolio-query.service';
import { PortfolioValuationService } from './portfolio-valuation.service';
import { PortfolioValuationView, PortfolioView } from './portfolio.models';

describe('PortfolioDetailPageComponent', () => {
  let fixture: ComponentFixture<PortfolioDetailPageComponent>;
  let api: jasmine.SpyObj<PortfolioQueryService>;
  let valuationApi: jasmine.SpyObj<PortfolioValuationService>;
  let analysisApi: jasmine.SpyObj<PortfolioAnalysisService>;

  const portfolio: PortfolioView = {
    id: 'p1',
    name: 'Long Term Investment',
    status: 'ACTIVE',
    positions: [
      {
        id: 'x1',
        ticker: 'ASML',
        market: 'XAMS',
        quantity: '3.250',
        currency: 'EUR',
        initialPurchaseDate: '2024-05-14',
        averagePurchasePrice: '812.50',
      },
      {
        id: 'x2',
        ticker: 'MSFT',
        market: 'XNAS',
        quantity: '10',
        currency: 'USD',
        initialPurchaseDate: null,
        averagePurchasePrice: null,
      },
    ],
    createdAt: '2026-09-01T10:00:00Z',
  };

  const completedValuation: PortfolioValuationView = {
    portfolioId: 'p1',
    status: 'COMPLETED',
    calculatedAt: '2026-09-04T12:00:00Z',
    totalValueEUR: '2100',
    totalValueUSD: '2625',
    marketDataAsOf: '2026-09-04T11:00:00Z',
    fxDataAsOf: '2026-09-04T11:00:00Z',
    positions: [
      {
        ticker: 'ASML',
        market: 'XAMS',
        quantity: '3.250',
        nativeCurrency: 'EUR',
        valued: true,
        marketPrice: '160',
        nativeMarketValue: '520',
        valueInEUR: '520',
        valueInUSD: '650',
        portfolioWeight: '0.247619047619',
        sector: 'Technology',
        priceObservedAt: '2026-09-04T11:00:00Z',
      },
      {
        ticker: 'MSFT',
        market: 'XNAS',
        quantity: '10',
        nativeCurrency: 'USD',
        valued: true,
        marketPrice: '200',
        nativeMarketValue: '2000',
        valueInEUR: '1600',
        valueInUSD: '2000',
        portfolioWeight: '0.761904761905',
        sector: 'Technology',
        priceObservedAt: '2026-09-04T11:00:00Z',
      },
    ],
    sectors: [{ sector: 'Technology', sectorValueEUR: '2120', sectorWeight: '1.0' }],
  };

  function setup(): void {
    fixture = TestBed.createComponent(PortfolioDetailPageComponent);
    fixture.detectChanges();
  }

  beforeEach(() => {
    api = jasmine.createSpyObj<PortfolioQueryService>('PortfolioQueryService', ['list', 'getById']);
    valuationApi = jasmine.createSpyObj<PortfolioValuationService>('PortfolioValuationService', [
      'getValuation',
    ]);
    valuationApi.getValuation.and.returnValue(of(null)); // "unavailable" by default
    analysisApi = jasmine.createSpyObj<PortfolioAnalysisService>('PortfolioAnalysisService', [
      'getLatest',
      'requestNew',
    ]);
    analysisApi.getLatest.and.returnValue(of(null)); // "unavailable" by default
    TestBed.configureTestingModule({
      imports: [PortfolioDetailPageComponent],
      providers: [
        provideRouter([]),
        { provide: PortfolioQueryService, useValue: api },
        { provide: PortfolioValuationService, useValue: valuationApi },
        { provide: PortfolioAnalysisService, useValue: analysisApi },
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 'p1' }) } },
        },
      ],
    });
  });

  it('shows a loading state before the portfolio resolves', () => {
    api.getById.and.returnValue(new Subject());
    setup();
    expect(fixture.nativeElement.textContent).toContain('Loading portfolio');
  });

  it('renders the portfolio name and one row per position', () => {
    api.getById.and.returnValue(of(portfolio));
    setup();

    expect(api.getById).toHaveBeenCalledWith('p1');
    expect(fixture.nativeElement.querySelector('.page__title').textContent).toContain(
      'Long Term Investment',
    );
    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('ASML');
    expect(rows[0].textContent).toContain('3.250');
    expect(rows[0].textContent).toContain('2024-05-14');
    expect(rows[0].textContent).toContain('812.50');
  });

  it('shows a placeholder for a position without a date or price (not zero)', () => {
    api.getById.and.returnValue(of(portfolio));
    setup();
    const secondRow = fixture.nativeElement.querySelectorAll('tbody tr')[1];
    expect(secondRow.textContent).toContain('—');
    expect(secondRow.textContent).not.toContain('0.00');
  });

  it('shows a not-found state with a link home on a 404', () => {
    api.getById.and.returnValue(of('not-found' as const));
    setup();
    expect(fixture.nativeElement.textContent).toContain("doesn't exist");
    expect(fixture.nativeElement.querySelector('a[href="/"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('tbody')).toBeNull();
  });

  it('shows a recoverable error with Retry that reloads', () => {
    api.getById.and.returnValue(of(null));
    setup();
    expect(fixture.nativeElement.textContent).toContain("couldn't load this portfolio");

    api.getById.and.returnValue(of(portfolio));
    (fixture.nativeElement.querySelector('.state--error button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
  });

  it('has no edit / add / remove position control', () => {
    api.getById.and.returnValue(of(portfolio));
    valuationApi.getValuation.and.returnValue(of(completedValuation));
    setup();
    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('Edit');
    expect(text).not.toContain('Remove');
    expect(text).not.toContain('Add position');
    expect(fixture.nativeElement.querySelectorAll('button').length).toBe(0);
  });

  /* ---- FD004 — valuation display (US5) ---- */

  it('shows the EUR and USD totals and valuation columns when COMPLETED', () => {
    api.getById.and.returnValue(of(portfolio));
    valuationApi.getValuation.and.returnValue(of(completedValuation));
    setup();

    expect(valuationApi.getValuation).toHaveBeenCalledWith('p1');
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('€2,100.00');
    expect(text).toContain('$2,625.00');
    expect(text).toContain('Valued at');

    const msftRow = fixture.nativeElement.querySelectorAll('tbody tr')[1];
    expect(msftRow.textContent).toContain('76.19%');
    expect(msftRow.textContent).toContain('Technology');
    // Market price carries the native currency (FR-029) …
    expect(msftRow.textContent).toContain('200.00 USD');
    const asmlRow = fixture.nativeElement.querySelectorAll('tbody tr')[0];
    expect(asmlRow.textContent).toContain('160.00 EUR');

    // … and there is no native "Market value" column or standalone sector list any more.
    const headers = Array.from(fixture.nativeElement.querySelectorAll('thead th')).map(
      (th) => (th as HTMLElement).textContent?.trim(),
    );
    expect(headers).not.toContain('Market value');
    expect(headers).toContain('Market price');
    expect(fixture.nativeElement.querySelector('.sectors')).toBeNull();
  });

  it('leaves the valuation cells blank for an unvalued position and names the shortfall (PARTIAL)', () => {
    api.getById.and.returnValue(of(portfolio));
    const partial: PortfolioValuationView = {
      ...completedValuation,
      status: 'PARTIAL',
      totalValueEUR: '1600',
      totalValueUSD: '2000',
      positions: [
        { ...completedValuation.positions[0], valued: false, marketPrice: null, nativeMarketValue: null, valueInEUR: null, valueInUSD: null, portfolioWeight: null },
        completedValuation.positions[1],
      ],
      sectors: [],
    };
    valuationApi.getValuation.and.returnValue(of(partial));
    setup();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Partial valuation — market data unavailable for 1 position');
    const asmlRow = fixture.nativeElement.querySelectorAll('tbody tr')[0];
    expect(asmlRow.textContent).toContain('—');
    expect(asmlRow.textContent).not.toContain('0.00');
    expect(asmlRow.textContent).not.toContain('0%');
  });

  it('shows "Valuation pending" for a PENDING snapshot and no numeric columns', () => {
    api.getById.and.returnValue(of(portfolio));
    valuationApi.getValuation.and.returnValue(
      of({ ...completedValuation, status: 'PENDING', totalValueEUR: null, totalValueUSD: null }),
    );
    setup();

    expect(fixture.nativeElement.textContent).toContain('Valuation pending');
    expect(fixture.nativeElement.querySelectorAll('thead th').length).toBe(6);
  });

  it('shows "Valuation unavailable" and no fabricated zeros when the valuation call fails', () => {
    api.getById.and.returnValue(of(portfolio));
    valuationApi.getValuation.and.returnValue(of(null));
    setup();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Valuation unavailable');
    expect(text).not.toContain('0.00');
    expect(text).not.toContain('0%');
  });

  /* ---- FD004 §17 — two mandatory allocation pie charts (US7) ---- */

  // E2E-001 deterministic numbers
  const chartValuation: PortfolioValuationView = {
    ...completedValuation,
    status: 'COMPLETED',
    totalValueEUR: '2100',
    totalValueUSD: '2625',
    positions: [
      {
        ...completedValuation.positions[0],
        ticker: 'AAPL',
        valued: true,
        portfolioWeight: '0.761904761905',
        sector: 'Technology',
      },
      {
        ...completedValuation.positions[1],
        ticker: 'SAN',
        valued: true,
        portfolioWeight: '0.238095238095',
        sector: 'Financial Services',
      },
    ],
    sectors: [
      { sector: 'Technology', sectorValueEUR: '1600', sectorWeight: '0.761904761905' },
      { sector: 'Financial Services', sectorValueEUR: '500', sectorWeight: '0.238095238095' },
    ],
  };

  function charts(): HTMLElement[] {
    return Array.from(fixture.nativeElement.querySelectorAll('app-pie-chart'));
  }

  it('renders both allocation pie charts for a COMPLETED valuation with matching percentages', () => {
    api.getById.and.returnValue(of(portfolio));
    valuationApi.getValuation.and.returnValue(of(chartValuation));
    setup();

    expect(charts().length).toBe(2);
    const [ticker, sector] = charts();

    expect(ticker.textContent).toContain('Allocation by Ticker');
    expect(ticker.textContent).toContain('AAPL');
    expect(ticker.textContent).toContain('76.19%');
    expect(ticker.textContent).toContain('SAN');
    expect(ticker.textContent).toContain('23.81%');

    expect(sector.textContent).toContain('Allocation by Sector');
    expect(sector.textContent).toContain('Technology');
    expect(sector.textContent).toContain('Financial Services');
    expect(sector.textContent).toContain('76.19%');
    expect(sector.textContent).toContain('23.81%');

    // SC-014 — both charts surface the SAME percentages from the same backend fractions; there is
    // no separate sector-allocation list any more (FR-028), the sector chart legend IS the list.
    const pcts = (el: HTMLElement) => (el.textContent?.match(/\d+\.\d\d%/g) ?? []).sort();
    expect(pcts(sector)).toEqual(['23.81%', '76.19%']);
    expect(pcts(ticker)).toEqual(['23.81%', '76.19%']);
  });

  it('renders the charts for a PARTIAL valuation alongside the partial-state message', () => {
    api.getById.and.returnValue(of(portfolio));
    const partial: PortfolioValuationView = {
      ...chartValuation,
      status: 'PARTIAL',
      totalValueEUR: '1600',
      positions: [
        chartValuation.positions[0], // AAPL valued
        { ...chartValuation.positions[1], valued: false, portfolioWeight: null, marketPrice: null, nativeMarketValue: null, valueInEUR: null, valueInUSD: null },
      ],
      sectors: [{ sector: 'Technology', sectorValueEUR: '1600', sectorWeight: '1.0' }],
    };
    valuationApi.getValuation.and.returnValue(of(partial));
    setup();

    expect(charts().length).toBe(2);
    expect(charts()[0].textContent).toContain('AAPL');
    expect(charts()[0].textContent).not.toContain('SAN'); // only the valued portion
    expect(fixture.nativeElement.textContent).toContain('Partial valuation');
  });

  it('draws NEITHER chart when there is no EUR basis / FAILED / PENDING / absent', () => {
    api.getById.and.returnValue(of(portfolio));

    for (const v of [
      null,
      { ...chartValuation, status: 'FAILED' as const, totalValueEUR: null, totalValueUSD: null, positions: [], sectors: [] },
      { ...chartValuation, status: 'PENDING' as const, totalValueEUR: null },
      { ...chartValuation, totalValueEUR: null, positions: chartValuation.positions.map((p) => ({ ...p, portfolioWeight: null })) },
    ]) {
      valuationApi.getValuation.and.returnValue(of(v as PortfolioValuationView | null));
      setup();
      expect(charts().length).withContext(JSON.stringify(v)).toBe(0);
      expect(fixture.nativeElement.querySelector('.charts')).toBeNull();
      expect(fixture.nativeElement.textContent).not.toContain('0%');
    }
  });

  /* ---- FD005 — AI Portfolio Analysis (US2 completed-state rendering) ---- */

  const completedAnalysis: PortfolioAnalysisView = {
    status: 'COMPLETED',
    requestedAt: '2026-09-05T20:00:00Z',
    completedAt: '2026-09-05T20:00:07Z',
    overallDiversification: { level: 'MODERATE', explanation: 'Concentrated in Technology.' },
    keyInsights: [
      { type: 'SECTOR_EXPOSURE', message: 'Technology represents 76.19% of the Portfolio.' },
    ],
    risks: [
      {
        type: 'SECTOR_CONCENTRATION',
        severity: 'HIGH',
        title: 'Sector concentration',
        explanation: 'Technology dominates the portfolio.',
      },
    ],
  };

  it('renders diversification level+explanation, ordered insights, and risks by severity for a COMPLETED analysis', () => {
    api.getById.and.returnValue(of(portfolio));
    analysisApi.getLatest.and.returnValue(of(completedAnalysis));
    setup();

    expect(analysisApi.getLatest).toHaveBeenCalledWith('p1');
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('AI Portfolio Analysis');
    expect(text).toContain('Moderate diversification');
    expect(text).toContain('Concentrated in Technology.');
    expect(text).toContain('Technology represents 76.19% of the Portfolio.');
    expect(text).toContain('Sector concentration');
    expect(text).toContain('Technology dominates the portfolio.');
    const severity = fixture.nativeElement.querySelector('.analysis__risk-severity');
    expect(severity.textContent.trim()).toBe('HIGH');
    expect(text).toContain('Analysed');
  });

  it('shows a "no analysis requested" message for NONE', () => {
    api.getById.and.returnValue(of(portfolio));
    analysisApi.getLatest.and.returnValue(of({ status: 'NONE' } as PortfolioAnalysisView));
    setup();

    expect(fixture.nativeElement.textContent).toContain('No analysis has been requested yet');
  });

  it('shows an "unavailable" message when the analysis call fails', () => {
    api.getById.and.returnValue(of(portfolio));
    analysisApi.getLatest.and.returnValue(of(null));
    setup();

    expect(fixture.nativeElement.textContent).toContain('Analysis unavailable');
  });

  /* ---- FD005 US3 — in-progress UX + polling ---- */

  const pendingAnalysis: PortfolioAnalysisView = {
    status: 'PENDING',
    requestedAt: '2026-09-05T20:00:00Z',
    completedAt: null,
    overallDiversification: null,
    keyInsights: null,
    risks: null,
  };

  const runningAnalysis: PortfolioAnalysisView = { ...pendingAnalysis, status: 'RUNNING' };

  it('renders the in-progress message for PENDING and RUNNING', () => {
    api.getById.and.returnValue(of(portfolio));
    analysisApi.getLatest.and.returnValue(of(pendingAnalysis));
    setup();
    expect(fixture.nativeElement.textContent).toContain('Analysis in progress');

    analysisApi.getLatest.and.returnValue(of(runningAnalysis));
    fixture.componentInstance.load();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Analysis in progress');
  });

  it('never shows a previous COMPLETED analysis while a newer one is in progress', () => {
    api.getById.and.returnValue(of(portfolio));
    analysisApi.getLatest.and.returnValue(of(completedAnalysis));
    setup();
    expect(fixture.nativeElement.textContent).toContain('Moderate diversification');

    analysisApi.getLatest.and.returnValue(of(pendingAnalysis));
    fixture.componentInstance.load();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('Moderate diversification');
    expect(text).toContain('Analysis in progress');
  });

  it('polls every 2s while PENDING/RUNNING and stops the moment it becomes COMPLETED', fakeAsync(() => {
    api.getById.and.returnValue(of(portfolio));
    analysisApi.getLatest.and.returnValues(
      of(pendingAnalysis),
      of(runningAnalysis),
      of(completedAnalysis),
    );
    setup();
    expect(analysisApi.getLatest).toHaveBeenCalledTimes(1);

    tick(2000);
    expect(analysisApi.getLatest).toHaveBeenCalledTimes(2);

    tick(2000);
    fixture.detectChanges();
    expect(analysisApi.getLatest).toHaveBeenCalledTimes(3);
    expect(fixture.nativeElement.textContent).toContain('Moderate diversification');

    tick(2000); // the stream already completed — no further calls
    expect(analysisApi.getLatest).toHaveBeenCalledTimes(3);
  }));

  it('stops polling the moment the analysis becomes FAILED', fakeAsync(() => {
    api.getById.and.returnValue(of(portfolio));
    const failed: PortfolioAnalysisView = {
      status: 'FAILED',
      requestedAt: '2026-09-05T20:00:00Z',
      completedAt: '2026-09-05T20:00:03Z',
      overallDiversification: null,
      keyInsights: null,
      risks: null,
    };
    analysisApi.getLatest.and.returnValues(of(pendingAnalysis), of(failed));
    setup();

    tick(2000);
    fixture.detectChanges();
    expect(analysisApi.getLatest).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('We could not complete this analysis');

    tick(2000);
    expect(analysisApi.getLatest).toHaveBeenCalledTimes(2); // no further polling once terminal
  }));

  it('applies a soft cap after 30 polls and shows a "taking longer" hint without erroring', fakeAsync(() => {
    api.getById.and.returnValue(of(portfolio));
    analysisApi.getLatest.and.returnValue(of(pendingAnalysis));
    setup();

    tick(2000 * 30);
    fixture.detectChanges();

    expect(analysisApi.getLatest).toHaveBeenCalledTimes(31); // 1 initial + 30 polls
    expect(fixture.nativeElement.textContent).toContain('taking longer than expected');
    expect(fixture.nativeElement.textContent).toContain('Analysis in progress'); // last known state kept

    tick(2000);
    expect(analysisApi.getLatest).toHaveBeenCalledTimes(31); // capped — no further calls
  }));

  it('stops polling when the component is destroyed', fakeAsync(() => {
    api.getById.and.returnValue(of(portfolio));
    analysisApi.getLatest.and.returnValue(of(pendingAnalysis));
    setup();

    tick(2000);
    expect(analysisApi.getLatest).toHaveBeenCalledTimes(2);

    fixture.destroy();
    tick(2000 * 5);
    expect(analysisApi.getLatest).toHaveBeenCalledTimes(2);
    discardPeriodicTasks();
  }));

  /* ---- FD005 US4 — manual re-analysis ---- */

  function rerunButton(): HTMLButtonElement | null {
    return fixture.nativeElement.querySelector('.analysis__rerun');
  }

  it('shows "Run analysis again" for a COMPLETED analysis', () => {
    api.getById.and.returnValue(of(portfolio));
    analysisApi.getLatest.and.returnValue(of(completedAnalysis));
    setup();

    expect(rerunButton()).not.toBeNull();
  });

  it('shows "Run analysis again" when no analysis has ever been requested (NONE)', () => {
    api.getById.and.returnValue(of(portfolio));
    analysisApi.getLatest.and.returnValue(of({ status: 'NONE' } as PortfolioAnalysisView));
    setup();

    expect(rerunButton()).not.toBeNull();
  });

  it('shows "Run analysis again" for a FAILED analysis', () => {
    api.getById.and.returnValue(of(portfolio));
    analysisApi.getLatest.and.returnValue(
      of({ status: 'FAILED', requestedAt: null, completedAt: null, overallDiversification: null, keyInsights: null, risks: null } as PortfolioAnalysisView),
    );
    setup();

    expect(rerunButton()).not.toBeNull();
  });

  it('hides "Run analysis again" while the latest is PENDING or RUNNING', () => {
    api.getById.and.returnValue(of(portfolio));
    analysisApi.getLatest.and.returnValue(of(pendingAnalysis));
    setup();
    expect(rerunButton()).toBeNull();

    analysisApi.getLatest.and.returnValue(of(runningAnalysis));
    fixture.componentInstance.load();
    fixture.detectChanges();
    expect(rerunButton()).toBeNull();
  });

  it('triggers requestNew, then shows the in-progress state and resumes polling', fakeAsync(() => {
    api.getById.and.returnValue(of(portfolio));
    analysisApi.getLatest.and.returnValue(of(completedAnalysis));
    setup();
    expect(rerunButton()).not.toBeNull();

    analysisApi.requestNew.and.returnValue(
      of({ analysisId: 'a2', status: 'PENDING', requestedAt: '2026-09-05T21:00:00Z' }),
    );
    analysisApi.getLatest.and.returnValue(of(runningAnalysis));
    rerunButton()!.click();
    fixture.detectChanges();

    expect(analysisApi.requestNew).toHaveBeenCalledWith('p1');
    expect(fixture.nativeElement.textContent).toContain('Analysis in progress');
    expect(rerunButton()).toBeNull(); // hidden again while the new request is open

    tick(2000);
    expect(analysisApi.getLatest).toHaveBeenCalled(); // polling resumed
    discardPeriodicTasks();
  }));

  /* ---- FD005 US5 — failed analysis: explicit, recoverable, never leaks internals ---- */

  const failedAnalysis: PortfolioAnalysisView = {
    status: 'FAILED',
    requestedAt: '2026-09-05T20:00:00Z',
    completedAt: '2026-09-05T20:00:03Z',
    overallDiversification: null,
    keyInsights: null,
    risks: null,
  };

  it('shows the controlled unavailable message and a working "Run analysis again" for FAILED', fakeAsync(() => {
    api.getById.and.returnValue(of(portfolio));
    analysisApi.getLatest.and.returnValue(of(failedAnalysis));
    setup();

    expect(fixture.nativeElement.textContent).toContain(
      'We could not complete this analysis. You can run it again.',
    );
    expect(rerunButton()).not.toBeNull();

    analysisApi.requestNew.and.returnValue(
      of({ analysisId: 'a3', status: 'PENDING', requestedAt: '2026-09-05T21:00:00Z' }),
    );
    analysisApi.getLatest.and.returnValue(of(pendingAnalysis));
    rerunButton()!.click();
    fixture.detectChanges();

    expect(analysisApi.requestNew).toHaveBeenCalledWith('p1');
    expect(fixture.nativeElement.textContent).toContain('Analysis in progress');
    discardPeriodicTasks();
  }));

  it('never leaks a provider name, stack trace, or internal failure code for any FAILED analysis', () => {
    api.getById.and.returnValue(of(portfolio));
    analysisApi.getLatest.and.returnValue(of(failedAnalysis));
    setup();

    const text = (fixture.nativeElement.textContent as string).toLowerCase();
    const html = fixture.nativeElement.innerHTML as string;
    for (const forbidden of [
      'openai',
      'not_configured',
      'provider_unavailable',
      'guardrail',
      'timeout',
      'exception',
      'stack trace',
      'at com.',
    ]) {
      expect(text).not.toContain(forbidden);
      expect(html.toLowerCase()).not.toContain(forbidden);
    }
  });
});
