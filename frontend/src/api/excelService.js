import api from './client';

export const excelService = {
  async uploadExcel(file, commit = false) {
    const formData = new FormData();
    formData.append('file', file);
    const res = await api.post(`/excel/upload?commit=${Boolean(commit)}`, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    });
    return res.data;
  },

  async downloadTemplate() {
    const res = await api.get('/excel/template', {
      responseType: 'blob',
    });
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'student_import_template.xlsx';
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
  },
};
