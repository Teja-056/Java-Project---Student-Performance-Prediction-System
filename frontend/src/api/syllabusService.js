import api from './client';

export const syllabusService = {
  async getAllSyllabuses(status = null) {
    const params = status && status !== 'ALL' ? { status } : {};
    const res = await api.get('/syllabus', { params });
    return res.data;
  },

  async getApprovedSyllabuses() {
    const res = await api.get('/syllabus/approved');
    return res.data;
  },

  async getReviewRequiredSyllabuses() {
    const res = await api.get('/syllabus/review-required');
    return res.data;
  },

  async uploadSyllabus(file, autoApprove = false) {
    const formData = new FormData();
    formData.append('file', file);
    const res = await api.post(`/syllabus/upload?autoApprove=${Boolean(autoApprove)}`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return res.data;
  },

  async getSyllabusById(id) {
    const res = await api.get(`/syllabus/${id}`);
    return res.data;
  },

  async getValidationReport(id) {
    const res = await api.get(`/syllabus/${id}/validation-report`);
    return res.data;
  },

  async updateSyllabus(id, data) {
    const res = await api.put(`/syllabus/${id}`, data);
    return res.data;
  },

  async revalidateSyllabus(id) {
    const res = await api.post(`/syllabus/${id}/revalidate`);
    return res.data;
  },

  async reprocessSyllabus(id) {
    const res = await api.post(`/syllabus/${id}/reprocess`);
    return res.data;
  },

  async approveSyllabus(id) {
    const res = await api.post(`/syllabus/${id}/approve`);
    return res.data;
  },

  async rejectSyllabus(id, reason = 'Rejected by faculty') {
    const res = await api.post(`/syllabus/${id}/reject`, { reason });
    return res.data;
  },
};
