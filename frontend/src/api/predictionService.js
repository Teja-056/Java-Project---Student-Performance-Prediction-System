import client from './client';

export const predictionService = {
  getPrediction: async (rollNo) => {
    const res = await client.get(`/prediction/${encodeURIComponent(rollNo)}`);
    return res.data;
  },

  calculateCustomPrediction: async (rollNo, payload) => {
    const res = await client.post(`/prediction/${encodeURIComponent(rollNo)}/calculate`, payload);
    return res.data;
  },

  getAiInsights: async (rollNo) => {
    const res = await client.get(`/prediction/${encodeURIComponent(rollNo)}/ai-insights`);
    return res.data;
  },
};

export default predictionService;
