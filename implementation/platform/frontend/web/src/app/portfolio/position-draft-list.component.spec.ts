import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PositionDraftListComponent } from './position-draft-list.component';
import { PositionDraft } from './portfolio-creation.models';

describe('PositionDraftListComponent', () => {
  let fixture: ComponentFixture<PositionDraftListComponent>;
  let component: PositionDraftListComponent;

  const p = (ticker: string): PositionDraft => ({
    ticker,
    market: 'XAMS',
    quantity: '1',
    currency: 'EUR',
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [PositionDraftListComponent] }).compileComponents();
    fixture = TestBed.createComponent(PositionDraftListComponent);
    component = fixture.componentInstance;
  });

  it('shows an empty message with no positions', () => {
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No positions added yet');
  });

  it('renders one row per position', () => {
    component.positions = [p('ASML'), p('MSFT'), p('SAP')];
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(3);
    expect(fixture.nativeElement.textContent).toContain('ASML');
  });

  it('emits remove / edit with the row index', () => {
    component.positions = [p('ASML'), p('MSFT')];
    fixture.detectChanges();
    const removed: number[] = [];
    const edited: number[] = [];
    component.remove.subscribe((i) => removed.push(i));
    component.edit.subscribe((i) => edited.push(i));

    const row1 = fixture.nativeElement.querySelectorAll('tbody tr')[1];
    const buttons = row1.querySelectorAll('button');
    (buttons[0] as HTMLButtonElement).click(); // Edit
    (buttons[1] as HTMLButtonElement).click(); // Remove

    expect(edited).toEqual([1]);
    expect(removed).toEqual([1]);
  });

  it('renders a row error when provided', () => {
    component.positions = [];
    component.rowError = 'A portfolio must have at least one position.';
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('at least one position');
  });
});
