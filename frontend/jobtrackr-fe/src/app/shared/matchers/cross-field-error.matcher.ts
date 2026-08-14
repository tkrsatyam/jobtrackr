import { ErrorStateMatcher } from '@angular/material/core';
import { FormControl, FormGroupDirective, NgForm, AbstractControl } from '@angular/forms';

/**
 * ErrorStateMatcher that bridges a group-level validation error to a
 * specific field's Material error display.
 *
 * Usage: pass an instance to [errorStateMatcher] on the input, along with
 * the error key to watch on the parent group.
 *
 * Example:
 *   matcher = new CrossFieldErrorMatcher('passwordMismatch');
 *   <input [errorStateMatcher]="matcher" ... >
 */
export class CrossFieldErrorMatcher implements ErrorStateMatcher {
    constructor(private groupErrorKey: string) {}

    isErrorState(
        control: FormControl | AbstractControl | null,
        _form: FormGroupDirective | NgForm | null
    ): boolean {
        const touched = control?.touched ?? false;
        const groupInvalid = control?.parent?.hasError(this.groupErrorKey) ?? false;
        return touched && groupInvalid;
    }
}