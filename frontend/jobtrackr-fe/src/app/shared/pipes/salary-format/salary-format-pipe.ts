import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'salaryFormat',
})
export class SalaryFormatPipe implements PipeTransform {
  transform(value: number | null, currency: string = 'INR'): string {
    if (value === null) return '—';
    const symbol = currency === 'INR' ? '₹' : currency;
    return `${symbol}${value.toLocaleString('en-IN')}`;
  }
}
