import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'titleCaseTag',
})
export class TitleCaseTagPipe implements PipeTransform {
  transform(tag: string): string {
    return tag.split(' ')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }
}
