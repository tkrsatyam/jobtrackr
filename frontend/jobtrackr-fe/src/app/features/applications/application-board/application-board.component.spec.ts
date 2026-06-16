import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ApplicationBoardComponent } from './application-board.component';

describe('ApplicationBoardComponent', () => {
  let component: ApplicationBoardComponent;
  let fixture: ComponentFixture<ApplicationBoardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ApplicationBoardComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ApplicationBoardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
