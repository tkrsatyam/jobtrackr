import { inject, Injectable, signal, computed } from '@angular/core';
import { ApplicationService } from './application.service';
import { ApplicationResponse, ConfirmReviewRequest } from '../../../shared/models/application.model';
import { Observable, Subject, tap } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class PendingReviewService {
  private appService = inject(ApplicationService);

  readonly count = signal<number>(0);
  readonly pendingList = signal<ApplicationResponse[]>([]);
  readonly loading = signal<boolean>(false);
  readonly hasPending = computed(() => this.count() > 0);

  // Subject to notify components when reviews are updated/completed
  private reviewActionSubject = new Subject<void>();
  readonly reviewAction$ = this.reviewActionSubject.asObservable();

  refreshCount(): Observable<{ count: number }> {
    return this.appService.getPendingReviewCount().pipe(
      tap(res => {
        this.count.set(res.count);
      })
    );
  }

  loadPendingList(): Observable<ApplicationResponse[]> {
    this.loading.set(true);
    return this.appService.getPendingReview().pipe(
      tap({
        next: list => {
          this.pendingList.set(list);
          this.count.set(list.length);
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
        }
      })
    );
  }

  confirm(id: string, req: ConfirmReviewRequest): Observable<ApplicationResponse> {
    return this.appService.confirmReview(id, req).pipe(
      tap(() => {
        this.pendingList.update(list => list.filter(item => item.applicationId !== id));
        this.count.update(c => Math.max(0, c - 1));
        this.reviewActionSubject.next();
      })
    );
  }

  reject(id: string): Observable<void> {
    return this.appService.rejectReview(id).pipe(
      tap(() => {
        this.pendingList.update(list => list.filter(item => item.applicationId !== id));
        this.count.update(c => Math.max(0, c - 1));
        this.reviewActionSubject.next();
      })
    );
  }
}
