import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { NavigationEnd, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../features/auth/services/auth.service';
import { MatDialog } from '@angular/material/dialog';
import { ConfirmDialogComponent } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { ThemeService } from '../../core/services/theme.service';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApplicationService } from '../../features/applications/services/application.service';
import { ApplicationSearchResult } from '../../shared/models/application.model';
import { StatusBadgeComponent } from '../../shared/components/status-badge/status-badge.component';
import { Subject, Subscription } from 'rxjs';
import { debounceTime, distinctUntilChanged, filter, switchMap } from 'rxjs/operators';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-topbar',
  imports: [
    RouterLink,
    FormsModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatTooltipModule,
    StatusBadgeComponent
  ],
  templateUrl: './topbar.component.html',
  styleUrl: './topbar.component.scss',
})
export class TopbarComponent implements OnInit, OnDestroy {
  private authService = inject(AuthService);
  private dialog = inject(MatDialog);
  private router = inject(Router);
  private appService = inject(ApplicationService);
  readonly themeService = inject(ThemeService);

  currentUser = this.authService.currentUser;

  searchTerm = '';
  searchResults: ApplicationSearchResult[] = [];
  showDropdown = false;
  searching = false;

  private searchInput$ = new Subject<string>();
  private sub = new Subscription();

  ngOnInit(): void {
    this.sub.add(
      this.router.events.pipe(
        filter(event => event instanceof NavigationEnd)
      ).subscribe(() => {
        this.searchTerm = '';
        this.searchResults = [];
        this.showDropdown = false;
        this.searching = false;
      })
    );
    this.sub.add(
      this.searchInput$.pipe(
        debounceTime(500),
        distinctUntilChanged(),
        switchMap(term => {
          if (!term.trim()) {
            this.searchResults = [];
            this.showDropdown = false;
            this.searching = false;
            return [];
          }
          this.searching = true;
          return this.appService.search(term);
        })
      ).subscribe({
        next: results => {
          this.searchResults = results;
          this.showDropdown = true;
          this.searching = false;
        },
        error: () => {
          this.searching = false;
          this.showDropdown = false;
        }
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  onSearchInput(): void {
    if (!this.searchTerm.trim()) {
      this.searchResults = [];
      this.showDropdown = false;
      this.searching = false;
    }
    this.searchInput$.next(this.searchTerm);
  }

  onSearchKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && this.searchTerm.trim()) {
      this.searchInput$.next('');
      this.closeDropdown();
      this.router.navigate(['/applications'], { queryParams: { keyword: this.searchTerm.trim() } });
    }
    if (event.key === 'Escape') {
      this.searchInput$.next('');
      this.closeDropdown();
    }
  }

  selectResult(result: ApplicationSearchResult): void {
    this.closeDropdown();
    this.router.navigate(['/applications', result.applicationId]);
  }

  onBlur(): void {
    setTimeout(() => this.closeDropdown(), 150);
  }

  private closeDropdown(): void {
    this.showDropdown = false;
  }

  logout(): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Logout',
        message: 'Are you sure you want to log out?',
        confirmLabel: 'Logout'
      }
    });
    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.authService.logout();
    });
  }
}
