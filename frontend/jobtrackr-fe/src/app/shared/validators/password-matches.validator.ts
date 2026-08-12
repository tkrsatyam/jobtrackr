import { AbstractControl, ValidationErrors } from "@angular/forms";

export function confirmPasswordValidator(control: AbstractControl): ValidationErrors | null {
    const newPassword = control.parent?.get('newPassword')?.value;
    return !newPassword || control.value === newPassword ? null : { passwordMismatch: true };
}