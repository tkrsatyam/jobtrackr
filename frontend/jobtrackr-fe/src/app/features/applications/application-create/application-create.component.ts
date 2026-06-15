import { Component, inject, signal } from '@angular/core';
import { ApplicationService } from '../services/application.service';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { CreateApplicationRequest, UpdateApplicationRequest } from '../../../shared/models/application.model';
import { ApplicationFormComponent } from "../components/application-form/application-form.component";

@Component({
  selector: 'app-application-create',
  imports: [ApplicationFormComponent],
  templateUrl: './application-create.component.html',
  styleUrl: './application-create.component.scss',
})
export class ApplicationCreateComponent {
  private appService = inject(ApplicationService);
  private router = inject(Router);
  private snackBar = inject(MatSnackBar);

  submitting = signal(false);

  onSubmit(request: CreateApplicationRequest | UpdateApplicationRequest): void {
    this.submitting.set(true);
    this.appService.create(request as CreateApplicationRequest).subscribe({
      next: app => {
        this.snackBar.open('Application created', 'OK', { duration: 3000 });
        this.router.navigate(['/applications', app.applicationId]);
      },
      error: () => this.submitting.set(false)
    });
  }
}