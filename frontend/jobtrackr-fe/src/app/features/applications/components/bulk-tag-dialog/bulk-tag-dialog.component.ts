import { Component, inject, signal } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { TagInputComponent } from '../tag-input/tag-input.component';

export interface BulkTagDialogData {
  selectedCount: number;
}

@Component({
  selector: 'app-bulk-tag-dialog',
  imports: [
    MatDialogModule,
    MatButtonModule,
    TagInputComponent
  ],
  templateUrl: './bulk-tag-dialog.component.html',
  styleUrl: './bulk-tag-dialog.component.scss',
})
export class BulkTagDialogComponent {
  data = inject<BulkTagDialogData>(MAT_DIALOG_DATA);
  dialogRef = inject(MatDialogRef<BulkTagDialogComponent>);

  tags = signal<string[]>([]);

  onTagsChange(tags: string[]): void {
    this.tags.set(tags);
  }

  confirm(): void {
    if (this.tags().length === 0) return;
    this.dialogRef.close(this.tags());
  }
}