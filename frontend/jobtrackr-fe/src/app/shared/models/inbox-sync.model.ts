export interface IngestionStatus {
  connected: boolean;
  connectedEmail: string | null;
  lastPolledAt: string | null;
  totalScanned: number;
  totalProcessed: number;
  totalSkipped: number;
  totalRejected: number;
  totalFailed: number;
}

export type EmailProcessingStatus = 'PROCESSED' | 'SKIPPED' | 'REJECTED' | 'FAILED';

export interface ProcessedEmailItem {
  id: string;
  messageId: string;
  subject: string;
  sender: string;
  receivedAt: string | null;
  status: EmailProcessingStatus;
  confidenceScore: number | null;
  rejectionReason: string | null;
  processedAt: string;
}

export interface IngestionHistoryPage {
  content: ProcessedEmailItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export interface IngestionSyncResponse {
  status: string;
  discoveredCount: number;
  message: string;
}
