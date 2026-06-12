import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { environment } from '../../../../environments/environment';
import { ApplicationFilter, ApplicationResponse, ApplicationStatus, BulkIdsRequest, BulkStatusRequest, ChangeStatusRequest, CreateApplicationRequest, Page, StatusHistoryEntry, UpdateApplicationRequest } from '../../../shared/models/application.model';
import { Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class ApplicationService {
  private http = inject(HttpClient);
  private baseUrl = `${environment.apiUrl}/api/applications`;

  getApplications(filter: ApplicationFilter = {}): Observable<Page<ApplicationResponse>> {
    let params = new HttpParams();
    Object.entries(filter).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    });
    return this.http.get<Page<ApplicationResponse>>(this.baseUrl, { params });
  }

  getById(id: string): Observable<ApplicationResponse> {
    return this.http.get<ApplicationResponse>(`${this.baseUrl}/${id}`);
  }

  create(request: CreateApplicationRequest): Observable<ApplicationResponse> {
    return this.http.post<ApplicationResponse>(this.baseUrl, request);
  }

  update(id: string, request: UpdateApplicationRequest): Observable<ApplicationResponse> {
    return this.http.put<ApplicationResponse>(`${this.baseUrl}/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }

  toggleArchive(id: string): Observable<ApplicationResponse> {
    return this.http.put<ApplicationResponse>(`${this.baseUrl}/${id}/archive`, {});
  }

  changeStatus(id: string, request: ChangeStatusRequest): Observable<ApplicationResponse> {
    return this.http.put<ApplicationResponse>(`${this.baseUrl}/${id}/status`, request);
  }

  getStatusHistory(id: string): Observable<StatusHistoryEntry[]> {
    return this.http.get<StatusHistoryEntry[]>(`${this.baseUrl}/${id}/status`);
  }

  addTag(id: string, tag: string): Observable<ApplicationResponse> {
    return this.http.post<ApplicationResponse>(`${this.baseUrl}/${id}/tags`, { tag });
  }

  removeTag(id: string, tag: string): Observable<ApplicationResponse> {
    return this.http.delete<ApplicationResponse>(`${this.baseUrl}/${id}/tags/${tag}`);
  }

  bulkDelete(ids: string[]): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/bulk/delete`, { ids } as BulkIdsRequest);
  }

  bulkArchive(ids: string[]): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/bulk/archive`, { ids } as BulkIdsRequest);
  }

  bulkChangeStatus(ids: string[], status: ApplicationStatus): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/bulk/status`, { ids, status } as BulkStatusRequest);
  }
}
