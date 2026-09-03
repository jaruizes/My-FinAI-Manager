import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';
import { routes } from './app.routes';

describe('AppComponent (application shell)', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter(routes)],
    }).compileComponents();
  });

  it('creates the root component', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('renders the shell with a sidebar and a top bar', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;

    expect(host.querySelector('app-shell')).not.toBeNull();
    expect(host.querySelector('app-sidebar')).not.toBeNull();
    expect(host.querySelector('app-top-bar')).not.toBeNull();
  });

  it('exposes the Portfolios navigation entry (FD001 — the first product capability)', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;

    const navLinks = Array.from(host.querySelectorAll('app-sidebar a')) as HTMLAnchorElement[];
    expect(navLinks.map((a) => a.textContent?.trim())).toContain('Portfolios');
    expect(navLinks.some((a) => a.getAttribute('href') === '/portfolios/new')).toBeTrue();
  });
});
