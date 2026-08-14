import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { PriorityBadgeComponent } from '../../../shared/components/priority-badge/priority-badge.component';
import { TagChipComponent } from '../../../shared/components/tag-chip/tag-chip.component';
import { ApplicationService } from '../services/application.service';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ApplicationFilter, ApplicationResponse, ApplicationStatus, PriorityLevel, WorkMode } from '../../../shared/models/application.model';
import { ALL_PRIORITIES, ALL_STATUSES, ALL_WORK_MODES, PRIORITY_LABELS, STATUS_LABELS, WORK_MODE_LABELS } from '../../../shared/constants/enum-labels';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { BulkAction, BulkActionToolbarComponent } from '../components/bulk-action-toolbar/bulk-action-toolbar.component';
import { MatSortModule, Sort } from '@angular/material/sort';
import { FilterConfig, FilterModalComponent, FilterModalResult } from '../components/filter-modal/filter-modal.component';
import { MatChipsModule } from "@angular/material/chips";
import { BulkTagDialogComponent } from '../components/bulk-tag-dialog/bulk-tag-dialog.component';

type ColumnType = 'text' | 'status' | 'priority' | 'date' | 'tags' | 'select' | 'actions';

interface ColumnConfig {
  key: string;
  header: string;
  type: ColumnType;
  field?: keyof ApplicationResponse;
  sortable?: boolean;
  sortField?: string;
}

@Component({
  selector: 'app-application-list',
  imports: [
    RouterLink,
    FormsModule,
    DatePipe,
    MatTableModule,
    MatSortModule,
    MatPaginatorModule,
    MatCheckboxModule,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    MatMenuModule,
    MatProgressBarModule,
    StatusBadgeComponent,
    PriorityBadgeComponent,
    TagChipComponent,
    BulkActionToolbarComponent,
    MatChipsModule
],
  templateUrl: './application-list.component.html',
  styleUrl: './application-list.component.scss',
})
export class ApplicationListComponent implements OnInit {
  private appService = inject(ApplicationService);
  private dialog = inject(MatDialog);
  private snackBar = inject(MatSnackBar);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  applications = signal<ApplicationResponse[]>([]);
  totalElements = signal(0);
  loading = signal(false);

  sortState = signal<{ active: string; direction: 'asc' | 'desc' }>({
    active: 'createdAt',
    direction: 'desc'
  });

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
//     {
//       key: 'company',
//       label: 'Company',
//       type: 'text'
//     },
//     {
//       key: 'role',
//       label: 'Role',
//       type: 'text'
//     },
    {
      key: 'appliedAfter',
      label: 'Applied After',
      type: 'date'
    },
    {
      key: 'appliedBefore',
      label: 'Applied Before',
      type: 'date'
    },
    {
      key: 'isArchived',
      label: 'Show Archived',
      type: 'boolean'
    }
  ];

  filterValues = signal<Record<string, string>>({
    keyword: '',
    status: '',
    priority: '',
    workMode: '',
    company: '',
    role: '',
    isArchived: 'false',
    appliedAfter: '',
    appliedBefore: ''
  });
  tagFilters = signal<string[]>([]);

  page = signal(0);
  pageSize = signal(20);

  selectedIds = signal<Set<string>>(new Set());
  selectedCount = computed(() => this.selectedIds().size);

  activeFilterChips = computed(() => {
    const chips: { key: string; label: string; displayValue: string }[] = [];
    const values = this.filterValues();

    if (values['keyword']) {
      chips.push({ key: 'keyword', label: 'Search', displayValue: values['keyword'] });
    }

    for (const config of this.filterConfigs) {
      const val = values[config.key];
      if (!val || val === '' || (config.type === 'boolean' && val !== 'true')) continue;

      let displayValue = val;
      if (config.options) {
        displayValue = config.options.find(o => o.value === val)?.label ?? val;
      }

      chips.push({ key: config.key, label: config.label, displayValue });
    }

    for (const tag of this.tagFilters()) {
      chips.push({ key: `tag:${tag}`, label: 'Tag', displayValue: tag });
    }

    return chips;
  });

  columns: ColumnConfig[] = [
    { key: 'select', header: '', type: 'select' },
    { key: 'company', header: 'Company' , type: 'text', field: 'companyName', sortable: true, sortField: 'companyName' },
    { key: 'role', header: 'Role' , type: 'text', field: 'role', sortable: true, sortField: 'role' },
    { key: 'status', header: 'Status' , type: 'status', sortable: false },
    { key: 'priority', header: 'Priority' , type: 'priority', sortable: false },
    { key: 'appliedDate', header: 'Applied' , type: 'date', field: 'appliedDate', sortable: true, sortField: 'appliedDate' },
    { key: 'createdAt', header: 'Created', type: 'date', field: 'createdAt', sortable: true, sortField: 'createdAt'},
    { key: 'tags', header: 'Tags' , type: 'tags', sortable: false },
    { key: 'actions', header: 'Actions' , type: 'actions', sortable: false }
  ];

  displayedColumns = this.columns.map(column => column.key);

  readonly applicationFilter = computed<ApplicationFilter>(() => {
    const values = this.filterValues();
    const sort = this.sortState();

    const filter: ApplicationFilter = {
      page: this.page(),
      size: this.pageSize(),
      sortBy: sort.active,
      sortDir: sort.direction
    };

    if (values['keyword']) filter.keyword = values['keyword'];
    if (values['status']) filter.status = values['status'] as ApplicationStatus;
    if (values['priority']) filter.priority = values['priority'] as PriorityLevel;
    if (values['workMode']) filter.workMode = values['workMode'] as WorkMode;
    if (values['company']) filter.company = values['company'];
    if (values['role']) filter.role = values['role'];
    if (values['isArchived'] === 'true') filter.isArchived = true;
    if (values['appliedAfter']) filter.appliedAfter = values['appliedAfter'];
    if (values['appliedBefore']) filter.appliedBefore = values['appliedBefore'];
    if (this.tagFilters().length > 0) filter.tags = this.tagFilters();

    return filter;
  })

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      const keyword = params.get('keyword') ?? '';
      this.filterValues.update(v => ({ ...v, keyword }));
      this.load();
    });
  }

  load(): void {
    this.loading.set(true);
    this.selectedIds.set(new Set());

    this.appService.getApplications(this.applicationFilter()).subscribe({
      next: page => {
        this.applications.set(page.content);
        this.totalElements.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false)
    });
  }

  onSortChange(sort: Sort): void {
    this.sortState.set(
      sort.direction
        ? { active: sort.active, direction: sort.direction as 'asc' | 'desc' }
        : { active: 'createdAt', direction: 'desc' }
    );
    this.page.set(0);
    this.load();
  }

  openFilterModal(): void {
    const ref = this.dialog.open<FilterModalComponent, unknown, FilterModalResult>(FilterModalComponent, {
      data: {
        filterConfigs: this.filterConfigs,
        filterValues: this.filterValues(),
        tagFilters: this.tagFilters()
      },
      width: '520px'
    });

    ref.afterClosed().subscribe(result => {
      if (!result) return;
      this.filterValues.set(result.filterValues);
      this.tagFilters.set(result.tagFilters);
      this.applyFilters();
    });
  }

  removeFilterChip(key: string): void {
    if (key.startsWith('tag:')) {
      const tag = key.slice(4);
      this.tagFilters.update(tags => tags.filter(t => t !== tag));
    } else {
      const isBoolean = this.filterConfigs.find(c => c.key === key)?.type === 'boolean';
      this.filterValues.update(v => ({ ...v, [key]: isBoolean ? 'false' : '' }));
    }
    this.applyFilters();
  }

  applyFilters(): void {
    this.page.set(0);
    this.load();
  }

  onPageChange(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  isSelected(id: string): boolean {
    return this.selectedIds().has(id);
  }

  toggleSelection(id: string): void {
    this.selectedIds.update(set => {
      const next = new Set(set);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  isAllSelected(): boolean {
    const ids = this.selectedIds();
    const apps = this.applications();
    return apps.length > 0 && apps.every(app => ids.has(app.applicationId));
  }

  toggleSelectAll(): void {
    if (this.isAllSelected()) {
      this.selectedIds.set(new Set());
    } else {
      this.selectedIds.set(new Set(this.applications().map(app => app.applicationId)));
    }
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
    const ids = [...this.selectedIds()];

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

    if (action.type === 'tag') {
      const ref = this.dialog.open(BulkTagDialogComponent, {
        data: { selectedCount: ids.length }
      });
      ref.afterClosed().subscribe((tag: string | undefined) => {
        if (!tag) return;
        this.appService.bulkAddTag(ids, tag).subscribe(() => {
          this.snackBar.open(`Tag added to ${ids.length} applications`, 'OK', { duration: 3000 });
          this.load();
        });
      });
    }
  }

}
