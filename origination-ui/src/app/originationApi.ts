import { createApi, fetchBaseQuery } from "@reduxjs/toolkit/query/react";
import { getAccessToken } from "./authSlice";
import type {
  ApplicationReceipt,
  ApplicationRequest,
  ApplicationStatusResponse,
  JobSummary,
  Product,
  ReviewQueueItem,
  SensitiveReviewItem,
} from "../types";

const baseQuery = fetchBaseQuery({
  baseUrl: "/api/v1",
  prepareHeaders(headers) {
    const token = getAccessToken();
    if (token) headers.set("Authorization", `Bearer ${token}`);
    headers.set("X-Correlation-ID", crypto.randomUUID());
    return headers;
  },
});

export const originationApi = createApi({
  reducerPath: "originationApi",
  baseQuery,
  tagTypes: ["Products", "Application", "Queue", "Jobs"],
  endpoints: (builder) => ({
    getProducts: builder.query<Product[], void>({
      query: () => "/products",
      providesTags: ["Products"],
    }),
    submitApplication: builder.mutation<
      ApplicationReceipt,
      { application: ApplicationRequest; persona: string }
    >({
      query: ({ application, persona }) => ({
        url: "/applications",
        method: "POST",
        body: application,
        headers: {
          "X-Idempotency-Key": crypto.randomUUID(),
          "X-Mock-Persona": persona,
        },
      }),
      invalidatesTags: ["Queue"],
    }),
    getApplicationStatus: builder.query<ApplicationStatusResponse, string>({
      query: (id) => `/applications/${id}`,
      providesTags: (_result, _error, id) => [{ type: "Application", id }],
    }),
    getReviewQueue: builder.query<ReviewQueueItem[], void>({
      query: () => "/workbench/applications",
      providesTags: ["Queue"],
    }),
    getSensitiveReviewQueue: builder.query<SensitiveReviewItem[], void>({
      query: () => "/workbench/sensitive-validations",
    }),
    claimApplication: builder.mutation<void, string>({
      query: (id) => ({
        url: `/workbench/applications/${id}/claim`,
        method: "POST",
        headers: { "X-Idempotency-Key": crypto.randomUUID() },
      }),
      invalidatesTags: ["Queue"],
    }),
    addReviewNote: builder.mutation<void, { id: string; note: string }>({
      query: ({ id, note }) => ({
        url: `/workbench/applications/${id}/notes`,
        method: "POST",
        body: { note },
        headers: { "X-Idempotency-Key": crypto.randomUUID() },
      }),
      invalidatesTags: ["Queue"],
    }),
    proposeDecision: builder.mutation<void, { id: string; outcome: "APPROVED" | "DECLINED" }>({
      query: ({ id, outcome }) => ({
        url: `/workbench/applications/${id}/decisions`,
        method: "POST",
        body: { outcome, reasonCodes: ["UNDERWRITER_REVIEW"], note: "" },
        headers: { "X-Idempotency-Key": crypto.randomUUID() },
      }),
      invalidatesTags: ["Queue"],
    }),
    approveDecision: builder.mutation<void, { id: string; approve: boolean }>({
      query: ({ id, approve }) => ({
        url: `/workbench/applications/${id}/decision-approval`,
        method: "POST",
        body: { approve },
        headers: { "X-Idempotency-Key": crypto.randomUUID() },
      }),
      invalidatesTags: ["Queue"],
    }),
  }),
});

const batchQuery = fetchBaseQuery({
  baseUrl: "/batch-api/api/v1/ops/batch",
  prepareHeaders(headers) {
    const token = getAccessToken();
    if (token) headers.set("Authorization", `Bearer ${token}`);
    headers.set("X-Correlation-ID", crypto.randomUUID());
    return headers;
  },
});

export const batchApi = createApi({
  reducerPath: "batchApi",
  baseQuery: batchQuery,
  tagTypes: ["Jobs"],
  endpoints: (builder) => ({
    getBatchJobs: builder.query<JobSummary[], void>({
      query: () => "/jobs",
      providesTags: ["Jobs"],
    }),
    launchBatchJob: builder.mutation<
      JobSummary,
      { jobName: string; businessDate: string }
    >({
      query: ({ jobName, businessDate }) => ({
        url: `/jobs/${jobName}/executions`,
        method: "POST",
        body: { businessDate },
        headers: { "X-Idempotency-Key": crypto.randomUUID() },
      }),
      invalidatesTags: ["Jobs"],
    }),
  }),
});

export const {
  useGetProductsQuery,
  useSubmitApplicationMutation,
  useGetApplicationStatusQuery,
  useGetReviewQueueQuery,
  useGetSensitiveReviewQueueQuery,
  useClaimApplicationMutation,
  useAddReviewNoteMutation,
  useProposeDecisionMutation,
  useApproveDecisionMutation,
} = originationApi;

export const { useGetBatchJobsQuery, useLaunchBatchJobMutation } = batchApi;
