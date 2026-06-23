import { ApplicationStatus, PriorityLevel } from "../models/application.model";

export function getStatusColor(status: ApplicationStatus): string {
  return getComputedStyle(document.documentElement)
    .getPropertyValue(`--color-${status.toLowerCase().replaceAll('_', '-')}`)
    .trim();
}

export function getPriorityColor(priority: PriorityLevel): string {
  return getComputedStyle(document.documentElement)
    .getPropertyValue(`--color-priority-${priority.toLowerCase()}`)
    .trim();
}
