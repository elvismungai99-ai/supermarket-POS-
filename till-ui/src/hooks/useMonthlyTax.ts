import { useCallback, useEffect, useState } from 'react';
import { apiFetch, getErrorMessage } from '../utils/apiClient';
import type { MonthlyTaxReportResponse } from '../components/MonthlyTaxView';

export interface RecordTaxPaymentInput {
  taxPeriod: string;
  taxType: string;
  amountPaidKes: number;
  paymentDate: string;
  paymentMethod: string;
  kraPrn: string;
  notes: string | null;
}

export function useMonthlyTax(
  selectedMonth: string,
  onError: (msg: string) => void
) {
  const [report, setReport] = useState<MonthlyTaxReportResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  const loadReport = useCallback(async (month: string) => {
    setLoading(true);
    try {
      const res = await apiFetch(`/api/reports/monthly-tax?month=${encodeURIComponent(month)}`);
      if (!res.ok) {
        const err = (await res.json().catch(() => ({}))) as { message?: string };
        throw new Error(err.message || 'Failed to load monthly tax calculation');
      }
      const data: MonthlyTaxReportResponse = await res.json();
      setReport(data);
    } catch (err: unknown) {
      onError(getErrorMessage(err, 'Could not load monthly tax report'));
    } finally {
      setLoading(false);
    }
  }, [onError]);

  useEffect(() => {
    void loadReport(selectedMonth);
  }, [selectedMonth, loadReport]);

  const recordPayment = useCallback(async (input: RecordTaxPaymentInput): Promise<boolean> => {
    setSubmitting(true);
    try {
      const res = await apiFetch('/api/reports/tax-payments', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(input),
      });
      const data = (await res.json().catch(() => ({}))) as { message?: string };
      if (!res.ok) throw new Error(data.message || 'Failed to record KRA tax payment');
      await loadReport(selectedMonth);
      return true;
    } catch (err: unknown) {
      onError(getErrorMessage(err, 'Error saving tax remittance'));
      return false;
    } finally {
      setSubmitting(false);
    }
  }, [loadReport, onError, selectedMonth]);

  return { report, loading, submitting, reload: () => loadReport(selectedMonth), recordPayment };
}
