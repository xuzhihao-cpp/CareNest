# Report Delivery Field Change

| Module | DTO | Field | Database column | Type | Rule |
| --- | --- | --- | --- | --- | --- |
| phase-16 | ReportAckRequest | satisfaction | satisfaction | integer | Inclusive range `0..100` |
| phase-16 | PendingReportResponse | reportId | report_id | string | Request identifier; not primary UI text |
| phase-16 | PendingReportResponse | orderId | order_id | string | Request identifier; not primary UI text |
| phase-16 | PendingReportResponse | elderName | elder_name | string | User-facing report owner name |

`WAIT_CONFIRM` is the only report status returned from the pending-report endpoints. A regenerated report must reset both order and report state to `WAIT_CONFIRM`.
