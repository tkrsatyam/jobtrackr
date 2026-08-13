import { AbstractControl, ValidationErrors } from "@angular/forms";

export function passwordsMatchValidator(control: AbstractControl): ValidationErrors | null {
    const newPassword = control.get('newPassword')?.value;
    const confirmNewPassword = control.get('confirmNewPassword')?.value;
    if (!newPassword || !confirmNewPassword) return null;
    return newPassword === confirmNewPassword ? null : { passwordMismatch: true };
}