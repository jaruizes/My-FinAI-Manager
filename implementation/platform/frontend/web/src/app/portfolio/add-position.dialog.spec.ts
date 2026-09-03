import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Observable, of } from 'rxjs';
import { AddPositionDialogComponent } from './add-position.dialog';
import { InstrumentSearchService } from './instrument-search.service';
import { CatalogListing } from './instrument.models';
import { PositionDraft } from './portfolio-creation.models';

const AAPL: CatalogListing = {
  id: 'us-aapl',
  name: 'Apple Inc.',
  ticker: 'AAPL',
  market: 'XNAS',
  currency: 'USD',
  active: true,
  isin: null,
};
const SAN: CatalogListing = {
  id: 'eu-san',
  name: 'Banco Santander, S.A.',
  ticker: 'SAN',
  market: 'XMAD',
  currency: 'EUR',
  active: true,
  isin: 'ES0113900J37',
};
// Two listings of one economic instrument (synthetic — EN004's catalog has none today).
const DUAL_EU: CatalogListing = { id: 'd-eu', name: 'Dual Listco plc', ticker: 'DUAL', market: 'XAMS', currency: 'EUR', active: true };
const DUAL_US: CatalogListing = { id: 'd-us', name: 'Dual Listco plc', ticker: 'DUAL', market: 'XNAS', currency: 'USD', active: true };

class FakeSearch {
  result: CatalogListing[] | null = [];
  lastQuery = '';
  calls = 0;
  search(query: string): Observable<CatalogListing[] | null> {
    this.calls++;
    this.lastQuery = query;
    return of(this.result);
  }
}

describe('AddPositionDialogComponent (FD002)', () => {
  let fixture: ComponentFixture<AddPositionDialogComponent>;
  let component: AddPositionDialogComponent;
  let search: FakeSearch;

  beforeEach(async () => {
    search = new FakeSearch();
    await TestBed.configureTestingModule({
      imports: [AddPositionDialogComponent],
      providers: [{ provide: InstrumentSearchService, useValue: search }],
    }).compileComponents();
    fixture = TestBed.createComponent(AddPositionDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function type(value: string): void {
    component.searchControl.setValue(value);
    tick(250);
    fixture.detectChanges();
  }

  // ---- U1: no free-text ticker/market/currency -------------------------------------------

  it('has no free-text ticker / market / currency inputs', () => {
    for (const name of ['ticker', 'market', 'currency']) {
      expect(fixture.debugElement.query(By.css(`input[formControlName="${name}"]`))).toBeNull();
    }
    expect(fixture.debugElement.query(By.css('[role="combobox"]'))).not.toBeNull();
  });

  // ---- U2/U3: search → results → select (USD single listing) -----------------------------

  it('searches (debounced) and shows a results listbox', fakeAsync(() => {
    search.result = [AAPL];
    type('aapl');
    expect(search.lastQuery).toBe('aapl');
    expect(component.state.kind).toBe('results');
    const options = fixture.debugElement.queryAll(By.css('[role="option"]'));
    expect(options.length).toBe(1);
    expect(options[0].nativeElement.textContent).toContain('Apple Inc.');
    expect(options[0].nativeElement.textContent).toContain('AAPL · XNAS · USD');
  }));

  it('does not issue a search for a blank query', fakeAsync(() => {
    type('   ');
    expect(search.calls).toBe(0);
    expect(component.state.kind).toBe('idle');
  }));

  it('selects a USD single-listing instrument by keyboard and fills ticker/market/currency', fakeAsync(() => {
    search.result = [AAPL];
    type('aapl');
    const input = fixture.debugElement.query(By.css('[role="combobox"]')).nativeElement as HTMLInputElement;
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    fixture.detectChanges();

    expect(component.selected).toEqual(AAPL);
    expect(component.ticker).toBe('AAPL');
    expect(component.market).toBe('XNAS');
    expect(component.currency).toBe('USD');
    // single listing -> no Market/Currency selector
    expect(fixture.debugElement.query(By.css('#ap-listing'))).toBeNull();
  }));

  it('selects a EUR single-listing instrument (FR-026 EUR coverage)', fakeAsync(() => {
    search.result = [SAN];
    type('santander');
    component.choose(SAN);
    fixture.detectChanges();
    expect(component.currency).toBe('EUR');
    expect(component.market).toBe('XMAD');
  }));

  it('emits a PositionDraft from the selected listing + entered quantity', () => {
    let emitted: PositionDraft | undefined;
    component.confirmed.subscribe((d) => (emitted = d));
    component.choose(SAN);
    component.form.patchValue({ quantity: '3' });
    fixture.detectChanges();

    expect(component.canConfirm()).toBeTrue();
    component.confirm();

    expect(emitted).toEqual({
      ticker: 'SAN',
      market: 'XMAD',
      currency: 'EUR',
      quantity: '3',
      instrumentName: 'Banco Santander, S.A.',
    });
  });

  it('cannot confirm until an instrument is selected', () => {
    component.form.patchValue({ quantity: '3' });
    expect(component.canConfirm()).toBeFalse();
    component.choose(AAPL);
    expect(component.canConfirm()).toBeTrue();
  });

  // ---- U4: constrained listing selector (multi-listing) ---------------------------------

  it('shows a Market/Currency selector limited to the instrument’s real listings', fakeAsync(() => {
    search.result = [DUAL_EU, DUAL_US];
    type('dual');
    component.choose(DUAL_EU);
    fixture.detectChanges();

    const selectEl = fixture.debugElement.query(By.css('#ap-listing'));
    expect(selectEl).not.toBeNull();
    const opts = fixture.debugElement.queryAll(By.css('#ap-listing option')).map((o) => o.nativeElement.textContent.trim());
    expect(opts).toEqual(['XAMS · EUR', 'XNAS · USD']);

    component.chooseListingById('d-us');
    expect(component.currency).toBe('USD');
    expect(component.market).toBe('XNAS');
  }));

  // ---- U3: no-results / error -----------------------------------------------------------

  it('shows a no-results state and no way to accept the typed text', fakeAsync(() => {
    search.result = [];
    type('zzzznope');
    expect(component.state.kind).toBe('no-results');
    expect(fixture.nativeElement.textContent).toContain('No matching instrument found.');
    expect(component.canConfirm()).toBeFalse();
  }));

  it('shows a recoverable error with a Retry that re-runs the search', fakeAsync(() => {
    search.result = null;
    type('aapl');
    expect(component.state.kind).toBe('error');
    expect(fixture.nativeElement.textContent).toContain('Retry');

    search.result = [AAPL];
    component.retry();
    tick(0);
    fixture.detectChanges();
    expect(component.state.kind).toBe('results');
  }));

  // ---- edit mode ----------------------------------------------------------------------

  it('pre-fills as an edit when given an initial position', () => {
    component.initial = { ticker: 'ASML', market: 'XAMS', quantity: '3', currency: 'EUR', instrumentName: 'ASML Holding N.V.' };
    component.ngOnInit();
    fixture.detectChanges();
    expect(component.editing).toBeTrue();
    expect(component.selected?.ticker).toBe('ASML');
    expect(component.currency).toBe('EUR');
    expect(component.form.value.quantity).toBe('3');
  });

  // ---- U8: accessibility --------------------------------------------------------------

  it('exposes an accessible combobox: label, listbox wiring, and aria-activedescendant', fakeAsync(() => {
    const label = fixture.debugElement.query(By.css('label[for="ap-instrument"]'));
    expect(label?.nativeElement.textContent).toContain('Instrument');

    search.result = [AAPL, SAN];
    type('a');
    const combo = fixture.debugElement.query(By.css('[role="combobox"]')).nativeElement as HTMLElement;
    expect(combo.getAttribute('aria-controls')).toBe('ap-listbox');
    expect(combo.getAttribute('aria-expanded')).toBe('true');
    expect(combo.getAttribute('aria-activedescendant')).toBe('ap-opt-0');

    combo.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    fixture.detectChanges();
    expect(combo.getAttribute('aria-activedescendant')).toBe('ap-opt-1');
    expect(fixture.debugElement.query(By.css('#ap-listbox[role="listbox"]'))).not.toBeNull();
  }));

  it('emits cancelled on cancel', () => {
    let cancelled = false;
    component.cancelled.subscribe(() => (cancelled = true));
    component.cancel();
    expect(cancelled).toBeTrue();
  });
});
