import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BulkActionToolbarComponent } from './bulk-action-toolbar.component';

describe('BulkActionToolbarComponent', () => {
  let component: BulkActionToolbarComponent;
  let fixture: ComponentFixture<BulkActionToolbarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BulkActionToolbarComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(BulkActionToolbarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
