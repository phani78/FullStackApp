import { expect, test, type APIRequestContext, type Page } from "@playwright/test";
import type { ApplicationStatus, Role } from "../../origination-ui/src/types";
import { randomUUID } from "node:crypto";

const apiBase = process.env.API_BASE_URL ?? "http://127.0.0.1:8080";
const batchBase = process.env.BATCH_BASE_URL ?? "http://127.0.0.1:8081";
const syntheticPersonaIds: Record<string, string> = {
  PRIME: "900000001",
  THIN_FILE: "900000002",
  FRAUD_ALERT: "900000003",
  LOW_SCORE: "900000004",
  INVALID_ADDRESS: "900000005",
  KYC_MISMATCH: "900000006",
  OFAC_MATCH: "900000007",
  ADAPTER_FAILURE: "900000008",
};

async function tokenFor(request: APIRequestContext, role: Role, subject = `${role.toLowerCase()}@example.test`) {
  const response = await request.post(`${apiBase}/api/v1/local-tokens`, {
    data: { subject, role },
    headers: { "X-Idempotency-Key": randomUUID() },
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json() as { accessToken: string }).accessToken;
}

function applicationBody(persona: string, email: string, productCode = "CASHBACK") {
  return {
    firstName: "Synthetic",
    lastName: `Applicant${persona}`,
    email,
    ssn: syntheticPersonaIds[persona],
    dateOfBirth: "1990-01-02",
    addressLine: persona === "INVALID_ADDRESS" ? "999 Invalid Road" : "100 Test Street",
    city: "Sample City",
    state: "NY",
    postalCode: "10001",
    annualIncome: 100000,
    monthlyHousingPayment: 1000,
    monthlyDebtPayment: 500,
    employmentStatus: "EMPLOYED",
    consentToCreditCheck: true,
    productCode,
  };
}

async function submitApiApplication(
  request: APIRequestContext,
  persona: string,
  productCode = "CASHBACK",
  subject?: string,
) {
  const applicant = subject ?? `${randomUUID()}@example.test`;
  const accessToken = await tokenFor(request, "CUSTOMER", applicant);
  const response = await request.post(`${apiBase}/api/v1/applications`, {
    data: applicationBody(persona, applicant, productCode),
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "X-Idempotency-Key": randomUUID(),
      "X-Mock-Persona": persona,
    },
  });
  expect(response.status()).toBe(202);
  return {
    accessToken,
    email: applicant,
    receipt: await response.json() as { applicationId: string; status: ApplicationStatus; message: string },
  };
}

async function waitForStatus(
  request: APIRequestContext,
  applicationId: string,
  accessToken: string,
  status: ApplicationStatus,
) {
  await expect.poll(async () => {
    const response = await request.get(`${apiBase}/api/v1/applications/${applicationId}`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    if (!response.ok()) return "UNAVAILABLE";
    return ((await response.json()) as { status: string }).status;
  }, { timeout: 30_000 }).toBe(status);
}

test.describe("API origination and operations", () => {
  test("prime synthetic persona is auto-approved", async ({ request }) => {
    const { accessToken, receipt } = await submitApiApplication(request, "PRIME", "CASHBACK");
    await waitForStatus(request, receipt.applicationId, accessToken, "APPROVED");
  });

  test("thin-file application is routed to manual review and maker-checker approval", async ({ request }) => {
    const customer = `${randomUUID()}@example.test`;
    const { accessToken, receipt } = await submitApiApplication(request, "THIN_FILE", "SECURED", customer);
    await waitForStatus(request, receipt.applicationId, accessToken, "MANUAL_REVIEW");

    const underwriterToken = await tokenFor(request, "UNDERWRITER", "maker@example.test");
    const queue = await request.get(`${apiBase}/api/v1/workbench/applications`, {
      headers: { Authorization: `Bearer ${underwriterToken}` },
    });
    expect(queue.ok()).toBeTruthy();
    expect((await queue.json() as Array<{ applicationId: string }>).some((item) => item.applicationId === receipt.applicationId)).toBeTruthy();

    const decisionRequest = await request.post(`${apiBase}/api/v1/workbench/applications/${receipt.applicationId}/decisions`, {
      data: { outcome: "APPROVED", reasonCodes: ["THIN_FILE_REVIEW"], note: "Synthetic test review." },
      headers: { Authorization: `Bearer ${underwriterToken}`, "X-Idempotency-Key": randomUUID() },
    });
    expect(decisionRequest.ok()).toBeTruthy();

    const supervisorToken = await tokenFor(request, "UNDERWRITER_SUPERVISOR", "checker@example.test");
    const approval = await request.post(`${apiBase}/api/v1/workbench/applications/${receipt.applicationId}/decision-approval`, {
      data: { approve: true, note: "Independent synthetic test approval." },
      headers: { Authorization: `Bearer ${supervisorToken}`, "X-Idempotency-Key": randomUUID() },
    });
    expect(approval.ok()).toBeTruthy();
    await waitForStatus(request, receipt.applicationId, accessToken, "APPROVED");
  });

  test("low score is declined without disclosing internal reason codes", async ({ request }) => {
    const { accessToken, receipt } = await submitApiApplication(request, "LOW_SCORE", "SECURED");
    await waitForStatus(request, receipt.applicationId, accessToken, "DECLINED");
    const response = await request.get(`${apiBase}/api/v1/applications/${receipt.applicationId}`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    const body = await response.text();
    expect(body).not.toContain("LOW_CREDIT_SCORE");
    expect(body).not.toContain("FICO");
  });

  test("sensitive address validation returns only generic customer messaging", async ({ request }) => {
    const { accessToken, receipt } = await submitApiApplication(request, "INVALID_ADDRESS");
    expect(receipt.status).toBe("SUBMITTED");
    expect(receipt.message).toContain("Please call customer service at 1-800-XXX-XXXX.");
    expect(receipt.message).not.toContain("ADDRESS_UNVERIFIABLE");
    await waitForStatus(request, receipt.applicationId, accessToken, "SUBMITTED");
  });

  test("customer is denied access to the underwriter queue", async ({ request }) => {
    const accessToken = await tokenFor(request, "CUSTOMER");
    const response = await request.get(`${apiBase}/api/v1/workbench/applications`, {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    expect(response.status()).toBe(403);
  });

  test("operations can start a batch job with an idempotency key", async ({ request }) => {
    const accessToken = await tokenFor(request, "OPERATIONS", "ops@example.test");
    const response = await request.post(`${batchBase}/api/v1/ops/batch/jobs/adverseActionNoticeJob/executions`, {
      data: { businessDate: new Date().toISOString().slice(0, 10) },
      headers: { Authorization: `Bearer ${accessToken}`, "X-Idempotency-Key": randomUUID() },
    });
    expect([200, 201]).toContain(response.status());
    expect((await response.json() as { jobName: string }).jobName).toBe("adverseActionNoticeJob");
  });
});

test("customer can submit an application through the browser wizard", async ({ page }) => {
  await signIn(page, "CUSTOMER", "browser-customer@example.test");
  await page.getByRole("button", { name: "Apply for a card" }).click();
  await page.getByLabel("Legal first name").fill("Synthetic");
  await page.getByLabel("Legal last name").fill("Browser");
  await page.getByLabel("Email address").fill("browser-customer@example.test");
  await page.getByLabel("Social Security number").fill(syntheticPersonaIds.PRIME);
  await page.getByLabel("Date of birth").fill("1990-01-02");
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByLabel("Street address").fill("100 Test Street");
  await page.getByLabel("City").fill("Sample City");
  await page.getByLabel("State").fill("NY");
  await page.getByLabel("ZIP code").fill("10001");
  await page.getByRole("button", { name: "Continue" }).click();
  await page.getByLabel("Annual income ($)").fill("100000");
  await page.getByLabel("Monthly housing payment ($)").fill("1000");
  await page.getByLabel("Monthly debt payments ($)").fill("500");
  await page.getByLabel("Card product").selectOption("CASHBACK");
  await page.getByLabel(/I authorize a credit bureau inquiry/).check();
  await page.getByRole("button", { name: "Submit application" }).click();
  await expect(page.getByText("Application reference", { exact: true })).toBeVisible({ timeout: 30_000 });
});

async function signIn(page: Page, role: Role, subject: string) {
  await page.goto("/");
  await page.getByLabel("Work email").fill(subject);
  await page.getByLabel("Demonstration role").selectOption(role);
  await page.getByRole("button", { name: "Continue" }).click();
  await expect(page.getByRole("heading", { name: "Find a card that fits" })).toBeVisible();
}
