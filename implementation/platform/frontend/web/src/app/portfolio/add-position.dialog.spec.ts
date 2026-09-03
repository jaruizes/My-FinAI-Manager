import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AddPositionDialogComponent } from './add-position.dialog';
import { PositionDraft } from './portfolio-creation.models';

describe('AddPositionDialogComponent', () => {
  let fixture: ComponentFixture<AddPositionDialogComponent>;
  let component: AddPositionDialogComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [AddPositionDialogComponent] }).compileComponents();
    fixture = TestBed.createComponent(AddPositionDialogComponent);
    component = fixture.componentInstance;
  });

  function fill(values: Partial<Record<string, string>>): void {
    component.form.patchValue(values);
    fixture.detectChanges();
  }

  it('is invalid until the required fields are filled', () => {
    fixture.detectChanges();
    expect(component.form.invalid).toBeTrue();
    fill({ ticker: 'ASML', market: 'XAMS', quantity: '12', currency: 'EUR' });
    expect(component.form.valid).toBeTrue();
  });

  it('emits a PositionDraft (upper-cased, trimmed) on confirm, omitting blank optionals', () => {
    let emitted: PositionDraft | undefined;
    component.confirmed.subscribe((d) => (emitted = d));
    fill({ ticker: ' asml ', market: ' xams ', quantity: '12', currency: 'eur' });

    component.confirm();

    expect(emitted).toEqual({ ticker: 'ASML', market: 'XAMS', quantity: '12', currency: 'EUR' });
  });

  it('includes the optional date and price when provided', () => {
    let emitted: PositionDraft | undefined;
    component.confirmed.subscribe((d) => (emitted = d));
    fill({
      ticker: 'ASML',
      market: 'XAMS',
      quantity: '3',
      currency: 'EUR',
      initialPurchaseDate: '2024-05-14',
      averagePurchasePrice: '812.50',
    });

    component.confirm();

    expect(emitted?.initialPurchaseDate).toBe('2024-05-14');
    expect(emitted?.averagePurchasePrice).toBe('812.50');
  });

  it('rejects a non-numeric quantity and a malformed currency (format checks only)', () => {
    fill({ ticker: 'ASML', market: 'XAMS', quantity: 'twelve', currency: 'EU' });
    expect(component.form.valid).toBeFalse();
  });

  it('pre-fills and confirms as an edit when given an initial position', () => {
    component.initial = { ticker: 'ASML', market: 'XAMS', quantity: '3', currency: 'EUR' };
    component.ngOnInit();
    fixture.detectChanges();
    expect(component.editing).toBeTrue();
    expect(component.form.value.ticker).toBe('ASML');
  });

  it('emits cancelled on cancel', () => {
    let cancelled = false;
    component.cancelled.subscribe(() => (cancelled = true));
    component.cancel();
    expect(cancelled).toBeTrue();
  });
});
