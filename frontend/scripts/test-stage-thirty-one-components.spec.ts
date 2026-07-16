import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const stageThirtyOneApi = vi.hoisted(() => ({
  acknowledgeAttentionNotices: vi.fn(),
  getAttentionNotices: vi.fn(),
  getAttentionPermissions: vi.fn()
}));

vi.mock('@/api/stageThirtyOne', () => stageThirtyOneApi);

import StageThirtyOneAttentionPanel from '@/components/StageThirtyOneAttentionPanel.vue';

const pendingNotice = {
  noticeId: 'notice-a',
  level: 'CRITICAL',
  content: '协助起身时请全程搀扶，并留意头晕情况。',
  source: 'HEALTH_ARCHIVE',
  requiredAck: true,
  acknowledged: false,
  acknowledgedAt: ''
};

const informationNotice = {
  noticeId: 'notice-b',
  level: 'INFO',
  content: '本次服务结束后请记录血压测量结果。',
  source: 'SERVICE_ITEM',
  requiredAck: false,
  acknowledged: false,
  acknowledgedAt: ''
};

function success<T>(data: T) {
  return { code: 0, message: 'success', traceId: 'stage-31-component-test', data };
}

function failure(code: number, message = 'failed') {
  return { code, message, traceId: 'stage-31-component-test', data: { items: [] } };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

beforeEach(() => {
  stageThirtyOneApi.getAttentionNotices.mockResolvedValue(success({ items: [pendingNotice, informationNotice] }));
  stageThirtyOneApi.getAttentionPermissions.mockResolvedValue(success(['NURSE_ATTENTION_ACK']));
  stageThirtyOneApi.acknowledgeAttentionNotices.mockResolvedValue(success({ items: [{
    ...pendingNotice,
    acknowledged: true,
    acknowledgedAt: '2099-07-22T08:45:00'
  }, informationNotice] }));
});

describe('stage 31 mounted component behavior', () => {
  it('keeps start disabled until required notices are acknowledged and re-read', async () => {
    stageThirtyOneApi.getAttentionNotices
      .mockResolvedValueOnce(success({ items: [pendingNotice, informationNotice] }))
      .mockResolvedValueOnce(success({ items: [{
        ...pendingNotice,
        acknowledged: true,
        acknowledgedAt: '2099-07-22T08:45:00'
      }, informationNotice] }));
    const wrapper = mount(StageThirtyOneAttentionPanel, {
      props: { orderId: 'order-a', taskStatus: 'ON_THE_WAY' }
    });

    await flushPromises();
    const startButton = wrapper.findAll('button').find((button) => button.text() === '开始服务');
    expect(startButton?.attributes('disabled')).toBeDefined();
    expect(wrapper.text()).toContain('还需确认 1 项');

    await wrapper.find('checkbox-group').trigger('change', { detail: { value: ['notice-a'] } });
    const acknowledgeButton = wrapper.findAll('button').find((button) => button.text().includes('确认已勾选'));
    expect(acknowledgeButton?.attributes('disabled')).toBeUndefined();
    await acknowledgeButton?.trigger('click');
    await flushPromises();

    expect(stageThirtyOneApi.acknowledgeAttentionNotices).toHaveBeenCalledWith(
      'order-a',
      { noticeIds: ['notice-a'] },
      expect.anything()
    );
    expect(stageThirtyOneApi.getAttentionNotices).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('所有必须确认的事项均已完成核对');
    const enabledStartButton = wrapper.findAll('button').find((button) => button.text() === '开始服务');
    expect(enabledStartButton?.attributes('disabled')).toBeUndefined();
    await enabledStartButton?.trigger('click');
    expect(wrapper.emitted('startService')).toHaveLength(1);
    wrapper.unmount();
  });

  it('drops stale notices when switching orders during an in-flight request', async () => {
    const orderAResponse = deferred<ReturnType<typeof success>>();
    let orderASignal: AbortSignal | undefined;
    stageThirtyOneApi.getAttentionNotices.mockImplementation((orderId: string, signal?: AbortSignal) => {
      if (orderId === 'order-a') {
        orderASignal = signal;
        return orderAResponse.promise;
      }
      return Promise.resolve(success({ items: [{
        ...informationNotice,
        noticeId: 'notice-c',
        content: '订单B需要留意服务结束后的活动情况。'
      }] }));
    });
    const wrapper = mount(StageThirtyOneAttentionPanel, {
      props: { orderId: 'order-a', taskStatus: 'ACCEPTED' }
    });

    await wrapper.setProps({ orderId: 'order-b' });
    await flushPromises();
    expect(orderASignal?.aborted).toBe(true);
    orderAResponse.resolve(success({ items: [pendingNotice] }));
    await flushPromises();

    expect(wrapper.text()).toContain('订单B需要留意服务结束后的活动情况');
    expect(wrapper.text()).not.toContain(pendingNotice.content);
    wrapper.unmount();
  });

  it('refreshes after a 409 conflict and keeps service start blocked', async () => {
    stageThirtyOneApi.acknowledgeAttentionNotices.mockResolvedValue(failure(409, 'state conflict'));
    const wrapper = mount(StageThirtyOneAttentionPanel, {
      props: { orderId: 'order-a', taskStatus: 'ON_THE_WAY' }
    });

    await flushPromises();
    await wrapper.find('checkbox-group').trigger('change', { detail: { value: ['notice-a'] } });
    const acknowledgeButton = wrapper.findAll('button').find((button) => button.text().includes('确认已勾选'));
    await acknowledgeButton?.trigger('click');
    await flushPromises();

    expect(wrapper.emitted('stateConflict')).toHaveLength(1);
    expect(stageThirtyOneApi.getAttentionNotices).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('任务状态已经变化');
    expect(wrapper.findAll('button').find((button) => button.text() === '开始服务')?.attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('keeps service start blocked when acknowledgement returns 422', async () => {
    stageThirtyOneApi.acknowledgeAttentionNotices.mockResolvedValue(failure(422, 'invalid notice'));
    const wrapper = mount(StageThirtyOneAttentionPanel, {
      props: { orderId: 'order-a', taskStatus: 'ON_THE_WAY' }
    });

    await flushPromises();
    await wrapper.find('checkbox-group').trigger('change', { detail: { value: ['notice-a'] } });
    await wrapper.findAll('button').find((button) => button.text().includes('确认已勾选'))?.trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('所选注意事项已失效');
    expect(stageThirtyOneApi.getAttentionNotices).toHaveBeenCalledTimes(1);
    expect(wrapper.findAll('button').find((button) => button.text() === '开始服务')?.attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('closes acknowledgement controls when permission loading fails', async () => {
    stageThirtyOneApi.getAttentionPermissions.mockResolvedValue({
      code: 500,
      message: 'service unavailable',
      traceId: 'stage-31-component-test',
      data: []
    });
    const wrapper = mount(StageThirtyOneAttentionPanel, {
      props: { orderId: 'order-a', taskStatus: 'ON_THE_WAY' }
    });

    await flushPromises();
    expect(wrapper.text()).toContain('账号权限暂时无法读取');
    expect(wrapper.find('checkbox').attributes('disabled')).toBeDefined();
    expect(wrapper.findAll('button').find((button) => button.text().includes('确认已勾选'))?.attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('prevents repeated acknowledgement while the first request is pending', async () => {
    const acknowledgement = deferred<ReturnType<typeof success>>();
    stageThirtyOneApi.acknowledgeAttentionNotices.mockReturnValue(acknowledgement.promise);
    const wrapper = mount(StageThirtyOneAttentionPanel, {
      props: { orderId: 'order-a', taskStatus: 'ON_THE_WAY' }
    });

    await flushPromises();
    await wrapper.find('checkbox-group').trigger('change', { detail: { value: ['notice-a'] } });
    const acknowledgeButton = wrapper.findAll('button').find((button) => button.text().includes('确认已勾选'));
    const firstClick = acknowledgeButton?.trigger('click');
    const secondClick = acknowledgeButton?.trigger('click');
    await Promise.resolve();
    expect(stageThirtyOneApi.acknowledgeAttentionNotices).toHaveBeenCalledTimes(1);

    acknowledgement.resolve(success({ items: [{
      ...pendingNotice,
      acknowledged: true,
      acknowledgedAt: '2099-07-22T08:45:00+08:00'
    }] }));
    await Promise.all([firstClick, secondClick]);
    await flushPromises();
    wrapper.unmount();
  });

  it('requires acknowledgement again after reassignment refreshes the same order', async () => {
    stageThirtyOneApi.getAttentionNotices
      .mockResolvedValueOnce(success({ items: [{
        ...pendingNotice,
        acknowledged: true,
        acknowledgedAt: '2099-07-22T08:45:00+08:00'
      }] }))
      .mockResolvedValueOnce(success({ items: [pendingNotice] }));
    const wrapper = mount(StageThirtyOneAttentionPanel, {
      props: { orderId: 'order-a', taskStatus: 'ON_THE_WAY', refreshKey: 0 }
    });

    await flushPromises();
    expect(wrapper.findAll('button').find((button) => button.text() === '开始服务')?.attributes('disabled')).toBeUndefined();
    await wrapper.setProps({ refreshKey: 1 });
    await flushPromises();

    expect(wrapper.text()).toContain('还需确认 1 项');
    expect(wrapper.findAll('button').find((button) => button.text() === '开始服务')?.attributes('disabled')).toBeDefined();
    wrapper.unmount();
  });

  it('renders an admin review as read-only Chinese content without acknowledgement controls', async () => {
    const wrapper = mount(StageThirtyOneAttentionPanel, {
      props: { orderId: 'order-a', taskStatus: 'DISPATCHED', readOnly: true }
    });

    await flushPromises();
    expect(stageThirtyOneApi.getAttentionPermissions).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('高风险');
    expect(wrapper.text()).toContain('健康档案');
    expect(wrapper.text()).not.toContain('notice-a');
    expect(wrapper.text()).not.toContain('HEALTH_ARCHIVE');
    expect(wrapper.find('checkbox').exists()).toBe(false);
    expect(wrapper.findAll('button').some((button) => button.text().includes('确认已勾选'))).toBe(false);
    wrapper.unmount();
  });
});
