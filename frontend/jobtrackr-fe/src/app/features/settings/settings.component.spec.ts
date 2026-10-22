import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { Observable, of } from 'rxjs';
import { signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { SettingsComponent } from './settings.component';
import { AuthService } from '../auth/services/auth.service';
import { TokenStorageService } from '../../core/services/token-storage.service';
import { UserProfile } from '../../shared/models/auth.model';

describe('SettingsComponent', () => {
  let component: SettingsComponent;
  let fixture: ComponentFixture<SettingsComponent>;

  let currentUserSignal: ReturnType<typeof signal<UserProfile | null>>;
  let mockAuthService: {
    currentUser: ReturnType<typeof signal<UserProfile | null>>;
    getProfile: ReturnType<typeof vi.fn>;
    updateProfile: ReturnType<typeof vi.fn>;
    changePassword: ReturnType<typeof vi.fn>;
    deleteAccount: ReturnType<typeof vi.fn>;
    initLinkGoogle: ReturnType<typeof vi.fn>;
    unlinkGoogle: ReturnType<typeof vi.fn>;
  };
  let mockTokenStorage: {
    clearTokens: ReturnType<typeof vi.fn>;
    saveTokens: ReturnType<typeof vi.fn>;
  };
  let mockDialog: { open: ReturnType<typeof vi.fn> };
  let mockSnackBar: { open: ReturnType<typeof vi.fn> };
  let mockRouter: { navigate: ReturnType<typeof vi.fn> };
  let queryParams$: Observable<Params>;

  const defaultUser: UserProfile = {
    id: 'user-123',
    email: 'test@jobtrackr.com',
    fullName: 'Test User',
    avatarUrl: null,
    role: 'USER',
    provider: 'LOCAL',
    googleConnected: false,
    googleEmail: null
  };

  beforeEach(async () => {
    currentUserSignal = signal<UserProfile | null>(defaultUser);
    queryParams$ = of({});

    mockAuthService = {
      currentUser: currentUserSignal,
      getProfile: vi.fn().mockReturnValue(of(defaultUser)),
      updateProfile: vi.fn().mockReturnValue(of(defaultUser)),
      changePassword: vi.fn().mockReturnValue(of(void 0)),
      deleteAccount: vi.fn().mockReturnValue(of(void 0)),
      initLinkGoogle: vi.fn().mockReturnValue(of({ authUrl: '/api/auth/oauth2/authorize/google' })),
      unlinkGoogle: vi.fn().mockReturnValue(of(void 0))
    };

    mockTokenStorage = {
      clearTokens: vi.fn(),
      saveTokens: vi.fn()
    };

    mockDialog = {
      open: vi.fn().mockReturnValue({ afterClosed: () => of(true) })
    };

    mockSnackBar = {
      open: vi.fn()
    };

    mockRouter = {
      navigate: vi.fn()
    };

    await TestBed.configureTestingModule({
      imports: [SettingsComponent],
      providers: [
        { provide: AuthService, useValue: mockAuthService },
        { provide: TokenStorageService, useValue: mockTokenStorage },
        { provide: MatDialog, useValue: mockDialog },
        { provide: MatSnackBar, useValue: mockSnackBar },
        { provide: Router, useValue: mockRouter },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: queryParams$
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(SettingsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should compute isGoogleConnected false for LOCAL user without linked Google', () => {
    expect(component.isGoogleConnected()).toBe(false);
    expect(component.googleEmail()).toBeNull();
  });

  it('should compute isGoogleConnected true when googleConnected is true', () => {
    currentUserSignal.set({
      ...defaultUser,
      googleConnected: true,
      googleEmail: 'linked@gmail.com'
    });
    fixture.detectChanges();

    expect(component.isGoogleConnected()).toBe(true);
    expect(component.googleEmail()).toBe('linked@gmail.com');
  });

  it('should compute isGoogleConnected true for GOOGLE provider user', () => {
    currentUserSignal.set({
      ...defaultUser,
      provider: 'GOOGLE',
      email: 'primary@gmail.com'
    });
    fixture.detectChanges();

    expect(component.isGoogleConnected()).toBe(true);
    expect(component.googleEmail()).toBe('primary@gmail.com');
  });

  it('should call initLinkGoogle on connectGoogle()', () => {
    component.connectGoogle();
    expect(mockAuthService.initLinkGoogle).toHaveBeenCalled();
  });

  it('should open confirm dialog and call unlinkGoogle on disconnectGoogle()', () => {
    component.disconnectGoogle();
    expect(mockDialog.open).toHaveBeenCalled();
    expect(mockAuthService.unlinkGoogle).toHaveBeenCalled();
    expect(mockSnackBar.open).toHaveBeenCalledWith('Google account disconnected', 'OK', { duration: 3000 });
  });

  it('should persist tokens and show snackbar when queryParams contain linked=true and tokens', async () => {
    const route = TestBed.inject(ActivatedRoute);
    (route as { queryParams: Observable<Params> }).queryParams = of({
      linked: 'true',
      accessToken: 'new-acc-tok',
      refreshToken: 'new-ref-tok'
    });

    component.ngOnInit();

    expect(mockTokenStorage.saveTokens).toHaveBeenCalledWith('new-acc-tok', 'new-ref-tok');
    expect(mockSnackBar.open).toHaveBeenCalledWith('Google account connected successfully.', 'OK', { duration: 4000 });
  });
});
