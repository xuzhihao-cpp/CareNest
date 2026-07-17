const legacyTrainingPrefix = '/training/';
const staticTrainingPrefix = '/static/training/';

export function resolveTrainingArticleUrl(contentUrl: string, origin: string) {
  const value = contentUrl.trim();
  if (!value) throw new Error('empty training article url');

  const base = new URL(origin);
  const target = new URL(value, base);
  if (!['http:', 'https:'].includes(target.protocol)) {
    throw new Error('unsupported training article protocol');
  }

  // 兼容旧数据，避免 H5 将 /training 页面回退到应用入口并触发登录页。
  if (target.origin === base.origin && target.pathname.startsWith(legacyTrainingPrefix)) {
    target.pathname = `${staticTrainingPrefix}${target.pathname.slice(legacyTrainingPrefix.length)}`;
  }
  return target.toString();
}
