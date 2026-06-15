import { Component, inject, OnInit, signal } from '@angular/core';
import { ApplicationService } from '../services/application.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApplicationResponse, UpdateApplicationRequest } from '../../../shared/models/application.model';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ApplicationFormComponent } from '../components/application-form/application-form.component';

@Component({
  selector: 'app-application-edit',
  imports: [
      MatProgressBarModule,
      ApplicationFormComponent
  ],
  templateUrl: './application-edit.component.html',
  styleUrl: './application-edit.component.scss',
})
export class ApplicationEditComponent implements OnInit {
  private appService = inject(ApplicationService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private snackBar = inject(MatSnackBar);

  application = signal<ApplicationResponse | null>(null);
  loading = signal(true);
  submitting = signal(false);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.appService.getById(id).subscribe({
      next: app => {
        this.application.set(app);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  onSubmit(request: UpdateApplicationRequest): void {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.submitting.set(true);
    this.appService.update(id, request).subscribe({
      next: app => {
        this.snackBar.open('Application updated', 'OK', { duration: 3000 });
        this.router.navigate(['/applications', app.applicationId]);
      },
      error: () => this.submitting.set(false)
    });
  }
}
