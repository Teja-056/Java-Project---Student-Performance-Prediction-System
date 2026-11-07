import api from './client';

export const analyticsService = {
  async getSubjectAnalytics() {
    const res = await api.get('/analytics/subjects');
    return res.data;
  },
};
