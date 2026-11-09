import api from './client';

export const datasetService = {
  async getAllDatasets() {
    const res = await api.get('/datasets');
    return res.data;
  },

  async getActiveDataset() {
    const res = await api.get('/datasets/active');
    return res.data;
  },

  async getDatasetById(id) {
    const res = await api.get(`/datasets/${id}`);
    return res.data;
  },

  async uploadDataset(formData) {
    const res = await api.post('/datasets/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return res.data;
  },

  async activateDataset(id) {
    const res = await api.post(`/datasets/${id}/activate`);
    return res.data;
  },

  async deleteDataset(id) {
    const res = await api.delete(`/datasets/${id}`);
    return res.data;
  },
};
