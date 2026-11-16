export type Role =
  | "CUSTOMER"
  | "UNDERWRITER"
  | "UNDERWRITER_SUPERVISOR"
  | "OPERATIONS"
  | "AUDITOR";

export type ApplicationStatus =
  | "SUBMITTED"
  | "UNDERWRITING"
  | "APPROVED"
  | "DECLINED"
  | "MANUAL_REVIEW"
  | "SENSITIVE_VALIDATION_REVIEW"
  | "EXPIRED";

export interface Product {
  productCode: string;
  tier: string;
  aprMin: number;
  aprMax: number;
  annualFee: number;
  rewardsDescription: string;
  creditLimitMin: number;
  creditLimitMax: number;
}

export interface ApplicationRequest {
  firstName: string;
  lastName: string;
  email: string;
  ssn: string;
  dateOfBirth: string;
  addressLine: string;
  city: string;
  state: string;
  postalCode: string;
  annualIncome: number;
  monthlyHousingPayment: number;
  monthlyDebtPayment: number;
  employmentStatus: string;
  consentToCreditCheck: boolean;
  productCode: string;
}

export interface ApplicationReceipt {
  applicationId: string;
  status: ApplicationStatus;
  message: string;
}

export interface ApplicationStatusResponse extends ApplicationReceipt {
  productCode: string;
  submittedAt: string;
}

export interface ReviewQueueItem {
  applicationId: string;
  applicantName: string;
  productCode: string;
  status: ApplicationStatus;
  ssnLast4: string;
  annualIncome: number;
  monthlyHousingPayment: number;
  monthlyDebtPayment: number;
  bureauScore: number | null;
  addressValidationStatus: string;
  submittedAt: string;
}

export interface SensitiveReviewItem {
  applicationId: string;
  validationCategory: string;
  providerReference: string;
  createdAt: string;
}

export interface JobSummary {
  executionId: number;
  jobName: string;
  status: string;
  startedAt: string | null;
  exitCode: string;
}
