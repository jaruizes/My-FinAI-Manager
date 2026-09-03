import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { PortfolioDetailPageComponent } from './portfolio-detail.page';
import { PortfolioQueryService } from './portfolio-query.service';
import { PortfolioView } from './portfolio.models';

describe('PortfolioDetailPageComponent', () => {
  let fixture: ComponentFixture<PortfolioDetailPageComponent>;
  let api: jasmine.SpyObj<PortfolioQueryService>;

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

  function setup(): void {
    fixture = TestBed.createComponent(PortfolioDetailPageComponent);
    fixture.detectChanges();
  }

  beforeEach(() => {
    api = jasmine.createSpyObj<PortfolioQueryService>('PortfolioQueryService', ['list', 'getById']);
    TestBed.configureTestingModule({
      imports: [PortfolioDetailPageComponent],
      providers: [
        provideRouter([]),
        { provide: PortfolioQueryService, useValue: api },
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
    setup();
    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('Edit');
    expect(text).not.toContain('Remove');
    expect(text).not.toContain('Add position');
    expect(fixture.nativeElement.querySelectorAll('button').length).toBe(0);
  });
});
