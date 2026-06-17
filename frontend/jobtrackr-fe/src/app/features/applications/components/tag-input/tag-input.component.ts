import { COMMA, ENTER } from '@angular/cdk/keycodes';
import { Component, input, OnInit, output, signal } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TitleCaseTagPipe } from '../../../../shared/pipes/title-case-tag/title-case-tag-pipe';
import { TagChipComponent } from '../../../../shared/components/tag-chip/tag-chip.component';

@Component({
  selector: 'app-tag-input',
  imports: [
    MatChipsModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    TagChipComponent
  ],
  templateUrl: './tag-input.component.html',
  styleUrl: './tag-input.component.scss',
})
export class TagInputComponent implements OnInit {
  
  initialTags = input<string[]>([]);
  tagsChange = output<string[]>();

  tags = signal<string[]>([]);
  separatorKeysCodes = [ENTER, COMMA];

  ngOnInit(): void {
    this.tags.set([...this.initialTags()]);
  }

  addTag(event: any): void {
    const value = (event.value ?? '').trim().toLowerCase();
    if (value && !this.tags().includes(value)) {
      this.tags.update(t => [...t, value]);
      this.tagsChange.emit(this.tags());
    }
    event.chipInput?.clear();
  }

  removeTag(tag: string): void {
    this.tags.update(t => t.filter(x => x !== tag));
    this.tagsChange.emit(this.tags())
  }

  getCurrentTags(): string[] {
    return this.tags();
  }
}
