import { Component } from '@angular/core';
import { PortfolioListComponent } from './portfolio/portfolio-list.component';

/**
 * The default route (`''`). Since FD003 the Home page shows the investor's saved portfolios
 * (FD003 §17.1) — it hosts {@link PortfolioListComponent}. `HomeComponent` stays as the route
 * host so the route registration is stable.
 */
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [PortfolioListComponent],
  template: `<app-portfolio-list />`,
})
export class HomeComponent {}
