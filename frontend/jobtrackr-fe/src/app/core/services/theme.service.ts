import { DOCUMENT, effect, inject, Injectable, signal } from '@angular/core';

export type Theme = 'light' | 'dark';

const STORAGE_KEY = 'jt-theme';

@Injectable({
  providedIn: 'root',
})
export class ThemeService {
  private document = inject(DOCUMENT);

  readonly theme = signal<Theme>(this.resolveInitialTheme());

  constructor() {
    effect(() => {
      const current = this.theme();
      this.applyTheme(current);
      localStorage.setItem(STORAGE_KEY, current);
    });
  }

  toggle(): void {
    this.theme.update(t => t === 'light' ? 'dark' : 'light');
  }

  private resolveInitialTheme(): Theme {
    const stored = localStorage.getItem(STORAGE_KEY) as Theme | null;
    if (stored === 'light' || stored === 'dark') return stored;
    const value = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    console.log('initial theme: ', value);
    return value;
  }

  private applyTheme(theme: Theme): void {
    const root = this.document.documentElement;
    root.classList.toggle('dark-theme', theme === 'dark');
  }
}
