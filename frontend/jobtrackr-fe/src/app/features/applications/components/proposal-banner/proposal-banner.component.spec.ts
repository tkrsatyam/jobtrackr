import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ProposalBannerComponent } from './proposal-banner.component';

describe('ProposalBannerComponent', () => {
  let component: ProposalBannerComponent;
  let fixture: ComponentFixture<ProposalBannerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ProposalBannerComponent],
      providers: [provideRouter([])]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ProposalBannerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
