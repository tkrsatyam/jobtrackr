import { effect, Injectable, signal } from '@angular/core';

const STORAGE_KEY = 'jobtrackr_sidebar';

@Injectable({
  providedIn: 'root',
})
export class SidebarService {
  readonly isCollapsed = signal<boolean>(this.readFromStorage());

  constructor() {
    effect(() => {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ isCollapsed: this.isCollapsed() })
      );
    });
  }

  toggle(): void {
    this.isCollapsed.update(v => !v);
  }

  private readFromStorage(): boolean {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw).isCollaped : false;
  }
}
