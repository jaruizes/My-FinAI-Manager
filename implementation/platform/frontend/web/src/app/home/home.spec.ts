import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Home } from './home';

describe('Home', () => {
  let fixture: ComponentFixture<Home>;
  let httpMock: HttpTestingController;

  const helloUrl = '/api/v1/hello';
  const text = () => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const testId = (id: string) =>
    (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Home],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(Home);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('shows the loading state before the response resolves', () => {
    fixture.detectChanges(); // triggers ngOnInit + request
    httpMock.expectOne(helloUrl);

    expect(testId('version-loading')).not.toBeNull();
    expect(testId('version')).toBeNull();
    expect(testId('version-error')).toBeNull();
  });

  it('renders the exact platform version returned by the backend', () => {
    fixture.detectChanges();
    httpMock.expectOne(helloUrl).flush({ version: '0.1.0' });
    fixture.detectChanges();

    expect(testId('version')?.textContent?.trim()).toBe('0.1.0');
    expect(testId('version-error')).toBeNull();
  });

  it('shows an explicit error and no version when the request fails', () => {
    fixture.detectChanges();
    httpMock
      .expectOne(helloUrl)
      .flush({ error: 'PLATFORM_VERSION_UNAVAILABLE' }, { status: 503, statusText: 'Service Unavailable' });
    fixture.detectChanges();

    expect(testId('version-error')).not.toBeNull();
    expect(testId('version')).toBeNull();
    expect(text()).not.toContain('0.1.0');
  });

  it('exposes the application identity and a version element, and no product functionality', () => {
    fixture.detectChanges();
    httpMock.expectOne(helloUrl).flush({ version: '0.1.0' });
    fixture.detectChanges();

    const host = fixture.nativeElement as HTMLElement;
    expect(text()).toContain('My-FinAI-Manager');
    expect(testId('version')).not.toBeNull();

    // No product functionality: no navigation, links, forms, tables, or action controls.
    for (const selector of ['nav', 'a', 'button', 'form', 'input', 'select', 'table', '[routerLink]']) {
      expect(host.querySelector(selector)).withContext(`unexpected <${selector}>`).toBeNull();
    }
  });
});
