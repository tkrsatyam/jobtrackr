import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BulkTagDialogComponent } from './bulk-tag-dialog.component';

describe('BulkTagDialogComponent', () => {
  let component: BulkTagDialogComponent;
  let fixture: ComponentFixture<BulkTagDialogComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [BulkTagDialogComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(BulkTagDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
