import { Routes } from '@angular/router';
import { HomeComponent } from './home.component';
import { CreatePortfolioPageComponent } from './portfolio/create-portfolio.page';
import { PortfolioDetailPageComponent } from './portfolio/portfolio-detail.page';

/**
 * Application routes. Feature Definitions register their routes here.
 *
 * - `''` — Home: the investor's saved portfolios (FD003).
 * - `portfolios/new` — Create Portfolio (FD001).
 * - `portfolios/:id` — a portfolio's detail with its positions (FD003).
 */
export const routes: Routes = [
  { path: '', component: HomeComponent, title: 'Portfolios · My-FinAI-Manager' },
  {
    path: 'portfolios/new',
    component: CreatePortfolioPageComponent,
    title: 'Create portfolio · My-FinAI-Manager',
  },
  {
    path: 'portfolios/:id',
    component: PortfolioDetailPageComponent,
    title: 'Portfolio · My-FinAI-Manager',
  },
  { path: '**', redirectTo: '' },
];
