import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of } from 'rxjs';
import { CreatePortfolioPageComponent } from './create-portfolio.page';
import {
  CreatePortfolioOutcome,
  PortfolioDraft,
  PositionDraft,
} from './portfolio-creation.models';
import { PortfolioApiService } from './portfolio-api.service';

describe('CreatePortfolioPageComponent', () => {
  let fixture: ComponentFixture<CreatePortfolioPageComponent>;
  let component: CreatePortfolioPageComponent;
  let api: jasmine.SpyObj<PortfolioApiService>;

  const position = (ticker = 'ASML'): PositionDraft => ({
    ticker,
    market: 'XAMS',
    quantity: '12',
    currency: 'EUR',
  });

  const created: CreatePortfolioOutcome = {
    kind: 'created',
    portfolio: { id: 'p1', name: 'x', status: 'ACTIVE', positions: [], createdAt: 'now' },
    replayed: false,
  };

  beforeEach(async () => {
    api = jasmine.createSpyObj<PortfolioApiService>('PortfolioApiService', ['createPortfolio']);
    await TestBed.configureTestingModule({
      imports: [CreatePortfolioPageComponent],
      providers: [{ provide: PortfolioApiService, useValue: api }],
    }).compileComponents();
    fixture = TestBed.createComponent(CreatePortfolioPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  function addName(name: string): void {
    component.form.controls.name.setValue(name);
  }

  it('cannot save with no name or no positions', () => {
    expect(component.canSave()).toBeFalse();
    addName('Growth');
    expect(component.canSave()).toBeFalse(); // still no positions
    component.onDialogConfirmed(position());
    expect(component.canSave()).toBeTrue();
  });

  it('US1: save calls the API once with an Idempotency-Key and shows a success message', () => {
    api.createPortfolio.and.returnValue(of(created));
    addName('Long-Term Growth');
    component.onDialogConfirmed(position());

    component.save();

    expect(api.createPortfolio).toHaveBeenCalledTimes(1);
    const [draft, key] = api.createPortfolio.calls.mostRecent().args as [PortfolioDraft, string];
    expect(draft.name).toBe('Long-Term Growth');
    expect(key).toMatch(/[0-9a-f-]{36}/);
    expect(component.successMessage()).toContain('created successfully');
    expect(component.positions().length).toBe(0); // draft reset
  });

  it('US1: Save is disabled while a request is in flight', () => {
    const pending = new Subject<CreatePortfolioOutcome>();
    api.createPortfolio.and.returnValue(pending.asObservable());
    addName('Growth');
    component.onDialogConfirmed(position());

    component.save();
    expect(component.submitting()).toBeTrue();
    expect(component.canSave()).toBeFalse();

    pending.next(created);
    pending.complete();
    expect(component.submitting()).toBeFalse();
  });

  it('US2: every added position is submitted in one request', () => {
    api.createPortfolio.and.returnValue(of(created));
    addName('Diversified');
    component.onDialogConfirmed(position('ASML'));
    component.onDialogConfirmed(position('MSFT'));
    component.onDialogConfirmed(position('SAP'));
    expect(component.positions().length).toBe(3);

    component.save();

    const draft = api.createPortfolio.calls.mostRecent().args[0] as PortfolioDraft;
    expect(draft.positions.map((p) => p.ticker)).toEqual(['ASML', 'MSFT', 'SAP']);
  });

  it('US3: a 400 outcome maps errors onto fields and blocks Save', () => {
    api.createPortfolio.and.returnValue(
      of<CreatePortfolioOutcome>({
        kind: 'invalid',
        errors: [
          { field: 'name', code: 'REQUIRED', message: 'Portfolio name is required.' },
          { field: 'positions[0].quantity', code: 'NOT_POSITIVE', message: 'Quantity must be > 0.' },
        ],
      }),
    );
    addName('x');
    component.onDialogConfirmed(position());
    component.save();
    fixture.detectChanges();

    expect(component.nameErrors()).toContain('Portfolio name is required.');
    expect(component.positionErrors()[0]).toContain('positions[0].quantity');
    expect(component.canSave()).toBeFalse();
    expect(api.createPortfolio).toHaveBeenCalledTimes(1);

    // fixing the draft clears the blocking errors
    component.onDialogConfirmed(position('MSFT'));
    expect(component.canSave()).toBeTrue();
  });

  it('US5: removing the last position disables Save', () => {
    addName('Growth');
    component.onDialogConfirmed(position());
    expect(component.canSave()).toBeTrue();
    component.removePosition(0);
    expect(component.positions().length).toBe(0);
    expect(component.canSave()).toBeFalse();
  });

  it('US5: editing a position replaces it in place', () => {
    addName('Growth');
    component.onDialogConfirmed(position('ASML'));
    component.openEditDialog(0);
    component.onDialogConfirmed({ ...position('ASML'), quantity: '99' });
    expect(component.positions()).toEqual([{ ticker: 'ASML', market: 'XAMS', quantity: '99', currency: 'EUR' }]);
  });

  it('Polish: a 503 outcome keeps the draft and the idempotency key for retry', () => {
    api.createPortfolio.and.returnValue(of<CreatePortfolioOutcome>({ kind: 'not-saved' }));
    addName('Growth');
    component.onDialogConfirmed(position());
    component.save();

    expect(component.notSavedMessage()).toContain('try again');
    expect(component.positions().length).toBe(1); // draft intact

    api.createPortfolio.and.returnValue(of(created));
    const firstKey = api.createPortfolio.calls.first().args[1];
    component.save();
    const retryKey = api.createPortfolio.calls.mostRecent().args[1];
    expect(retryKey).toBe(firstKey); // same key reused on retry (FR-031a)
  });
});
