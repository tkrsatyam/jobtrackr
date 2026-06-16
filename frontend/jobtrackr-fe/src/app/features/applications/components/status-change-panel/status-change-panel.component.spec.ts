import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StatusChangePanelComponent } from './status-change-panel.component';

describe('StatusChangePanelComponent', () => {
  let component: StatusChangePanelComponent;
  let fixture: ComponentFixture<StatusChangePanelComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusChangePanelComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(StatusChangePanelComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
