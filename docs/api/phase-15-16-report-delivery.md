# Phase 15-16 Report Delivery Contract

## Report retrieval

| Method | Path | Access |
| --- | --- | --- |
| GET | `/api/v1/elder/reports` | The elder who owns the related order |
| GET | `/api/v1/elder/reports/pending` | The elder who owns the related order |
| GET | `/api/v1/family/reports` | An active family binding with `REPORT_CONFIRM` |
| GET | `/api/v1/family/reports/pending` | An active family binding with `REPORT_CONFIRM` |
| GET | `/api/v1/orders/{orderId}/service-report` | Admin, assigned nurse, order elder, or an authorized family member |

The list response contains `reportId`, `orderId`, and `elderName`. The client must use the identifiers only for requests and must not show them as primary user-facing text.

`pending` returns reports whose `reportStatus` is `WAIT_CONFIRM`. The full list returns reports in all report states that the current user may read.

## Generation and acknowledgement

`POST /api/v1/orders/{orderId}/service-report/generate` requires at least one saved nursing service record. Generation copies the saved service record and nursing advice into the report, then sets both the report and order to `WAIT_CONFIRM`.

When a previously rejected report is generated again, the existing report is refreshed, its confirmation time is cleared, and its status is reset to `WAIT_CONFIRM` before it is delivered again.

The acknowledgement request is:

```json
{
  "ackResult": "ACCEPTED",
  "satisfaction": 80,
  "remark": "The report has been reviewed.",
  "acceptedSuggestionIds": []
}
```

`satisfaction` is an integer from `0` through `100`.
