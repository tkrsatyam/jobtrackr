import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { ProposalBannerComponent } from './proposal-banner.component';
import { ApplicationResponse } from '../../../../shared/models/application.model';

describe('ProposalBannerComponent', () => {
  let component: ProposalBannerComponent;
  let fixture: ComponentFixture<ProposalBannerComponent>;

  const mockApp: ApplicationResponse = {
    applicationId: 'app-123',
    userId: 'user-1',
    companyName: 'Google',
    role: 'Staff Software Engineer',
    status: 'APPLIED',
    proposedStatus: 'PHONE_SCREEN',
    statusProposalReason: 'Recruiter phone screen invite',
    statusProposalReceivedAt: '2026-10-24T10:00:00Z',
    isArchived: false,
    isDeleted: false,
    createdAt: '2026-10-20T00:00:00Z',
    updatedAt: '2026-10-24T10:00:00Z',
    tags: [],
    statusHistory: []
  };

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
    expect(component.statusLabels.PHONE_SCREEN).toBe('Phone Screen');
  });

  it('should filter proposals and render user-friendly status label', () => {
    fixture.componentRef.setInput('applications', [mockApp]);
    fixture.detectChanges();

    expect(component.hasProposals()).toBe(true);
    expect(component.count()).toBe(1);
    expect(component.firstProposal().companyName).toBe('Google');

    const compiled = fixture.nativeElement as HTMLElement;
    const chip = compiled.querySelector('.status-chip');
    expect(chip?.textContent?.trim()).toBe('Phone Screen');
  });
});
