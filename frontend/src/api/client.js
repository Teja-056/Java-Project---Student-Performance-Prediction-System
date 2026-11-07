import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 30000, // 30 seconds for AI analysis calls
  headers: {
    'Accept': 'application/json',
  },
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    let message = 'An unexpected error occurred.';
    let status = 500;

    if (error.response) {
      status = error.response.status;
      const data = error.response.data;

      if (data && typeof data === 'object') {
        message = data.message || data.error || `Request failed with status ${status}`;
      } else if (typeof data === 'string' && data.length > 0) {
        message = data;
      } else {
        message = `Server returned status ${status}`;
      }
    } else if (error.request) {
      message = 'Cannot connect to backend server at http://localhost:8080. Please ensure the Spring Boot application is running.';
      status = 0;
    } else {
      message = error.message;
    }

    const enhancedError = new Error(message);
    enhancedError.status = status;
    enhancedError.originalError = error;
    return Promise.reject(enhancedError);
  }
);

export default api;
