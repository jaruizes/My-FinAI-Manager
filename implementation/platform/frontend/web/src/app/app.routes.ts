import { Routes } from '@angular/router';
import { HomeComponent } from './home.component';

/**
 * Routing foundation (EN001). One placeholder route so the shell renders a non-blank content
 * area. Feature Definitions register their routes here (or via lazy-loaded child routes).
 */
export const routes: Routes = [
  { path: '', component: HomeComponent, title: 'My-FinAI-Manager' },
  { path: '**', redirectTo: '' },
];
