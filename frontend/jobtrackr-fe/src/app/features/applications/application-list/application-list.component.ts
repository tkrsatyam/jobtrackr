import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { PriorityBadgeComponent } from '../../../shared/components/priority-badge/priority-badge.component';
import { TagChipComponent } from '../../../shared/components/tag-chip/tag-chip.component';
import { ApplicationService } from '../services/application.service';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApplicationFilter, ApplicationResponse, ApplicationStatus, PriorityLevel, WorkMode } from '../../../shared/models/application.model';
import { SelectionModel } from '@angular/cdk/collections';
import { ALL_PRIORITIES, ALL_STATUSES, ALL_WORK_MODES, PRIORITY_LABELS, STATUS_LABELS, WORK_MODE_LABELS } from '../../../shared/constants/enum-labels';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { BulkAction, BulkActionToolbarComponent } from '../components/bulk-action-toolbar/bulk-action-toolbar.component';

type FilterType = 'select' | 'text' | 'boolean' | 'date';
type ColumnType = 'text' | 'status' | 'priority' | 'date' | 'tags' | 'select' | 'actions';

interface FilterConfig {
  key: string;
  label: string;
  type: FilterType;
  options?: { value: string; label: string }[];   // only for 'select' type
}

interface ColumnConfig {
  key: string;
  header: string;
  type: ColumnType;
  field?: keyof ApplicationResponse;
}

@Component({
  selector: 'app-application-list',
  imports: [
    RouterLink,
    FormsModule,
    DatePipe,
    MatTableModule,
    MatPaginatorModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatMenuModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatDatepickerModule,
    MatSlideToggleModule,
    StatusBadgeComponent,
    PriorityBadgeComponent,
    TagChipComponent,
    BulkActionToolbarComponent
],
  templateUrl: './application-list.component.html',
  styleUrl: './application-list.component.scss',
})
export class ApplicationListComponent implements OnInit {
  private appService = inject(ApplicationService);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);
  private router = inject(Router);

  applications = signal<ApplicationResponse[]>([]);
  totalElements = signal(0);
  loading = signal(false);

  filterConfigs: FilterConfig[] = [
    {
      key: 'status',
      label: 'Status',
      type: 'select',
      options: ALL_STATUSES.map(status => ({ value: status, label: STATUS_LABELS[status] }))
    },
    {
      key: 'priority',
      label: 'Priority',
      type: 'select',
      options: ALL_PRIORITIES.map(priority => ({ value: priority, label: PRIORITY_LABELS[priority] }))
    },
    {
      key: 'workMode',
      label: 'Work Mode',
      type: 'select',
      options: ALL_WORK_MODES.map(workMode => ({ value: workMode, label: WORK_MODE_LABELS[workMode] }))
    },
    {
      key: 'company',
      label: 'Company',
      type: 'text'
    },
    {
      key: 'role',
      label: 'Role',
      type: 'text'
    },
    {
      key: 'isArchived',
      label: 'Show Archived',
      type: 'boolean'
    },
    {
      key: 'appliedAfter',
      label: 'Applied After',
      type: 'date'
    },
    {
      key: 'appliedBefore',
      label: 'Applied Before',
      type: 'date'
    }
  ];

  filterValues = signal<Record<string, string>>({
    status: '',
    priority: '',
    workMode: '',
    company: '',
    role: '',
    isArchived: 'false',
    appliedAfter: '',
    appliedBefore: ''
  });

  page = signal(0);
  pageSize = signal(20);

  selection = new SelectionModel<string>(true, []);
  selectedCount = computed(() => this.selection.selected.length);

  columns: ColumnConfig[] = [
    { key: 'select', header: '', type: 'select' },
    { key: 'company', header: 'Company' , type: 'text', field: 'companyName' },
    { key: 'role', header: 'Role' , type: 'text', field: 'role' },
    { key: 'status', header: 'Status' , type: 'status' },
    { key: 'priority', header: 'Priority' , type: 'priority' },
    { key: 'appliedDate', header: 'Applied' , type: 'date', field: 'appliedDate' },
    { key: 'tags', header: 'Tags' , type: 'tags' },
    { key: 'actions', header: 'Actions' , type: 'actions' }
  ];

  displayedColumns = this.columns.map(column => column.key);
  allStatuses = ALL_STATUSES;
  allPriorities = ALL_PRIORITIES;
  allWorkModes = ALL_WORK_MODES;
  statusLabels = STATUS_LABELS;
  priorityLabels = PRIORITY_LABELS;
  workModeLabels = WORK_MODE_LABELS;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.selection.clear();

    const values = this.filterValues();
    const filter: ApplicationFilter = {
      page: this.page(),
      size: this.pageSize(),
      sortBy: 'createdAt',
      sortDir: 'desc'
    };

    if (values['status']) filter.status = values['status'] as ApplicationStatus;
    if (values['priority']) filter.priority = values['priority'] as PriorityLevel;
    if (values['workMode']) filter.workMode = values['workMode'] as WorkMode;
    if (values['company']) filter.company = values['company'];
    if (values['role']) filter.role = values['role'];
    if (values['isArchived'] === 'true') filter.isArchived = true;
    if (values['appliedAfter']) filter.appliedAfter = values['appliedAfter'];
    if (values['appliedBefore']) filter.appliedBefore = values['appliedBefore'];

    this.appService.getApplications(filter).subscribe({
      next: page => {
        this.applications.set(page.content);
        this.totalElements.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  getFilterValue(key: string): string {
    return this.filterValues()[key] ?? '';
  }

  getBooleanFilterValue(key: string): boolean {
    return this.filterValues()[key] === 'true';
  }

  getDateFilterValue(key: string): Date | null {
    const val = this.filterValues()[key];
    return val ? new Date(val) : null;
  }

  setFilterValue(key: string, value: string): void {
    this.filterValues.update(current => ({ ...current, [key]: value }));
    this.applyFilters();
  }

  toggleBooleanFilter(key: string): void {
    const current = this.filterValues()[key] === 'true';
    this.filterValues.update(values => ({ ...values, [key]: String(!current) }));
    this.applyFilters();
  }

  setDateFilterValue(key: string, date: Date | null): void {
    const formatted = date ? date.toISOString().split('T')[0] : '';
    this.filterValues.update(current => ({ ...current, [key]: formatted }));
    this.applyFilters();
  }

  applyFilters(): void {
    this.page.set(0);
    this.load();
  }

  resetFilters(): void {
    this.filterValues.set({
      status: '',
      priority: '',
      workMode: '',
      company: '',
      role: '',
      isArchived: 'false',
      appliedAfter: '',
      appliedBefore: ''
    });
    this.page.set(0);
    this.load();
  }

  onPageChange(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  toggleSelectAll(): void {
    if (this.selection.selected.length === this.applications().length) {
      this.selection.clear();
    } else {
      this.applications().forEach(a => this.selection.select(a.applicationId));
    }
  }

  isAllSelected(): boolean {
    return this.selection.selected.length === this.applications().length && this.applications().length > 0;
  }

  delete(id: string): void {
    const ref = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Delete Application',
        message: 'This application will be permanently removed from your list. This cannot be undone.',
        confirmLabel: 'Delete',
        destructive: true
      }
    });
    ref.afterClosed().subscribe(confirmed => {
      if (!confirmed) return;
      this.appService.delete(id).subscribe(() => {
        this.snackBar.open('Application deleted', 'OK', { duration: 3000 });
        this.load();
      });
    });
  }

  archive(id:string): void {
    this.appService.toggleArchive(id).subscribe(() => {
      this.snackBar.open('Archive status updated', 'OK', { duration: 3000 });
      this.load();
    });
  }

  onBulkAction(action: BulkAction): void {
    const ids = this.selection.selected;

    if (action.type === 'delete') {
      const ref = this.dialog.open(ConfirmDialogComponent, {
        data: {
          title: `Delete ${ids.length} Applications`,
          message: `Are you sure you want to delete ${ids.length} applications? This cannot be undone.`,
          confirmLabel: 'Delete All',
          destructive: true
        }
      });
      ref.afterClosed().subscribe(confirmed => {
        if (!confirmed) return;
        this.appService.bulkDelete(ids).subscribe(() => {
          this.snackBar.open(`${ids.length} applications deleted`, 'OK', { duration: 3000 });
          this.load();
        });
      });
    }

    if (action.type == 'archive') {
      this.appService.bulkArchive(ids).subscribe(() => {
        this.snackBar.open(`${ids.length} applications archived`, 'OK', { duration: 3000 });
        this.load();
      });
    }
    
    if (action.type == 'status') {
      this.appService.bulkChangeStatus(ids, action.status).subscribe(() => {
        this.snackBar.open(`Status updated for ${ids.length} applications`, 'OK', { duration: 3000 });
        this.load();
      })
    }
  }

}
