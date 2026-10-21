import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { Oauth2CallbackComponent } from './oauth2-callback.component';
import { AuthService } from '../services/auth.service';

describe('Oauth2CallbackComponent', () => {
  let component: Oauth2CallbackComponent;
  let fixture: ComponentFixture<Oauth2CallbackComponent>;
  let mockRouter: { navigate: ReturnType<typeof vi.fn> };
  let mockAuthService: {
    handleOAuthCallback: ReturnType<typeof vi.fn>;
    getProfile: ReturnType<typeof vi.fn>;
  };
  let queryParamsMap: Map<string, string>;

  beforeEach(async () => {
    mockRouter = { navigate: vi.fn() };
    mockAuthService = {
      handleOAuthCallback: vi.fn(),
      getProfile: vi.fn().mockReturnValue(of({ id: '123', email: 'test@example.com' }))
    };
    queryParamsMap = new Map();

    const mockActivatedRoute = {
      snapshot: {
        queryParamMap: {
          get: (key: string) => queryParamsMap.get(key) ?? null
        }
      }
    };

    await TestBed.configureTestingModule({
      imports: [Oauth2CallbackComponent],
      providers: [
        { provide: Router, useValue: mockRouter },
        { provide: AuthService, useValue: mockAuthService },
        { provide: ActivatedRoute, useValue: mockActivatedRoute }
      ]
    }).compileComponents();
  });

  it('should process tokens, update auth service, and navigate to dashboard when tokens present', () => {
    queryParamsMap.set('accessToken', 'mock-access');
    queryParamsMap.set('refreshToken', 'mock-refresh');

    fixture = TestBed.createComponent(Oauth2CallbackComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(mockAuthService.handleOAuthCallback).toHaveBeenCalledWith('mock-access', 'mock-refresh');
    expect(mockAuthService.getProfile).toHaveBeenCalled();
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('should redirect to login with error message when tokens are missing', () => {
    queryParamsMap.set('error', 'access_denied');

    fixture = TestBed.createComponent(Oauth2CallbackComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(mockAuthService.handleOAuthCallback).not.toHaveBeenCalled();
    expect(mockRouter.navigate).toHaveBeenCalledWith(['/login'], {
      queryParams: { error: 'access_denied' }
    });
  });
});
