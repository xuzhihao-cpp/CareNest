import { defineComponent, nextTick } from 'vue';
import { flushPromises, shallowMount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';

const stageElevenApi = vi.hoisted(() => ({
  getAdminOrders: vi.fn(),
  getAdminOrderDetail: vi.fn()
}));

const stageThirtyApi = vi.hoisted(() => ({
  getPreferredNurse: vi.fn(),
  getPreferredNurseBindings: vi.fn(),
  getPreferredNursePermissions: vi.fn(),
  updatePreferredNurse: vi.fn()
}));

const stageTwentyNineApi = vi.hoisted(() => ({
  getOrderRecommendations: vi.fn()
}));

vi.mock('@/api/stageEleven', () => stageElevenApi);
vi.mock('@/api/stageThirty', () => stageThirtyApi);
vi.mock('@/api/stageTwentyNine', () => stageTwentyNineApi);

import StageElevenAdminOrdersPanel from '@/components/StageElevenAdminOrdersPanel.vue';
import StageThirtyFamilyPreferencePanel from '@/components/StageThirtyFamilyPreferencePanel.vue';
import StageThirtyPreferenceBadge from '@/components/StageThirtyPreferenceBadge.vue';

const RecommendationStub = defineComponent({
  name: 'StageTwentyNineRecommendationPanel',
  props: {
    selectable: Boolean,
    selectedNurseId: String,
    orderId: String
  },
  emits: ['selected', 'recommendationsLoaded'],
  template: '<div data-test="recommendations" />'
});

const AdminPreferenceStub = defineComponent({
  name: 'StageThirtyAdminPreferenceSummary',
  template: '<div data-test="admin-preference" />'
});

const orderA = {
  orderId: 'order-a',
  orderNo: 'NO-A',
  orderStatus: 'WAIT_DISPATCH',
  elderId: 'elder-a',
  serviceId: 'service-a',
  serviceName: '基础上门护理',
  addressId: 'address-a',
  scheduledStart: '2099-07-22T09:00',
  contactName: '张瑞嘉',
  statusLogs: []
};

const recommendationA = {
  nurseId: 'nurse-a',
  nurseName: '李护士',
  score: 96,
  matchedSkills: ['BASIC_CARE'],
  recommendReason: '护理资质和培训均在有效期内，当前预约时段可提供服务。',
  available: true
};

function success<T>(data: T) {
  return { code: 0, message: 'success', traceId: 'stage-30-component-test', data };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

beforeEach(() => {
  stageElevenApi.getAdminOrders.mockResolvedValue(success({
    records: [orderA],
    total: 1,
    page: 1,
    size: 10
  }));
  stageElevenApi.getAdminOrderDetail.mockResolvedValue(success({
    records: [orderA],
    total: 1,
    page: 1,
    size: 1
  }));
  stageThirtyApi.getPreferredNurse.mockResolvedValue({
    code: 404,
    message: 'not found',
    traceId: 'stage-30-component-test',
    data: { orderId: '', preferredNurseId: '', recommendReason: '' }
  });
  stageThirtyApi.getPreferredNursePermissions.mockResolvedValue(success([
    'NURSE_PREFERENCE_SELECT',
    'ORDER_CREATE'
  ]));
  stageThirtyApi.getPreferredNurseBindings.mockResolvedValue(success([{
    bindingId: 'binding-a',
    elderId: 'elder-a',
    elderName: '张瑞嘉',
    relationType: 'DAUGHTER',
    bindingStatus: 'ACTIVE',
    scopeCodes: ['ORDER_CREATE'],
    pendingScopeCodes: [],
    scopeUpdatePending: false
  }]));
  stageThirtyApi.updatePreferredNurse.mockResolvedValue(success({
    orderId: 'order-a',
    preferredNurseId: 'nurse-a',
    recommendReason: recommendationA.recommendReason
  }));
  stageTwentyNineApi.getOrderRecommendations.mockResolvedValue(success({
    orderId: 'order-a',
    nurses: [recommendationA]
  }));
});

describe('stage 30 mounted component behavior', () => {
  it('does not render admin recommendation entry without NURSE_RECOMMEND_VIEW', async () => {
    const wrapper = shallowMount(StageElevenAdminOrdersPanel, {
      props: {
        roleCode: 'ADMIN',
        authUser: { userId: 'admin-001', username: 'admin', roles: ['ADMIN'], menus: [] },
        canViewRecommendations: false
      },
      global: {
        stubs: {
          StageTwentyNineRecommendationPanel: RecommendationStub,
          StageThirtyAdminPreferenceSummary: AdminPreferenceStub
        }
      }
    });

    await flushPromises();
    expect(stageElevenApi.getAdminOrders).toHaveBeenCalledOnce();
    expect(wrapper.find('[data-test="recommendations"]').exists()).toBe(false);
    expect(wrapper.find('[data-test="admin-preference"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('当前账号无权查看护理推荐信息');

    await wrapper.setProps({ canViewRecommendations: true });
    expect(wrapper.find('[data-test="recommendations"]').exists()).toBe(true);
    expect(wrapper.find('[data-test="admin-preference"]').exists()).toBe(true);
    wrapper.unmount();
  });

  it('drops the old save result when the order changes during preference reread', async () => {
    let orderAReads = 0;
    let rereadStarted = false;
    const oldOrderReread = deferred<ReturnType<typeof success>>();
    stageThirtyApi.getPreferredNurse.mockImplementation((orderId: string) => {
      if (orderId === 'order-a') {
        orderAReads += 1;
        if (orderAReads === 1) {
          return Promise.resolve({
            code: 404,
            message: 'not found',
            traceId: 'stage-30-component-test',
            data: { orderId: '', preferredNurseId: '', recommendReason: '' }
          });
        }
        rereadStarted = true;
        return oldOrderReread.promise;
      }
      return Promise.resolve(success({
        orderId: 'order-b',
        preferredNurseId: 'nurse-b',
        recommendReason: '护理资质有效，符合订单B的服务条件。'
      }));
    });

    const wrapper = shallowMount(StageThirtyFamilyPreferencePanel, {
      props: {
        orderId: 'order-a',
        orderStatus: 'WAIT_DISPATCH',
        elderId: 'elder-a'
      },
      global: {
        stubs: {
          StageTwentyNineRecommendationPanel: RecommendationStub
        }
      }
    });

    await flushPromises();
    const recommendation = wrapper.findComponent(RecommendationStub);
    expect(recommendation.props('selectable')).toBe(true);
    recommendation.vm.$emit('selected', recommendationA);
    await flushPromises();
    expect(stageThirtyApi.updatePreferredNurse).toHaveBeenCalledWith('order-a', 'nurse-a');
    expect(rereadStarted).toBe(true);

    await wrapper.setProps({ orderId: 'order-b', elderId: 'elder-b' });
    await flushPromises();
    oldOrderReread.resolve(success({
      orderId: 'order-a',
      preferredNurseId: 'nurse-a',
      recommendReason: recommendationA.recommendReason
    }));
    await flushPromises();
    await nextTick();

    expect(wrapper.emitted('updated')).toBeUndefined();
    expect(wrapper.text()).not.toContain('已将李护士设为本单偏好护理');
    wrapper.unmount();
  });

  it('uses the order read model without depending on the current recommendation list', async () => {
    const wrapper = shallowMount(StageThirtyPreferenceBadge, {
      props: {
        orderId: 'order-a',
        preferredNurseName: '李护士',
        preferredNurseReason: recommendationA.recommendReason
      }
    });

    await flushPromises();
    expect(stageThirtyApi.getPreferredNurse).not.toHaveBeenCalled();
    expect(stageTwentyNineApi.getOrderRecommendations).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('李护士');
    expect(wrapper.text()).toContain(recommendationA.recommendReason);
    wrapper.unmount();
  });
});
