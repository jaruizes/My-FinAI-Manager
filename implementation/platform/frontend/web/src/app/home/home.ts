import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { HelloService } from '../core/hello.service';

type HomeState =
  | { status: 'loading' }
  | { status: 'success'; version: string }
  | { status: 'error' };

/**
 * The initial application shell (EN001 FR-002). Deliberately empty of product
 * functionality — it only identifies the running application and displays the
 * platform version returned by the backend, so the browser-to-database smoke
 * test can assert it. On failure it shows an explicit error and never a
 * fabricated version (AC-008).
 */
@Component({
  selector: 'app-home',
  templateUrl: './home.html',
  styleUrl: './home.scss',
})
export class Home implements OnInit {
  private readonly hello = inject(HelloService);

  protected readonly state = signal<HomeState>({ status: 'loading' });

  /** The version to render — only ever non-empty in the success state. */
  protected readonly version = computed(() => {
    const current = this.state();
    return current.status === 'success' ? current.version : '';
  });

  ngOnInit(): void {
    this.hello.getPlatformVersion().subscribe({
      next: (response) => {
        const version = response?.version?.trim();
        this.state.set(
          version ? { status: 'success', version } : { status: 'error' },
        );
      },
      error: () => this.state.set({ status: 'error' }),
    });
  }
}
