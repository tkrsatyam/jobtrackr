import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { TagInputComponent } from '../tag-input/tag-input.component';

type FilterType = 'select' | 'text' | 'boolean' | 'date';

export interface FilterConfig {
  key: string;
  label: string;
  type: FilterType;
  options?: { value: string; label: string }[];
}

export interface FilterModalData {
  filterConfigs: FilterConfig[];
  filterValues: Record<string, string>;
  tagFilters: string[];
}

export interface FilterModalResult {
  filterValues: Record<string, string>;
  tagFilters: string[];
}

@Component({
  selector: 'app-filter-modal',
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatInputModule,
    MatDatepickerModule,
    MatSlideToggleModule,
    MatIconModule,
    TagInputComponent
  ],
  templateUrl: './filter-modal.component.html',
  styleUrl: './filter-modal.component.scss'
})
export class FilterModalComponent {
  data = inject<FilterModalData>(MAT_DIALOG_DATA);
  private dialogRef = inject(MatDialogRef<FilterModalComponent, FilterModalResult>);

  draftFilterValues = signal<Record<string, string>>({ ...this.data.filterValues });
  draftTagFilters = signal<string[]>([...this.data.tagFilters]);

  getFilterValue(key: string): string {
    return this.draftFilterValues()[key] ?? '';
  }

  getBooleanFilterValue(key: string): boolean {
    return this.draftFilterValues()[key] === 'true';
  }

  getDateFilterValue(key: string): Date | null {
    const val = this.draftFilterValues()[key];
    return val ? new Date(val) : null;
  }

  setFilterValue(key: string, value: string): void {
    this.draftFilterValues.update(current => ({ ...current, [key]: value }));
  }

  toggleBooleanFilter(key: string): void {
    const current = this.draftFilterValues()[key] === 'true';
    this.draftFilterValues.update(v => ({ ...v, [key]: String(!current) }));
  }

  setDateFilterValue(key: string, date: Date | null): void {
    const formatted = date ? date.toISOString().split('T')[0] : '';
    this.draftFilterValues.update(current => ({ ...current, [key]: formatted }));
  }

  onTagFiltersChange(tags: string[]): void {
    this.draftTagFilters.set(tags);
  }

  reset(): void {
  this.draftFilterValues.set({ isArchived: 'false' });
  this.draftTagFilters.set([]);
}

  apply(): void {
    this.dialogRef.close({
      filterValues: this.draftFilterValues(),
      tagFilters: this.draftTagFilters()
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}