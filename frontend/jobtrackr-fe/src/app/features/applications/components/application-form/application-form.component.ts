import { Component, inject, input, OnInit, output, ViewChild } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatNativeDateModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { TagInputComponent } from '../tag-input/tag-input.component';
import { ApplicationResponse, ApplicationSource, ApplicationStatus, CreateApplicationRequest, PriorityLevel, UpdateApplicationRequest, WorkMode } from '../../../../shared/models/application.model';
import { ACTIVE_STATUSES, ALL_PRIORITIES, ALL_SOURCES, ALL_WORK_MODES, PRIORITY_LABELS, SOURCE_LABELS, STATUS_LABELS, WORK_MODE_LABELS } from '../../../../shared/constants/enum-labels';

interface SelectFieldConfig {
  key: string;
  label: string;
  options: { value: string; label: string }[];
  showInEditMode: boolean;
}

@Component({
  selector: 'app-application-form',
  imports: [
    ReactiveFormsModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    TagInputComponent
  ],
  templateUrl: './application-form.component.html',
  styleUrl: './application-form.component.scss',
})
export class ApplicationFormComponent implements OnInit{
  private fb = inject(NonNullableFormBuilder);

  initialValue = input<ApplicationResponse | null>(null);
  submitting = input(false);
  formSubmit = output<CreateApplicationRequest | UpdateApplicationRequest>();

  @ViewChild(TagInputComponent) tagInput!: TagInputComponent;

  form = this.fb.group({
    companyName: ['', Validators.required],
    role: ['', Validators.required],
    jobUrl: [''],
    status: ['APPLIED' as ApplicationStatus],
    priority: ['MEDIUM' as PriorityLevel],
    workMode: ['' as WorkMode | ''],
    location: [''],
    salaryMin: [null as number | null],
    salaryMax: [null as number | null],
    currency: ['INR'],
    appliedDate: [null as Date | null],
    source: ['OTHER' as ApplicationSource],
    notes: ['']
  });

  selectFields: SelectFieldConfig[] = [
    {
      key: 'status',
      label: 'Status',
      options: ACTIVE_STATUSES.map(status => ({ value: status, label: STATUS_LABELS[status] })),
      showInEditMode: false
    },
    {
      key: 'priority',
      label: 'Priority',
      options: ALL_PRIORITIES.map(priority => ({ value: priority, label: PRIORITY_LABELS[priority] })),
      showInEditMode: true
    },
    {
      key: 'workMode',
      label: 'Work Mode',
      options: ALL_WORK_MODES.map(workMode => ({ value: workMode, label: WORK_MODE_LABELS[workMode] })),
      showInEditMode: true
    },
    {
      key: 'source',
      label: 'Source',
      options: ALL_SOURCES.map(source => ({ value: source, label: SOURCE_LABELS[source] })),
      showInEditMode: true
    }
  ];

  ngOnInit(): void {
    const value = this.initialValue();
    if (value) {
      this.form.patchValue({
        companyName: value.companyName,
        role: value.role,
        jobUrl: value.jobUrl ?? '',
        priority: value.priority,
        workMode: value.workMode ?? '',
        location: value.location ?? '',
        salaryMin: value.salaryMin,
        salaryMax: value.salaryMax,
        currency: value.currency,
        appliedDate: value.appliedDate? new Date(value.appliedDate) : null,
        source: value.source,
        notes: value.notes ?? ''
      })
    }
  }

  submit(): void {
    if (this.form.invalid) return;
    const raw = this.form.getRawValue();

    const request: CreateApplicationRequest = {
      companyName: raw.companyName,
      role: raw.role,
      jobUrl: raw.jobUrl || undefined,
      status: raw.status as ApplicationStatus,
      priority: raw.priority as PriorityLevel,
      workMode: (raw.workMode as WorkMode) || undefined,
      location: raw.location || undefined,
      salaryMin: raw.salaryMin ?? undefined,
      salaryMax: raw.salaryMax ?? undefined,
      currency: raw.currency,
      appliedDate: raw.appliedDate
        ? (raw.appliedDate as Date).toISOString().split('T')[0]
        : undefined,
      source: raw.source as ApplicationSource,
      notes: raw.notes || undefined,
      tags: this.tagInput.getCurrentTags()
    };

    this.formSubmit.emit(request);
  }
}
