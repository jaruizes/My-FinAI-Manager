import { Routes } from '@angular/router';
import { HomeComponent } from './home.component';
import { CreatePortfolioPageComponent } from './portfolio/create-portfolio.page';

/**
 * Application routes. Feature Definitions register their routes here.
 *
 * - `''` — placeholder landing view (EN001).
 * - `portfolios/new` — Create Portfolio (FD001).
 */
export const routes: Routes = [
  { path: '', component: HomeComponent, title: 'My-FinAI-Manager' },
  {
    path: 'portfolios/new',
    component: CreatePortfolioPageComponent,
    title: 'Create portfolio · My-FinAI-Manager',
  },
  { path: '**', redirectTo: '' },
];
