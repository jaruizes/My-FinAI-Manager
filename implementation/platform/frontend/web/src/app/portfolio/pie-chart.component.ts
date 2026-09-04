import { NgFor, NgIf } from '@angular/common';
import { Component, computed, input } from '@angular/core';
import { AllocationSlice } from './portfolio.models';
import { percent } from './valuation-format';

interface Arc {
  label: string;
  color: string;
  /** SVG path `d` for a wedge; empty when {@link fullCircle} is true. */
  d: string;
  fullCircle: boolean;
}

interface LegendEntry {
  label: string;
  /** the ORIGINAL backend fraction, formatted — matches the Position table / sector list exactly. */
  percent: string;
  color: string;
}

const CX = 50;
const CY = 50;
const R = 48;
const PALETTE = 8;

/**
 * A self-contained allocation pie chart (FD004 §17.1–§17.4). Given `slices` — `{ label, fraction }`
 * pairs where `fraction` is the **deterministic backend weight** — it draws inline SVG wedges and a
 * text legend. It performs **no** financial calculation: it only normalises the given fractions for
 * geometry and formats them for display (§22 BR-016). No charting dependency.
 *
 * Rendering rules: start at 12 o'clock, clockwise; a single ~100 % slice is a full `<circle>`;
 * slices with `fraction <= 0` are dropped; an empty / all-zero `slices` renders nothing.
 */
@Component({
  selector: 'app-pie-chart',
  standalone: true,
  imports: [NgFor, NgIf],
  template: `
    <figure class="chart" *ngIf="hasData()">
      <figcaption class="chart__title">{{ title() }}</figcaption>
      <svg
        class="chart__svg"
        viewBox="0 0 100 100"
        role="img"
        [attr.aria-label]="ariaLabel()"
      >
        <ng-container *ngFor="let a of arcs()">
          <circle
            *ngIf="a.fullCircle"
            [attr.cx]="CX"
            [attr.cy]="CY"
            [attr.r]="R"
            [attr.fill]="a.color"
          >
            <title>{{ a.label }}</title>
          </circle>
          <path
            *ngIf="!a.fullCircle"
            [attr.d]="a.d"
            [attr.fill]="a.color"
            stroke="var(--color-surface)"
            stroke-width="1"
          >
            <title>{{ a.label }}</title>
          </path>
        </ng-container>
      </svg>
      <ul class="chart__legend">
        <li *ngFor="let e of legend()">
          <span class="chart__swatch" [style.background]="e.color" aria-hidden="true"></span>
          <span class="chart__label">{{ e.label }}</span>
          <span class="chart__pct">{{ e.percent }}</span>
        </li>
      </ul>
    </figure>
  `,
  styles: [
    `
      .chart {
        margin: 0;
        padding: var(--spacing-md);
        background: var(--color-surface-elevated);
        border: 1px solid var(--color-border);
        border-radius: var(--radius-md);
      }
      .chart__title {
        font-size: var(--font-size-section-title, 1.125rem);
        color: var(--color-text-primary);
        margin: 0 0 var(--spacing-sm);
      }
      .chart__svg {
        display: block;
        width: 100%;
        max-width: 220px;
        height: auto;
        margin: 0 auto var(--spacing-sm);
      }
      .chart__legend {
        list-style: none;
        margin: 0;
        padding: 0;
        display: flex;
        flex-direction: column;
        gap: var(--spacing-xs);
      }
      .chart__legend li {
        display: flex;
        align-items: center;
        gap: var(--spacing-sm);
        font-size: var(--font-size-body);
        color: var(--color-text-primary);
      }
      .chart__swatch {
        width: 10px;
        height: 10px;
        border-radius: 2px;
        flex: none;
      }
      .chart__label {
        flex: 1;
      }
      .chart__pct {
        font-variant-numeric: tabular-nums;
        color: var(--color-text-secondary);
      }
    `,
  ],
})
export class PieChartComponent {
  readonly slices = input<AllocationSlice[]>([]);
  readonly title = input('');

  protected readonly CX = CX;
  protected readonly CY = CY;
  protected readonly R = R;

  /** Slices with a positive fraction, in input order. */
  private readonly positive = computed(() => this.slices().filter((s) => s.fraction > 0));

  protected readonly hasData = computed(() => this.positive().length > 0);

  protected readonly legend = computed<LegendEntry[]>(() =>
    this.positive().map((s, i) => ({
      label: s.label,
      percent: percent(String(s.fraction)),
      color: colorAt(i),
    })),
  );

  protected readonly arcs = computed<Arc[]>(() => {
    const slices = this.positive();
    const total = slices.reduce((sum, s) => sum + s.fraction, 0);
    if (total <= 0) {
      return [];
    }
    const out: Arc[] = [];
    let acc = 0;
    slices.forEach((s, i) => {
      const norm = s.fraction / total;
      const a0 = acc;
      const a1 = acc + norm;
      acc = a1;
      const color = colorAt(i);
      if (norm >= 0.999999) {
        out.push({ label: s.label, color, d: '', fullCircle: true });
      } else {
        out.push({ label: s.label, color, d: wedgePath(a0, a1), fullCircle: false });
      }
    });
    return out;
  });

  protected ariaLabel(): string {
    const top = this.legend()
      .slice(0, 3)
      .map((e) => `${e.label} ${e.percent}`)
      .join(', ');
    return this.title() ? `${this.title()}: ${top}` : top;
  }
}

function colorAt(index: number): string {
  return `var(--chart-${(index % PALETTE) + 1})`;
}

/** SVG wedge path for cumulative start/end fractions `a0`..`a1` (0..1), 12 o'clock, clockwise. */
function wedgePath(a0: number, a1: number): string {
  const [x0, y0] = pointAt(a0);
  const [x1, y1] = pointAt(a1);
  const largeArc = a1 - a0 > 0.5 ? 1 : 0;
  return `M ${CX} ${CY} L ${x0} ${y0} A ${R} ${R} 0 ${largeArc} 1 ${x1} ${y1} Z`;
}

function pointAt(fraction: number): [string, string] {
  const theta = 2 * Math.PI * fraction - Math.PI / 2;
  return [round(CX + R * Math.cos(theta)), round(CY + R * Math.sin(theta))];
}

function round(n: number): string {
  return (Math.round(n * 1000) / 1000).toString();
}
