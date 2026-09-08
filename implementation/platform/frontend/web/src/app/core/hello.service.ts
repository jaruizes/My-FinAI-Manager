import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface HelloResponse {
  version: string;
}

/**
 * Calls the platform bootstrap endpoint `GET /api/v1/hello`. The returned
 * version originates from PostgreSQL — the frontend never fabricates it
 * (EN001 BR-001).
 */
@Injectable({ providedIn: 'root' })
export class HelloService {
  private readonly http = inject(HttpClient);

  getPlatformVersion(): Observable<HelloResponse> {
    return this.http.get<HelloResponse>(`${environment.apiBaseUrl}/v1/hello`);
  }
}
