import { Component, input, output } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { TitleCaseTagPipe } from '../../pipes/title-case-tag/title-case-tag-pipe';

@Component({
  selector: 'app-tag-chip',
  imports: [
    MatChipsModule,
    MatIconModule,
    TitleCaseTagPipe
  ],
  templateUrl: './tag-chip.component.html',
  styleUrl: './tag-chip.component.scss',
})
export class TagChipComponent {
  tag = input.required<string>();
  removable = input(false);
  removed = output<string>();
}
