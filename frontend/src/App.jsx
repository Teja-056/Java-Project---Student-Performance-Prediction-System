import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import MainLayout from './components/layout/MainLayout';
import DashboardPage from './pages/DashboardPage';
import StudentsPage from './pages/StudentsPage';
import StudentReportPage from './pages/StudentReportPage';
import SubjectAnalyticsPage from './pages/SubjectAnalyticsPage';
import ExcelUploadPage from './pages/ExcelUploadPage';
import SyllabusPage from './pages/SyllabusPage';
import PredictionPage from './pages/PredictionPage';
import SubjectPerformancePage from './pages/SubjectPerformancePage';
import DatasetManagementPage from './pages/DatasetManagementPage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<MainLayout />}>
        <Route index element={<DashboardPage />} />
        <Route path="datasets" element={<DatasetManagementPage />} />
        <Route path="prediction" element={<PredictionPage />} />
        <Route path="prediction/:rollNo" element={<PredictionPage />} />
        <Route path="students" element={<StudentsPage />} />
        <Route path="students/:rollNo/report" element={<StudentReportPage />} />
        <Route path="subject-performance" element={<SubjectPerformancePage />} />
        <Route path="analytics" element={<SubjectAnalyticsPage />} />
        <Route path="excel-upload" element={<ExcelUploadPage />} />
        <Route path="syllabus" element={<SyllabusPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
