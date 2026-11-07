import api from './client';

export const studentService = {
  async getAllStudents() {
    const res = await api.get('/students');
    return res.data;
  },

  async getStudentByRoll(rollNo) {
    const res = await api.get(`/students/${encodeURIComponent(rollNo)}`);
    return res.data;
  },

  async getStudentReport(rollNo) {
    const res = await api.get(`/students/${encodeURIComponent(rollNo)}/report`);
    return res.data;
  },

  async getAiAnalysis(rollNo) {
    const res = await api.post(`/students/${encodeURIComponent(rollNo)}/ai-analysis`);
    return res.data;
  },

  async createStudent(payload) {
    const res = await api.post('/students', payload);
    return res.data;
  },

  async updateStudent(rollNo, payload) {
    const res = await api.put(`/students/${encodeURIComponent(rollNo)}`, payload);
    return res.data;
  },

  async deleteStudent(rollNo) {
    const res = await api.delete(`/students/${encodeURIComponent(rollNo)}`);
    return res.data;
  },
};
