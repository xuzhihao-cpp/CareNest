import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";

const apiBase = process.env.CARENEST_API_BASE ?? "http://localhost:5173/api/v1";
const mysqlContainer = process.env.CARENEST_MYSQL_CONTAINER ?? "carenest-mysql";
const redisContainer = process.env.CARENEST_REDIS_CONTAINER ?? "carenest-redis";
const minioContainer = process.env.CARENEST_MINIO_CONTAINER ?? "carenest-minio";
const elderId = "elder_001";
const bindingId = "binding_001";
const password = process.env.CARENEST_DEMO_PASSWORD ?? "Demo@123456";
const runId = `it-${Date.now()}`;
const uploadedPrefix = `${runId}-`;
const backupPrefix = `it_backup_${Date.now()}`;
const passes = [];

function run(command, args, options = {}) {
  return execFileSync(command, args, {
    encoding: "utf8",
    maxBuffer: 32 * 1024 * 1024,
    ...options,
  }).trim();
}

function containerEnvironment(container) {
  const output = run("docker", [
    "inspect",
    container,
    "--format",
    "{{range .Config.Env}}{{println .}}{{end}}",
  ]);
  return Object.fromEntries(
    output
      .split(/\r?\n/)
      .filter(Boolean)
      .map((line) => {
        const separator = line.indexOf("=");
        return [line.slice(0, separator), line.slice(separator + 1)];
      }),
  );
}

const mysqlEnvironment = containerEnvironment(mysqlContainer);
const mysqlUser = mysqlEnvironment.MYSQL_USER;
const mysqlPassword = mysqlEnvironment.MYSQL_PASSWORD;
const mysqlDatabase = mysqlEnvironment.MYSQL_DATABASE;
const minioEnvironment = containerEnvironment(minioContainer);

function mysqlArgs(program, extraArgs) {
  return [
    "exec",
    "-e",
    `MYSQL_PWD=${mysqlPassword}`,
    mysqlContainer,
    program,
    `--user=${mysqlUser}`,
    ...extraArgs,
  ];
}

function sql(statement) {
  return run("docker", mysqlArgs("mysql", [
    mysqlDatabase,
    "--batch",
    "--raw",
    "--skip-column-names",
    `--execute=${statement}`,
  ]));
}

function quoteSql(value) {
  if (value === null || value === undefined) return "NULL";
  return `'${String(value).replaceAll("\\", "\\\\").replaceAll("'", "''")}'`;
}

function rows(output) {
  if (!output) return [];
  return output.split(/\r?\n/).map((line) => line.split("\t"));
}

function redis(...args) {
  const environment = containerEnvironment(redisContainer);
  const command = ["exec", redisContainer, "redis-cli"];
  if (environment.REDIS_PASSWORD) command.push("-a", environment.REDIS_PASSWORD);
  return run("docker", [...command, ...args]);
}

function minio(...args) {
  const user = encodeURIComponent(minioEnvironment.MINIO_ROOT_USER);
  const secret = encodeURIComponent(minioEnvironment.MINIO_ROOT_PASSWORD);
  return run("docker", [
    "exec",
    "-e",
    `MC_HOST_verify=http://${user}:${secret}@localhost:9000`,
    minioContainer,
    "mc",
    ...args,
  ]);
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function pass(message) {
  passes.push(message);
  console.log(`PASS ${String(passes.length).padStart(2, "0")}  ${message}`);
}

async function request(path, { token, method = "GET", body, headers = {}, expected = 200 } = {}) {
  const requestHeaders = { ...headers };
  if (token) requestHeaders.Authorization = `Bearer ${token}`;
  let requestBody = body;
  if (body !== undefined && !(body instanceof FormData)) {
    requestHeaders["Content-Type"] = "application/json";
    requestBody = JSON.stringify(body);
  }
  const response = await fetch(`${apiBase}${path}`, {
    method,
    headers: requestHeaders,
    body: requestBody,
  });
  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    payload = { raw: text };
  }
  const expectedStatuses = Array.isArray(expected) ? expected : [expected];
  assert(
    expectedStatuses.includes(response.status),
    `${method} ${path}: expected ${expectedStatuses.join("/")}, got ${response.status}: ${text.slice(0, 300)}`,
  );
  if (payload && Object.hasOwn(payload, "code")) {
    const expectedCode = response.ok ? 0 : response.status;
    assert(payload.code === expectedCode, `${method} ${path}: expected code ${expectedCode}, got ${payload.code}`);
  }
  return { response, payload };
}

async function upload(token, bytes, fileName, mimeType, expected = 200) {
  const form = new FormData();
  form.append("file", new Blob([bytes], { type: mimeType }), fileName);
  return request("/files", { token, method: "POST", body: form, expected });
}

async function login(username) {
  const { payload } = await request("/auth/login", {
    method: "POST",
    body: { username, password },
  });
  assert(payload?.data?.token, `${username}: login did not return a token`);
  return payload.data;
}

function archivePayload(archive, archiveVersion = archive.archiveVersion) {
  return {
    archiveVersion,
    diseases: archive.diseases.map(({ diseaseName, diagnosedAt, status, remark }) => ({
      diseaseName,
      diagnosedAt,
      status,
      remark: remark ?? "",
    })),
    medications: archive.medications.map(
      ({ medicationName, dosage, frequency, timePoints, startDate, endDate, remark }) => ({
        medicationName,
        dosage: dosage ?? "",
        frequency,
        timePoints,
        startDate,
        endDate,
        remark: remark ?? "",
      }),
    ),
    allergies: archive.allergies.map(({ allergenName, reaction, severity, remark }) => ({
      allergenName,
      reaction: reaction ?? "",
      severity,
      remark: remark ?? "",
    })),
    riskTags: archive.riskTags.map((item) => item.tagCode),
    carePlan: archive.carePlan,
  };
}

function archiveBusinessData(archive) {
  const normalizeOptionalText = (value) => value ?? "";
  return {
    diseases: archive.diseases.map((item) => ({
      ...item,
      remark: normalizeOptionalText(item.remark),
    })),
    medications: archive.medications.map((item) => ({
      ...item,
      dosage: normalizeOptionalText(item.dosage),
      remark: normalizeOptionalText(item.remark),
    })),
    allergies: archive.allergies.map((item) => ({
      ...item,
      reaction: normalizeOptionalText(item.reaction),
      remark: normalizeOptionalText(item.remark),
    })),
    riskTags: archive.riskTags,
    carePlan: archive.carePlan,
  };
}

function homeKey(role, userId) {
  const hash = createHash("sha256").update(userId).digest("hex");
  return `carenest:home:${role}:${hash}:v1`;
}

function localShanghaiDate() {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Shanghai",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  const value = Object.fromEntries(parts.map((item) => [item.type, item.value]));
  return `${value.year}-${value.month}-${value.day}`;
}

const archiveTables = [
  "health_archive",
  "chronic_disease",
  "medication_plan",
  "allergy_record",
  "risk_tag",
  "care_plan",
];
const backupSpecifications = [
  ...archiveTables.map((table) => ({ table, where: `elder_id=${quoteSql(elderId)}` })),
  { table: "health_archive_change_log", where: `elder_id=${quoteSql(elderId)}` },
  {
    table: "operation_log",
    where: `biz_id=${quoteSql(elderId)} AND operation_type IN ('UPDATE_HEALTH_ARCHIVE','ADD_HEALTH_ARCHIVE_MEDICATION')`,
  },
  { table: "elder_family_binding", where: `binding_id=${quoteSql(bindingId)}` },
];
let databaseBackupReady = false;

function backupTableName(table) {
  return `${backupPrefix}_${table}`;
}

function takeDatabaseBackup() {
  const statements = [];
  for (const { table, where } of backupSpecifications) {
    const backup = backupTableName(table);
    statements.push(`DROP TABLE IF EXISTS ${backup}`);
    statements.push(`CREATE TABLE ${backup} LIKE ${table}`);
    statements.push(`INSERT INTO ${backup} SELECT * FROM ${table} WHERE ${where}`);
  }
  sql(statements.join(";\n"));
  databaseBackupReady = true;
}

function restoreDatabaseBackup() {
  const deletes = [
    `DELETE FROM operation_log WHERE biz_id=${quoteSql(elderId)} AND operation_type IN ('UPDATE_HEALTH_ARCHIVE','ADD_HEALTH_ARCHIVE_MEDICATION')`,
    `DELETE FROM health_archive_change_log WHERE elder_id=${quoteSql(elderId)}`,
    ...archiveTables.slice(1).map((table) => `DELETE FROM ${table} WHERE elder_id=${quoteSql(elderId)}`),
    `DELETE FROM health_archive WHERE elder_id=${quoteSql(elderId)}`,
    `DELETE FROM elder_family_binding WHERE binding_id=${quoteSql(bindingId)}`,
  ];
  const restores = backupSpecifications.map(
    ({ table }) => `INSERT INTO ${table} SELECT * FROM ${backupTableName(table)}`,
  );
  const drops = backupSpecifications.map(({ table }) => `DROP TABLE ${backupTableName(table)}`);
  sql([
    "SET FOREIGN_KEY_CHECKS=0",
    ...deletes,
    ...restores,
    "SET FOREIGN_KEY_CHECKS=1",
    ...drops,
  ].filter(Boolean).join(";\n"));
  databaseBackupReady = false;
}

function setBinding(status, scopes) {
  sql(`UPDATE elder_family_binding SET binding_status=${quoteSql(status)}, scope_codes=${quoteSql(JSON.stringify(scopes))} WHERE binding_id=${quoteSql(bindingId)}`);
}

function cleanupUploadedFiles() {
  const assets = rows(sql(`
    SELECT file_id, storage_bucket, object_key
    FROM file_asset
    WHERE original_name LIKE ${quoteSql(`${uploadedPrefix}%`)}
  `));
  for (const [fileId, bucket, objectKey] of assets) {
    const medicalIds = rows(sql(`SELECT medical_file_id FROM medical_file WHERE file_id=${quoteSql(fileId)}`))
      .map(([medicalId]) => medicalId);
    const businessIds = [fileId, ...medicalIds].map(quoteSql).join(",");
    if (businessIds) sql(`DELETE FROM operation_log WHERE biz_id IN (${businessIds})`);
    sql(`DELETE FROM medical_file WHERE file_id=${quoteSql(fileId)}`);
    sql(`DELETE FROM file_asset WHERE file_id=${quoteSql(fileId)}`);
    try {
      minio("rm", `verify/${bucket}/${objectKey}`);
    } catch (error) {
      console.warn(`WARN could not remove MinIO object ${bucket}/${objectKey}: ${error.message}`);
    }
  }
}

async function phaseNineteen(tokens, users) {
  const originalResult = await request(`/elders/${elderId}/health-archive`, { token: tokens.elder });
  const original = originalResult.payload.data;
  assert(original.elderId === elderId, "phase 19 returned the wrong elder");

  await request(`/elders/${elderId}/health-archive`, { expected: 401 });
  await request(`/elders/${elderId}/health-archive`, { token: tokens.family });
  for (const role of ["nurse", "admin", "customerService"]) {
    await request(`/elders/${elderId}/health-archive`, { token: tokens[role], expected: 403 });
  }
  pass("Phase 19 read permissions match all five roles");

  setBinding("ACTIVE", ["HEALTH_EDIT"]);
  await request(`/elders/${elderId}/health-archive`, { token: tokens.family, expected: 403 });
  setBinding("ACTIVE", ["HEALTH_VIEW"]);
  await request(`/elders/${elderId}/health-archive`, {
    token: tokens.family,
    method: "PUT",
    body: archivePayload(original),
    expected: 403,
  });
  setBinding("REVOKED", ["HEALTH_VIEW", "HEALTH_EDIT"]);
  await request(`/elders/${elderId}/health-archive`, { token: tokens.family, expected: 403 });
  setBinding("ACTIVE", ["HEALTH_VIEW", "HEALTH_EDIT", "ORDER_CREATE", "REPORT_VIEW", "REPORT_CONFIRM", "ARCHIVE_EDIT"]);
  pass("Phase 19 enforces ACTIVE binding and separate view/edit scopes");

  for (const role of ["elder", "nurse", "admin", "customerService"]) {
    await request(`/elders/${elderId}/health-archive`, {
      token: tokens[role],
      method: "PUT",
      body: archivePayload(original),
      expected: 403,
    });
  }
  pass("Phase 19 write permissions reject elder, nurse, admin and customer service roles");

  await request("/elder/home-summary", { token: tokens.elder });
  await request("/family/home-summary", { token: tokens.family });
  const elderCacheKey = homeKey("ELDER", users.elder.userId);
  const familyCacheKey = homeKey("FAMILY", users.family.userId);
  assert(redis("EXISTS", elderCacheKey) === "1", "elder home cache was not created");
  assert(redis("EXISTS", familyCacheKey) === "1", "family home cache was not created");

  const modified = {
    archiveVersion: original.archiveVersion,
    diseases: [{
      diseaseName: "集成验证高血压",
      diagnosedAt: "2024-02-29",
      status: "MONITORING",
      remark: runId,
    }],
    medications: [{
      medicationName: "集成验证用药",
      dosage: "半片",
      frequency: "TWICE_DAILY",
      timePoints: ["08:30", "18:30"],
      startDate: "2026-01-01",
      endDate: null,
      remark: runId,
    }],
    allergies: [{
      allergenName: "集成验证过敏原",
      reaction: "轻微皮疹",
      severity: "MILD",
      remark: runId,
    }],
    riskTags: ["FALL_RISK", "MEDICATION_RISK"],
    carePlan: {
      careGoals: "验证五类档案统一保存",
      dailyCare: "每日记录真实健康数据",
      precautions: "发现异常及时联系家属",
    },
  };
  const updateResult = await request(`/elders/${elderId}/health-archive`, {
    token: tokens.family,
    method: "PUT",
    body: modified,
  });
  assert(updateResult.payload.data.archiveVersion === original.archiveVersion + 1, "archive version did not advance");
  assert(redis("EXISTS", elderCacheKey) === "0", "elder home cache was not evicted");
  assert(redis("EXISTS", familyCacheKey) === "0", "family home cache was not evicted");

  const changed = (await request(`/elders/${elderId}/health-archive`, { token: tokens.family })).payload.data;
  assert(changed.diseases[0].diseaseName === modified.diseases[0].diseaseName, "disease update was not persisted");
  assert(changed.medications[0].timePoints.length === 2, "medication schedule was not persisted");
  assert(changed.allergies[0].allergenName === modified.allergies[0].allergenName, "allergy was not persisted");
  assert(changed.riskTags.length === 2, "risk tags were not persisted");
  assert(changed.carePlan.careGoals === modified.carePlan.careGoals, "care plan was not persisted");
  pass("Phase 19 persists all five archive categories and evicts Redis home caches");

  await request(`/elders/${elderId}/health-archive`, {
    token: tokens.family,
    method: "PUT",
    body: modified,
    expected: 409,
  });
  const versionBeforeValidation = changed.archiveVersion;
  await request(`/elders/${elderId}/health-archive`, {
    token: tokens.family,
    method: "PUT",
    body: { ...archivePayload(changed), medications: [archivePayload(changed).medications[0], archivePayload(changed).medications[0]] },
    expected: 422,
  });
  await request(`/elders/${elderId}/health-archive`, {
    token: tokens.family,
    method: "PUT",
    body: { ...archivePayload(changed), riskTags: ["UNSUPPORTED_RISK"] },
    expected: 422,
  });
  const versionAfterValidation = Number(sql(`SELECT archive_version FROM health_archive WHERE elder_id=${quoteSql(elderId)}`));
  assert(versionAfterValidation === versionBeforeValidation, "invalid archive request changed the version");
  pass("Phase 19 returns 409 for stale versions and rolls back invalid 422 requests");

  const quickMedication = {
    archiveVersion: changed.archiveVersion,
    medicationName: `集成验证新增药物${String(Date.now()).slice(-5)}`,
    dosage: "1片",
    frequency: "ONCE_DAILY",
    timePoints: ["09:00"],
    startDate: localShanghaiDate(),
    endDate: null,
    remark: runId,
  };
  const medicationResult = await request(`/elders/${elderId}/medications`, {
    token: tokens.family,
    method: "POST",
    body: quickMedication,
  });
  assert(medicationResult.payload.data.archiveVersion === changed.archiveVersion + 1, "quick medication did not advance version");
  await request(`/elders/${elderId}/medications`, {
    token: tokens.family,
    method: "POST",
    body: { ...quickMedication, archiveVersion: medicationResult.payload.data.archiveVersion },
    expected: 422,
  });
  pass("Phase 19 quick medication creation is real, versioned and duplicate-safe");

  const current = (await request(`/elders/${elderId}/health-archive`, { token: tokens.family })).payload.data;
  await request(`/elders/${elderId}/health-archive`, {
    token: tokens.family,
    method: "PUT",
    body: archivePayload(original, current.archiveVersion),
  });
  const restored = (await request(`/elders/${elderId}/health-archive`, { token: tokens.elder })).payload.data;
  const originalBusinessData = archiveBusinessData(original);
  const restoredBusinessData = archiveBusinessData(restored);
  assert(
    JSON.stringify(restoredBusinessData) === JSON.stringify(originalBusinessData),
    `archive business data was not restored after the test\noriginal=${JSON.stringify(originalBusinessData)}\nrestored=${JSON.stringify(restoredBusinessData)}`,
  );
  const changeCount = Number(sql(`SELECT COUNT(*) FROM health_archive_change_log WHERE elder_id=${quoteSql(elderId)} AND after_value LIKE ${quoteSql(`%${runId}%`)}`));
  const operationCount = Number(sql(`SELECT COUNT(*) FROM operation_log WHERE biz_id=${quoteSql(elderId)} AND operation_type IN ('UPDATE_HEALTH_ARCHIVE','ADD_HEALTH_ARCHIVE_MEDICATION') AND after_value LIKE ${quoteSql(`%${runId}%`)}`));
  assert(changeCount >= 2 && operationCount >= 2, "phase 19 audit logs were not persisted");
  pass("Phase 19 writes transactional change and operation audit logs to MySQL");
}

async function phaseTwenty(tokens, users) {
  const pdf = Buffer.from("%PDF-1.4\n1 0 obj\n<<>>\nendobj\n%%EOF\n", "ascii");
  const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46]);
  const png = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]);

  await upload(undefined, pdf, `${uploadedPrefix}anonymous.pdf`, "application/pdf", 401);
  await upload(tokens.family, Buffer.from("plain text"), `${uploadedPrefix}fake.pdf`, "application/pdf", 422);
  await upload(tokens.family, pdf, `${uploadedPrefix}renamed.txt`, "application/pdf", 422);
  await upload(tokens.family, Buffer.from([0x89, 0x50, 0x4e, 0x47, 0, 0, 0, 0]), `${uploadedPrefix}bad.png`, "image/png", 422);
  pass("Phase 20 rejects unauthenticated, disguised, renamed and malformed uploads");

  const exactLimit = Buffer.alloc(20 * 1024 * 1024, 0x20);
  pdf.copy(exactLimit, 0);
  const exactResult = await upload(tokens.family, exactLimit, `${uploadedPrefix}exact-20m.pdf`, "application/pdf");
  assert(exactResult.payload.data.size === exactLimit.length, "20 MiB upload returned the wrong size");
  const oversized = Buffer.alloc((20 * 1024 * 1024) + 1, 0x20);
  pdf.copy(oversized, 0);
  const oversizedResult = await upload(tokens.family, oversized, `${uploadedPrefix}over-20m.pdf`, "application/pdf", 422);
  assert(oversizedResult.payload.message.includes("20MB"), "oversized upload did not return the unified business message");
  pass("Phase 20 accepts exactly 20 MiB and returns unified 422 above the limit through Nginx");

  const pdfResult = (await upload(tokens.family, pdf, `${uploadedPrefix}report.pdf`, "application/pdf")).payload.data;
  const jpegResult = (await upload(tokens.family, jpeg, `${uploadedPrefix}photo.jpeg`, "image/jpeg")).payload.data;
  const pngResult = (await upload(tokens.family, png, `${uploadedPrefix}scan.PNG`, "image/png")).payload.data;
  assert(jpegResult.mimeType === "image/jpeg" && pngResult.mimeType === "image/png", "image MIME types were not persisted");
  for (const item of [pdfResult, jpegResult, pngResult]) {
    const asset = rows(sql(`SELECT storage_bucket, object_key, audit_status FROM file_asset WHERE file_id=${quoteSql(item.fileId)}`))[0];
    assert(asset?.[2] === "PENDING", `${item.originalName}: file_asset was not persisted as PENDING`);
    const stat = JSON.parse(minio("stat", "--json", `verify/${asset[0]}/${asset[1]}`));
    assert(stat.status === "success", `${item.originalName}: MinIO object is missing`);
  }
  const signedResponse = await fetch(pdfResult.url);
  assert(signedResponse.status === 200, "presigned upload URL is not readable");
  assert((await signedResponse.text()).startsWith("%PDF"), "presigned upload URL returned the wrong object");
  pass("Phase 20 stores PDF, JPEG and PNG assets in MySQL and real MinIO");

  const registration = {
    fileId: pdfResult.fileId,
    fileType: "EXAMINATION_REPORT",
    title: `集成验证病历 ${runId}`,
    occurredAt: localShanghaiDate(),
  };
  for (const role of ["elder", "nurse", "admin", "customerService"]) {
    await request(`/elders/${elderId}/medical-files`, {
      token: tokens[role],
      method: "POST",
      body: registration,
      expected: 403,
    });
  }
  setBinding("ACTIVE", ["HEALTH_VIEW"]);
  await request(`/elders/${elderId}/medical-files`, {
    token: tokens.family,
    method: "POST",
    body: registration,
    expected: 403,
  });
  setBinding("ACTIVE", ["HEALTH_VIEW", "HEALTH_EDIT", "ORDER_CREATE", "REPORT_VIEW", "REPORT_CONFIRM", "ARCHIVE_EDIT"]);
  await request(`/elders/${elderId}/medical-files`, {
    token: tokens.family,
    method: "POST",
    body: { ...registration, fileType: "UNKNOWN" },
    expected: 422,
  });
  await request(`/elders/${elderId}/medical-files`, {
    token: tokens.family,
    method: "POST",
    body: { ...registration, occurredAt: "2999-01-01" },
    expected: 422,
  });
  pass("Phase 20 registration enforces role, ACTIVE scope, type and date constraints");

  const elderOwned = (await upload(tokens.elder, pdf, `${uploadedPrefix}elder-owned.pdf`, "application/pdf")).payload.data;
  await request(`/elders/${elderId}/medical-files`, {
    token: tokens.family,
    method: "POST",
    body: { ...registration, fileId: elderOwned.fileId },
    expected: 403,
  });
  pass("Phase 20 prevents one user from registering another user's file asset");

  await request("/elder/home-summary", { token: tokens.elder });
  await request("/family/home-summary", { token: tokens.family });
  const elderCacheKey = homeKey("ELDER", users.elder.userId);
  const familyCacheKey = homeKey("FAMILY", users.family.userId);
  assert(redis("EXISTS", elderCacheKey) === "1" && redis("EXISTS", familyCacheKey) === "1", "home caches were not ready before registration");

  const registered = (await request(`/elders/${elderId}/medical-files`, {
    token: tokens.family,
    method: "POST",
    body: registration,
  })).payload.data;
  assert(registered.auditStatus === "PENDING", "new medical file did not start in PENDING");
  assert(redis("EXISTS", elderCacheKey) === "0" && redis("EXISTS", familyCacheKey) === "0", "registration did not evict home caches");
  await request(`/elders/${elderId}/medical-files`, {
    token: tokens.family,
    method: "POST",
    body: registration,
    expected: 409,
  });
  pass("Phase 20 registers once, starts PENDING and evicts Redis home caches");

  await request(`/elders/${elderId}/medical-files`, { expected: 401 });
  const familyList = (await request(`/elders/${elderId}/medical-files`, { token: tokens.family })).payload.data;
  const elderList = (await request(`/elders/${elderId}/medical-files`, { token: tokens.elder })).payload.data;
  for (const role of ["nurse", "admin", "customerService"]) {
    await request(`/elders/${elderId}/medical-files`, { token: tokens[role], expected: 403 });
  }
  const item = familyList.find((value) => value.medicalFileId === registered.medicalFileId);
  assert(item && elderList.some((value) => value.medicalFileId === registered.medicalFileId), "new medical file is not visible to family and elder");
  assert(!Object.hasOwn(item, "objectKey"), "medical file list leaked the internal object key");
  const preview = await fetch(item.previewUrl);
  assert(preview.status === 200 && (await preview.text()).startsWith("%PDF"), "medical file preview URL is not usable");
  setBinding("ACTIVE", ["HEALTH_EDIT"]);
  await request(`/elders/${elderId}/medical-files`, { token: tokens.family, expected: 403 });
  setBinding("ACTIVE", ["HEALTH_VIEW", "HEALTH_EDIT", "ORDER_CREATE", "REPORT_VIEW", "REPORT_CONFIRM", "ARCHIVE_EDIT"]);
  pass("Phase 20 list and signed preview permissions match elder, family, nurse, admin and customer service roles");

  const databaseRow = rows(sql(`
    SELECT m.audit_status, f.audit_status, f.storage_bucket, f.object_key
    FROM medical_file m JOIN file_asset f ON f.file_id=m.file_id
    WHERE m.medical_file_id=${quoteSql(registered.medicalFileId)}
  `))[0];
  assert(databaseRow?.[0] === "PENDING" && databaseRow?.[1] === "PENDING", "medical_file and file_asset status are not aligned");
  const uploadLogs = Number(sql(`SELECT COUNT(*) FROM operation_log WHERE biz_id=${quoteSql(pdfResult.fileId)} AND operation_type='UPLOAD_MEDICAL_FILE_ASSET'`));
  const registerLogs = Number(sql(`SELECT COUNT(*) FROM operation_log WHERE biz_id=${quoteSql(registered.medicalFileId)} AND operation_type='REGISTER_MEDICAL_FILE'`));
  assert(uploadLogs === 1 && registerLogs === 1, "phase 20 operation logs are missing");
  pass("Phase 20 persists aligned statuses and complete upload/register audit logs");
}

async function main() {
  assert(mysqlUser && mysqlPassword && mysqlDatabase, "MySQL container environment is incomplete");
  assert(minioEnvironment.MINIO_ROOT_USER && minioEnvironment.MINIO_ROOT_PASSWORD, "MinIO container environment is incomplete");
  const health = await request("/health");
  assert(health.payload.data.dbConnected === true, "backend health does not report a connected database");
  assert(redis("PING") === "PONG", "Redis did not answer PONG");
  pass("Docker gateway, backend, MySQL and Redis are healthy");

  takeDatabaseBackup();
  const users = {
    elder: await login("elder_demo"),
    family: await login("family_demo"),
    nurse: await login("nurse_demo"),
    admin: await login("admin_demo"),
    customerService: await login("cs_demo"),
  };
  const tokens = Object.fromEntries(Object.entries(users).map(([role, user]) => [role, user.token]));
  assert(users.elder.roles.includes("ELDER"), "elder_demo role is wrong");
  assert(users.family.roles.includes("FAMILY"), "family_demo role is wrong");
  assert(users.nurse.roles.includes("NURSE"), "nurse_demo role is wrong");
  assert(users.admin.roles.includes("ADMIN"), "admin_demo role is wrong");
  assert(users.customerService.roles.includes("CUSTOMER_SERVICE"), "cs_demo role is wrong");
  pass("All five demo roles authenticate against the real database");

  await phaseNineteen(tokens, users);
  await phaseTwenty(tokens, users);
}

let failure;
try {
  await main();
} catch (error) {
  failure = error;
  console.error(`FAIL ${error.stack ?? error.message}`);
} finally {
  try {
    if (databaseBackupReady) restoreDatabaseBackup();
    cleanupUploadedFiles();
    const elderUserId = sql(`SELECT user_id FROM elder_profile WHERE elder_id=${quoteSql(elderId)}`);
    redis("DEL", homeKey("ELDER", elderUserId), homeKey("FAMILY", "family-001"));
    console.log("CLEANUP database snapshots, test assets and MinIO objects restored");
  } catch (cleanupError) {
    console.error(`CLEANUP FAILED ${cleanupError.stack ?? cleanupError.message}`);
    failure ??= cleanupError;
  }
}

if (failure) process.exitCode = 1;
else console.log(`SUMMARY ${passes.length} full-stack checks passed for phases 19-20`);
