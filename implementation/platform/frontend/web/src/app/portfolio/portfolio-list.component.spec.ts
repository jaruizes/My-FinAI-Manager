import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject, of } from 'rxjs';
import { PortfolioListComponent } from './portfolio-list.component';
import { PortfolioQueryService } from './portfolio-query.service';
import { PortfolioSummary } from './portfolio.models';

describe('PortfolioListComponent', () => {
  let fixture: ComponentFixture<PortfolioListComponent>;
  let api: jasmine.SpyObj<PortfolioQueryService>;

  const summaries: PortfolioSummary[] = [
    { id: 'p2', name: 'Newer', positionCount: 3 },
    { id: 'p1', name: 'Older', positionCount: 1 },
  ];

  function setup(): void {
    fixture = TestBed.createComponent(PortfolioListComponent);
    fixture.detectChanges();
  }

  beforeEach(() => {
    api = jasmine.createSpyObj<PortfolioQueryService>('PortfolioQueryService', ['list', 'getById']);
    TestBed.configureTestingModule({
      imports: [PortfolioListComponent],
      providers: [provideRouter([]), { provide: PortfolioQueryService, useValue: api }],
    });
  });

  it('shows a loading state before the list resolves', () => {
    api.list.and.returnValue(new Subject());
    setup();
    expect(fixture.nativeElement.textContent).toContain('Loading portfolios');
  });

  it('renders one row per portfolio with its name and position count', () => {
    api.list.and.returnValue(of(summaries));
    setup();

    const rows = fixture.nativeElement.querySelectorAll('tbody tr');
    expect(rows.length).toBe(2);
    expect(rows[0].textContent).toContain('Newer');
    expect(rows[0].textContent).toContain('3');
    expect(rows[1].textContent).toContain('Older');
    expect(rows[1].textContent).toContain('1');
  });

  it('makes each row name a link to that portfolio detail route', () => {
    api.list.and.returnValue(of(summaries));
    setup();

    const link = fixture.nativeElement.querySelector('tbody tr a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toBe('/portfolios/p2');
  });

  it('shows the empty state with a Create action and no rows when the list is empty', () => {
    api.list.and.returnValue(of([]));
    setup();

    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(0);
    expect(fixture.nativeElement.textContent).toContain('do not have any portfolios yet');
    const create = fixture.nativeElement.querySelector('.empty a') as HTMLAnchorElement;
    expect(create.getAttribute('href')).toBe('/portfolios/new');
  });

  it('shows a recoverable error with a Retry that reloads', () => {
    api.list.and.returnValue(of(null));
    setup();
    expect(fixture.nativeElement.textContent).toContain("couldn't load your portfolios");

    api.list.and.returnValue(of(summaries));
    (fixture.nativeElement.querySelector('.state--error button') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(api.list).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(2);
  });

  it('has no edit or delete control anywhere', () => {
    api.list.and.returnValue(of(summaries));
    setup();
    const text = fixture.nativeElement.textContent as string;
    expect(text).not.toContain('Edit');
    expect(text).not.toContain('Delete');
    expect(text).not.toContain('Remove');
  });
});
