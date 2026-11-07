import api from './client';

export const syllabusService = {
  async getAllSyllabuses() {
    const res = await api.get('/syllabus');
    return res.data;
  },

  async uploadSyllabus(file, autoApprove = true) {
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

  async approveSyllabus(id) {
    const res = await api.post(`/syllabus/${id}/approve`);
    return res.data;
  },
};
