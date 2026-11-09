import api from './client';

export const analyticsService = {
  async getSubjectAnalytics() {
    const res = await api.get('/analytics/subjects');
    return res.data;
  },

  async getSubjectPerformance(subject = 'DS', assessment = 'COMBINED') {
    const res = await api.get('/analytics/subject-performance', {
      params: { subject, assessment },
    });
    return res.data;
  },
};
