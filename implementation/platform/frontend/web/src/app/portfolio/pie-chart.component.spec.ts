import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PieChartComponent } from './pie-chart.component';
import { AllocationSlice } from './portfolio.models';

describe('PieChartComponent', () => {
  let fixture: ComponentFixture<PieChartComponent>;

  function render(slices: AllocationSlice[], title = 'Allocation by Ticker'): HTMLElement {
    fixture = TestBed.createComponent(PieChartComponent);
    fixture.componentRef.setInput('slices', slices);
    fixture.componentRef.setInput('title', title);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => TestBed.configureTestingModule({ imports: [PieChartComponent] }));

  it('renders nothing for an empty or all-zero slice set', () => {
    expect(render([]).querySelector('svg')).toBeNull();
    expect(render([{ label: 'A', fraction: 0 }]).querySelector('svg')).toBeNull();
  });

  it('renders a single ~100% slice as a full circle, not a degenerate wedge', () => {
    const el = render([{ label: 'AAPL', fraction: 1 }]);
    expect(el.querySelectorAll('svg circle').length).toBe(1);
    expect(el.querySelectorAll('svg path').length).toBe(0);
    expect(el.querySelector('svg circle title')?.textContent).toBe('AAPL');
  });

  it('renders one <path> per slice for a multi-slice chart', () => {
    const el = render([
      { label: 'AAPL', fraction: 0.761904761905 },
      { label: 'SAN', fraction: 0.238095238095 },
    ]);
    const paths = el.querySelectorAll('svg path');
    expect(paths.length).toBe(2);
    // the > 50% slice uses the large-arc flag; the < 50% slice does not
    const dLarge = paths[0].getAttribute('d')!;
    const dSmall = paths[1].getAttribute('d')!;
    expect(dLarge).toMatch(/A 48 48 0 1 1 /); // largeArcFlag = 1
    expect(dSmall).toMatch(/A 48 48 0 0 1 /); // largeArcFlag = 0
    expect(dLarge.startsWith('M 50 50 L ')).toBeTrue();
  });

  it('still closes the ring when the fractions do not sum to exactly 1', () => {
    // e.g. three rounded thirds summing to 0.999
    const el = render([
      { label: 'A', fraction: 0.333 },
      { label: 'B', fraction: 0.333 },
      { label: 'C', fraction: 0.333 },
    ]);
    expect(el.querySelectorAll('svg path').length).toBe(3);
    // last wedge ends back at the 12 o'clock start point (50, 2) within rounding
    const last = el.querySelectorAll('svg path')[2].getAttribute('d')!;
    expect(last).toMatch(/ 50 2 Z$/);
  });

  it('shows a legend entry per slice with the backend percentage (2 dp), and a swatch', () => {
    const el = render([
      { label: 'AAPL', fraction: 0.761904761905 },
      { label: 'SAN', fraction: 0.238095238095 },
    ]);
    const items = Array.from(el.querySelectorAll('.chart__legend li')).map((li) => li.textContent);
    expect(items[0]).toContain('AAPL');
    expect(items[0]).toContain('76.19%');
    expect(items[1]).toContain('SAN');
    expect(items[1]).toContain('23.81%');
    expect(el.querySelectorAll('.chart__legend .chart__swatch').length).toBe(2);
  });

  it('exposes an accessible label naming the title and top slices', () => {
    const el = render(
      [
        { label: 'Technology', fraction: 0.7619 },
        { label: 'Financial Services', fraction: 0.2381 },
      ],
      'Allocation by Sector',
    );
    const svg = el.querySelector('svg')!;
    expect(svg.getAttribute('role')).toBe('img');
    const aria = svg.getAttribute('aria-label')!;
    expect(aria).toContain('Allocation by Sector');
    expect(aria).toContain('Technology 76.19%');
    expect(aria).toContain('Financial Services 23.81%');
  });

  it('drops non-positive slices from geometry and legend', () => {
    const el = render([
      { label: 'AAPL', fraction: 0.75 },
      { label: 'GHOST', fraction: 0 },
      { label: 'SAN', fraction: 0.25 },
    ]);
    expect(el.querySelectorAll('svg path').length).toBe(2);
    expect(el.textContent).not.toContain('GHOST');
  });
});
