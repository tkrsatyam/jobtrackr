import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

export interface BulkTagDialogData {
  selectedCount: number;
}

@Component({
  selector: 'app-bulk-tag-dialog',
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule
  ],
  templateUrl: './bulk-tag-dialog.component.html',
  styleUrl: './bulk-tag-dialog.component.scss',
})
export class BulkTagDialogComponent {
  data = inject<BulkTagDialogData>(MAT_DIALOG_DATA);
  dialogRef = inject(MatDialogRef<BulkTagDialogComponent>);

  tag = signal('');

  confirm(): void {
    const normalized = this.tag().trim().toLowerCase();
    if (!normalized) return;
    this.dialogRef.close(normalized);
  }
}