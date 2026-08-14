import { AbstractControl, ValidationErrors, ValidatorFn } from "@angular/forms";

/**
 * Group-level validator factory. Returns a validator that checks whether
 * two named fields on the FormGroup have equal values.
 *
 * Usage:
 *   this.fb.group({ password: [...], confirmPassword: [...] },
 *     { validators: passwordsMatchValidator('password', 'confirmPassword') })
 */
export function passwordsMatchValidator(field: string, confirmField: string): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
        const fieldValue = control.get(field)?.value;
        const confirmFieldValue = control.get(confirmField)?.value;
        if (!fieldValue || !confirmFieldValue) return null;
        return fieldValue === confirmFieldValue ? null : { passwordMismatch: true };
    };
}