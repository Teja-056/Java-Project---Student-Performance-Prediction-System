import React, { useState, useEffect } from 'react';
import {
  BookOpen,
  Upload,
  CheckCircle2,
  Clock,
  Layers,
  ChevronDown,
  ChevronUp,
  FileText,
  AlertCircle,
  AlertTriangle,
  Info,
  FileCheck,
  Check,
  X,
  Trash2,
  Plus,
  Save,
  RefreshCw,
  ShieldCheck,
  Edit3,
  Calendar,
  Building,
  GraduationCap,
  Sparkles,
  ArrowRight,
  ExternalLink,
} from 'lucide-react';
import { syllabusService } from '../api/syllabusService';
import Badge from '../components/common/Badge';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorAlert from '../components/common/ErrorAlert';
import EmptyState from '../components/common/EmptyState';
import StatCard from '../components/common/StatCard';

export default function SyllabusPage() {
  const [syllabuses, setSyllabuses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionSuccess, setActionSuccess] = useState(null);

  // Filters
  const [statusFilter, setStatusFilter] = useState('ALL');

  // Upload state
  const [uploadFile, setUploadFile] = useState(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState(null);

  // Selected document for review/editing
  const [selectedDocId, setSelectedDocId] = useState(null);
  const [selectedDoc, setSelectedDoc] = useState(null);
  const [loadingDoc, setLoadingDoc] = useState(false);
  const [isDirty, setIsDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [revalidating, setRevalidating] = useState(false);
  const [reprocessing, setReprocessing] = useState(false);

  // Expanded subject accordions within selected document
  const [expandedSubjects, setExpandedSubjects] = useState({});

  // Modals
  const [showApproveModal, setShowApproveModal] = useState(false);
  const [approving, setApproving] = useState(false);
  const [showRejectModal, setShowRejectModal] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [rejecting, setRejecting] = useState(false);

  // New subject modal or inline state
  const [showAddSubject, setShowAddSubject] = useState(false);
  const [newSubjectName, setNewSubjectName] = useState('');
  const [newSubjectCode, setNewSubjectCode] = useState('');
  const [newSubjectCredits, setNewSubjectCredits] = useState('3');
  const [newSubjectCategory, setNewSubjectCategory] = useState('THEORY');
  const [newSubjectSemester, setNewSubjectSemester] = useState('III');

  useEffect(() => {
    loadSyllabuses();
  }, []);

  const loadSyllabuses = async (selectIdAfterLoad = null) => {
    try {
      setLoading(true);
      setError(null);
      const data = await syllabusService.getAllSyllabuses();
      setSyllabuses(data || []);

      const targetId = selectIdAfterLoad || selectedDocId;
      if (targetId) {
        await loadDocumentDetails(targetId);
      } else if (data && data.length > 0 && !selectedDocId) {
        // Find first review-required or first document to view
        const firstReview = data.find((d) => d.status === 'REVIEW_REQUIRED' || d.status === 'REVIEW');
        const defaultDoc = firstReview || data[0];
        const docId = defaultDoc.documentId || defaultDoc.id;
        setSelectedDocId(docId);
        await loadDocumentDetails(docId);
      }
    } catch (err) {
      setError(err.message || 'Failed to load syllabus documents');
    } finally {
      setLoading(false);
    }
  };

  const loadDocumentDetails = async (id) => {
    try {
      setLoadingDoc(true);
      const doc = await syllabusService.getSyllabusById(id);
      setSelectedDocId(id);
      
      // Standardize topicDetails on all units
      if (doc && doc.subjects) {
        doc.subjects.forEach((s) => {
          if (s.units) {
            s.units.forEach((u) => {
              if (!u.topicDetails || u.topicDetails.length === 0) {
                u.topicDetails = (u.topics || []).map((t) => ({
                  text: t,
                  pageNumber: u.pageNumber || null,
                }));
              }
            });
          }
        });
      }

      setSelectedDoc(doc);
      setIsDirty(false);

      // Expand the first subject by default
      if (doc && doc.subjects && doc.subjects.length > 0) {
        setExpandedSubjects({ 0: true });
      }
    } catch (err) {
      setError(`Failed to load details for document #${id}: ${err.message}`);
    } finally {
      setLoadingDoc(false);
    }
  };

  const handleUpload = async (e) => {
    e.preventDefault();
    if (!uploadFile) return;

    try {
      setUploading(true);
      setUploadError(null);
      setActionSuccess(null);
      const newDoc = await syllabusService.uploadSyllabus(uploadFile, false);
      const newId = newDoc.documentId || newDoc.id;
      setActionSuccess(`Document "${newDoc.filename}" uploaded successfully and extracted via Gemini. Ready for faculty review.`);
      setUploadFile(null);
      await loadSyllabuses(newId);
    } catch (err) {
      setUploadError(err.message || 'Failed to upload and parse syllabus PDF via Gemini');
    } finally {
      setUploading(false);
    }
  };

  const handleSave = async () => {
    if (!selectedDoc || !selectedDocId) return;

    try {
      setSaving(true);
      setError(null);
      setActionSuccess(null);

      // Sync topics array from topicDetails
      const payload = JSON.parse(JSON.stringify(selectedDoc));
      if (payload.subjects) {
        payload.subjects.forEach((s) => {
          if (s.units) {
            s.units.forEach((u) => {
              if (u.topicDetails) {
                u.topics = u.topicDetails.map((t) => t.text).filter(Boolean);
              }
            });
          }
        });
      }

      const updated = await syllabusService.updateSyllabus(selectedDocId, payload);
      setSelectedDoc(updated);
      setIsDirty(false);
      setActionSuccess('Curriculum draft changes saved and validated successfully.');
      await loadSyllabuses(selectedDocId);
    } catch (err) {
      setError(err.message || 'Failed to save curriculum changes.');
    } finally {
      setSaving(false);
    }
  };

  const handleRevalidate = async () => {
    if (!selectedDocId) return;

    try {
      setRevalidating(true);
      setError(null);
      setActionSuccess(null);
      const updated = await syllabusService.revalidateSyllabus(selectedDocId);
      setSelectedDoc(updated);
      setActionSuccess('Curriculum revalidated against official Semester III catalog.');
      await loadSyllabuses(selectedDocId);
    } catch (err) {
      setError(err.message || 'Revalidation failed.');
    } finally {
      setRevalidating(false);
    }
  };

  const handleReprocess = async (docIdToReprocess) => {
    const id = docIdToReprocess || selectedDocId;
    if (!id) return;
    if (!window.confirm(`Are you sure you want to reprocess document #${id} with Gemini native PDF extraction? This will create a fresh, clean revision in Review Required status and will not overwrite historical approved records.`)) {
      return;
    }
    try {
      setReprocessing(true);
      setError(null);
      setActionSuccess(null);
      const res = await syllabusService.reprocessSyllabus(id);
      const newId = res.documentId || res.id;
      setActionSuccess(`Document #${id} successfully reprocessed with Gemini! New clean revision #${newId} is ready for review.`);
      await loadSyllabuses(newId);
    } catch (err) {
      setError(err.message || 'Reprocessing failed.');
    } finally {
      setReprocessing(false);
    }
  };

  const handleApprove = async () => {
    if (!selectedDocId) return;

    try {
      setApproving(true);
      setError(null);
      const updated = await syllabusService.approveSyllabus(selectedDocId);
      setShowApproveModal(false);
      setActionSuccess(`Document "${updated.filename}" has been officially APPROVED! It is now locked and used for student predictions.`);
      await loadSyllabuses(selectedDocId);
    } catch (err) {
      setError(err.message || 'Approval failed.');
    } finally {
      setApproving(false);
    }
  };

  const handleReject = async () => {
    if (!selectedDocId) return;

    try {
      setRejecting(true);
      setError(null);
      const updated = await syllabusService.rejectSyllabus(selectedDocId, rejectReason || 'Rejected by faculty');
      setShowRejectModal(false);
      setRejectReason('');
      setActionSuccess(`Document "${updated.filename}" was marked as REJECTED (FAILED).`);
      await loadSyllabuses(selectedDocId);
    } catch (err) {
      setError(err.message || 'Rejection failed.');
    } finally {
      setRejecting(false);
    }
  };

  // Field edit helpers
  const updateSelectedDocMetadata = (field, value) => {
    setSelectedDoc((prev) => ({
      ...prev,
      [field]: value,
    }));
    setIsDirty(true);
  };

  const updateSubjectField = (sIdx, field, value) => {
    setSelectedDoc((prev) => {
      const next = { ...prev };
      const subjects = [...(next.subjects || [])];
      subjects[sIdx] = {
        ...subjects[sIdx],
        [field]: value,
      };
      next.subjects = subjects;
      return next;
    });
    setIsDirty(true);
  };

  const deleteSubject = (sIdx) => {
    if (!window.confirm(`Are you sure you want to delete subject "${selectedDoc?.subjects?.[sIdx]?.subjectName}"?`)) {
      return;
    }
    setSelectedDoc((prev) => {
      const next = { ...prev };
      const subjects = [...(next.subjects || [])];
      subjects.splice(sIdx, 1);
      next.subjects = subjects;
      return next;
    });
    setIsDirty(true);
  };

  const handleAddSubjectSubmit = (e) => {
    e.preventDefault();
    if (!newSubjectName.trim()) return;

    const newSubj = {
      subjectName: newSubjectName.trim(),
      courseCode: newSubjectCode.trim(),
      credits: newSubjectCredits.trim(),
      category: newSubjectCategory,
      semester: newSubjectSemester,
      departmentCode: 'CSE',
      totalMarks: '40',
      isOpenElective: newSubjectCategory === 'OPEN_ELECTIVE',
      units: [],
    };

    setSelectedDoc((prev) => ({
      ...prev,
      subjects: [...(prev.subjects || []), newSubj],
    }));

    setNewSubjectName('');
    setNewSubjectCode('');
    setNewSubjectCredits('3');
    setNewSubjectCategory('THEORY');
    setShowAddSubject(false);
    setIsDirty(true);

    // Expand the newly added subject
    const newIdx = (selectedDoc?.subjects?.length || 0);
    setExpandedSubjects((prev) => ({ ...prev, [newIdx]: true }));
  };

  const updateUnitField = (sIdx, uIdx, field, value) => {
    setSelectedDoc((prev) => {
      const next = { ...prev };
      const subjects = [...(next.subjects || [])];
      const units = [...(subjects[sIdx].units || [])];
      units[uIdx] = {
        ...units[uIdx],
        [field]: value,
      };
      subjects[sIdx] = { ...subjects[sIdx], units };
      next.subjects = subjects;
      return next;
    });
    setIsDirty(true);
  };

  const addUnit = (sIdx) => {
    setSelectedDoc((prev) => {
      const next = { ...prev };
      const subjects = [...(next.subjects || [])];
      const units = [...(subjects[sIdx].units || [])];
      const nextNum = units.length + 1;
      units.push({
        unitNumber: nextNum,
        unitTitle: `Unit ${nextNum}: New Unit Title`,
        pageNumber: null,
        topics: [],
        topicDetails: [],
      });
      subjects[sIdx] = { ...subjects[sIdx], units };
      next.subjects = subjects;
      return next;
    });
    setIsDirty(true);
  };

  const deleteUnit = (sIdx, uIdx) => {
    setSelectedDoc((prev) => {
      const next = { ...prev };
      const subjects = [...(next.subjects || [])];
      const units = [...(subjects[sIdx].units || [])];
      units.splice(uIdx, 1);
      // Re-number subsequent units
      units.forEach((u, idx) => {
        u.unitNumber = idx + 1;
      });
      subjects[sIdx] = { ...subjects[sIdx], units };
      next.subjects = subjects;
      return next;
    });
    setIsDirty(true);
  };

  const updateTopicField = (sIdx, uIdx, tIdx, field, value) => {
    setSelectedDoc((prev) => {
      const next = { ...prev };
      const subjects = [...(next.subjects || [])];
      const units = [...(subjects[sIdx].units || [])];
      const topicDetails = [...(units[uIdx].topicDetails || [])];
      topicDetails[tIdx] = {
        ...topicDetails[tIdx],
        [field]: value,
      };
      units[uIdx] = { ...units[uIdx], topicDetails };
      subjects[sIdx] = { ...subjects[sIdx], units };
      next.subjects = subjects;
      return next;
    });
    setIsDirty(true);
  };

  const addTopic = (sIdx, uIdx) => {
    setSelectedDoc((prev) => {
      const next = { ...prev };
      const subjects = [...(next.subjects || [])];
      const units = [...(subjects[sIdx].units || [])];
      const topicDetails = [...(units[uIdx].topicDetails || [])];
      topicDetails.push({
        text: 'New Topic Title',
        pageNumber: units[uIdx].pageNumber || null,
      });
      units[uIdx] = { ...units[uIdx], topicDetails };
      subjects[sIdx] = { ...subjects[sIdx], units };
      next.subjects = subjects;
      return next;
    });
    setIsDirty(true);
  };

  const deleteTopic = (sIdx, uIdx, tIdx) => {
    setSelectedDoc((prev) => {
      const next = { ...prev };
      const subjects = [...(next.subjects || [])];
      const units = [...(subjects[sIdx].units || [])];
      const topicDetails = [...(units[uIdx].topicDetails || [])];
      topicDetails.splice(tIdx, 1);
      units[uIdx] = { ...units[uIdx], topicDetails };
      subjects[sIdx] = { ...subjects[sIdx], units };
      next.subjects = subjects;
      return next;
    });
    setIsDirty(true);
  };

  const toggleSubjectExpanded = (sIdx) => {
    setExpandedSubjects((prev) => ({
      ...prev,
      [sIdx]: !prev[sIdx],
    }));
  };

  // Helper calculations for summary
  const getDocMetrics = (doc) => {
    if (!doc) return { subjects: 0, units: 0, topics: 0, credits: 0 };
    const subjects = doc.subjects || [];
    let units = 0;
    let topics = 0;
    let credits = 0;
    subjects.forEach((s) => {
      units += s.units ? s.units.length : 0;
      (s.units || []).forEach((u) => {
        topics += u.topicDetails ? u.topicDetails.length : (u.topics ? u.topics.length : 0);
      });
      const c = parseInt(String(s.credits || '').replace(/[^0-9]/g, ''), 10);
      if (!isNaN(c)) credits += c;
    });
    return {
      subjects: subjects.length,
      units,
      topics,
      credits,
    };
  };

  // Parse validation report object
  const getValidationReport = (doc) => {
    if (!doc) return null;
    if (typeof doc.validationReport === 'object' && doc.validationReport !== null) {
      return doc.validationReport;
    }
    if (doc.validationReportJson) {
      try {
        return JSON.parse(doc.validationReportJson);
      } catch (e) {
        return null;
      }
    }
    return null;
  };

  // Filtered documents list
  const filteredDocs = syllabuses.filter((d) => {
    if (statusFilter === 'ALL') return true;
    if (statusFilter === 'REVIEW_REQUIRED') return d.status === 'REVIEW_REQUIRED' || d.status === 'REVIEW';
    return d.status === statusFilter;
  });

  const selectedMetrics = getDocMetrics(selectedDoc);
  const selectedReport = getValidationReport(selectedDoc);
  const isApproved = selectedDoc?.status === 'APPROVED';

  return (
    <div className="space-y-6 max-w-7xl mx-auto pb-16">
      {/* 1. Header Banner */}
      <div className="bg-white p-6 rounded-2xl border border-slate-200/80 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-xl bg-indigo-50 text-indigo-600 flex items-center justify-center shrink-0">
              <BookOpen className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-xl font-bold text-slate-900 tracking-tight flex items-center gap-2">
                Syllabus Curriculum Management
                <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 border border-indigo-200">
                  Gemini Grounded
                </span>
              </h2>
              <p className="text-xs text-slate-500 mt-0.5">
                Official course curriculum, units, topics, and source page references grounded in MySQL for AI student recommendations.
              </p>
            </div>
          </div>
        </div>

        {/* Global Action / Refresh */}
        <div className="flex items-center gap-2">
          <button
            onClick={() => loadSyllabuses()}
            disabled={loading}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold text-slate-700 bg-slate-50 hover:bg-slate-100 rounded-xl transition-colors border border-slate-200 cursor-pointer"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            Refresh List
          </button>
        </div>
      </div>

      {/* Global Alerts */}
      <ErrorAlert message={error} retryAction={loadSyllabuses} />

      {actionSuccess && (
        <div className="p-4 rounded-xl bg-emerald-50 border border-emerald-200 text-emerald-800 text-sm flex items-start justify-between gap-3 shadow-xs">
          <div className="flex items-start gap-3">
            <CheckCircle2 className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
            <div>
              <p className="font-bold text-emerald-950">Action Completed</p>
              <p className="mt-0.5 text-xs text-emerald-800">{actionSuccess}</p>
            </div>
          </div>
          <button
            onClick={() => setActionSuccess(null)}
            className="text-emerald-700 hover:text-emerald-900 text-xs font-bold cursor-pointer"
          >
            ✕
          </button>
        </div>
      )}

      {/* 2. PDF Upload Box */}
      <div className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-bold text-slate-900 flex items-center gap-2">
            <Upload className="w-4 h-4 text-indigo-600" />
            Upload Official Syllabus PDF (Gemini Multimodal Extraction)
          </h3>
          <span className="text-xs text-slate-400">PDF up to 25MB • Up to 100 pages</span>
        </div>

        <form onSubmit={handleUpload} className="flex flex-col sm:flex-row items-center gap-3">
          <div className="relative flex-1 w-full">
            <input
              type="file"
              accept=".pdf"
              onChange={(e) => {
                if (e.target.files && e.target.files[0]) {
                  setUploadFile(e.target.files[0]);
                  setUploadError(null);
                }
              }}
              className="w-full text-xs text-slate-500 file:mr-4 file:py-2.5 file:px-4 file:rounded-xl file:border-0 file:text-xs file:font-semibold file:bg-indigo-50 file:text-indigo-700 hover:file:bg-indigo-100 cursor-pointer"
            />
          </div>

          <button
            type="submit"
            disabled={uploading || !uploadFile}
            className="w-full sm:w-auto inline-flex items-center justify-center gap-2 px-6 py-2.5 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 rounded-xl transition-all shadow-xs cursor-pointer shrink-0"
          >
            {uploading ? (
              <>
                <RefreshCw className="w-4 h-4 animate-spin" />
                Gemini Extracting Syllabus...
              </>
            ) : (
              <>
                <Sparkles className="w-4 h-4 text-indigo-200" />
                Upload & Extract with Gemini
              </>
            )}
          </button>
        </form>

        {uploadError && (
          <div className="mt-3 text-xs text-rose-600 flex items-center gap-1.5 p-3 rounded-xl bg-rose-50 border border-rose-100">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{uploadError}</span>
          </div>
        )}
      </div>

      {/* 3. Document Dashboard Filter Tabs & Registered Documents Table */}
      <div className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden">
        <div className="p-5 border-b border-slate-100 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <h3 className="text-sm font-bold text-slate-900">Registered Syllabus Documents</h3>
            <p className="text-xs text-slate-500 mt-0.5">
              Select a document to inspect or edit curriculum subjects, units, topics, and source page numbers.
            </p>
          </div>

          {/* Filter Pills */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 md:pb-0">
            {[
              { id: 'ALL', label: 'All Documents', count: syllabuses.length },
              {
                id: 'REVIEW_REQUIRED',
                label: 'Review Required',
                count: syllabuses.filter((d) => d.status === 'REVIEW_REQUIRED' || d.status === 'REVIEW').length,
              },
              { id: 'APPROVED', label: 'Approved', count: syllabuses.filter((d) => d.status === 'APPROVED').length },
              { id: 'PROCESSING', label: 'Processing', count: syllabuses.filter((d) => d.status === 'PROCESSING').length },
              { id: 'FAILED', label: 'Failed', count: syllabuses.filter((d) => d.status === 'FAILED').length },
            ].map((tab) => (
              <button
                key={tab.id}
                onClick={() => setStatusFilter(tab.id)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold transition-colors flex items-center gap-1.5 cursor-pointer whitespace-nowrap ${
                  statusFilter === tab.id
                    ? 'bg-indigo-600 text-white shadow-xs'
                    : 'bg-slate-100 text-slate-600 hover:bg-slate-200'
                }`}
              >
                {tab.label}
                <span
                  className={`text-[10px] px-1.5 py-0.2 rounded-full ${
                    statusFilter === tab.id ? 'bg-indigo-700 text-white' : 'bg-slate-200 text-slate-700'
                  }`}
                >
                  {tab.count}
                </span>
              </button>
            ))}
          </div>
        </div>

        {/* Documents Table / Grid */}
        {loading ? (
          <div className="p-8">
            <LoadingSpinner message="Loading syllabus documents from MySQL database..." />
          </div>
        ) : syllabuses.length === 0 ? (
          <div className="p-12 text-center">
            <EmptyState
              title="No syllabus uploaded yet"
              description="Upload an official syllabus PDF to get started."
              icon={BookOpen}
            />
          </div>
        ) : filteredDocs.length === 0 ? (
          <div className="p-8 text-center text-slate-400 text-xs">
            No syllabus documents found matching filter "{statusFilter}".
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-slate-600">
              <thead className="bg-slate-50/80 text-slate-500 font-semibold border-b border-slate-100">
                <tr>
                  <th className="py-3 px-4">Document / File</th>
                  <th className="py-3 px-4">Extraction & Scheme</th>
                  <th className="py-3 px-4">Semester & Branch</th>
                  <th className="py-3 px-4">Curriculum Scope</th>
                  <th className="py-3 px-4">Lifecycle Status</th>
                  <th className="py-3 px-4">Validation</th>
                  <th className="py-3 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredDocs.map((doc) => {
                  const docId = doc.documentId || doc.id;
                  const isCurrent = selectedDocId === docId;
                  const metrics = getDocMetrics(doc);
                  const report = getValidationReport(doc);
                  const isValid = report?.overallStatus === 'PASSED';
                  const hasWarn = report?.overallStatus === 'PASSED_WITH_WARNINGS';
                  const isGemini = doc.extractionEngine === 'GEMINI_FLASH_LITE';

                  return (
                    <tr
                      key={docId}
                      className={`hover:bg-slate-50/80 transition-colors ${
                        isCurrent ? 'bg-indigo-50/40 font-medium' : ''
                      }`}
                    >
                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-2">
                          <FileText className={`w-4 h-4 shrink-0 ${isCurrent ? 'text-indigo-600' : 'text-slate-400'}`} />
                          <div>
                            <span className="font-bold text-slate-900 block truncate max-w-xs">{doc.filename}</span>
                            <div className="flex items-center gap-1.5 text-[11px] text-slate-400 mt-0.5">
                              <span>ID #{docId}</span>
                              {doc.parentDocumentId && (
                                <span className="text-indigo-600 font-mono text-[10px]">
                                  (rev of #{doc.parentDocumentId})
                                </span>
                              )}
                              <span>• {doc.uploadedAt || 'Recent'}</span>
                            </div>
                          </div>
                        </div>
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-1.5 mb-1">
                          {isGemini ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold bg-purple-50 text-purple-700 border border-purple-200">
                              <Sparkles className="w-2.5 h-2.5 text-purple-600" /> Gemini Flash Lite
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-semibold bg-amber-50 text-amber-700 border border-amber-200">
                              Legacy Parser
                            </span>
                          )}
                        </div>
                        <span className="text-[11px] text-slate-500 block">
                          {doc.institution || 'Vasavi College of Engg.'} • {doc.academicScheme || 'CBCS'}
                        </span>
                      </td>
                      <td className="py-3.5 px-4">
                        <span className="text-slate-800 font-semibold">Sem {doc.semester || 'III'}</span>
                        <span className="text-[11px] text-slate-500 block">{doc.branch || 'CSE'}</span>
                      </td>
                      <td className="py-3.5 px-4">
                        <div className="flex items-center gap-1.5 flex-wrap">
                          <span className="px-2 py-0.5 rounded bg-slate-100 text-slate-700 font-mono text-[11px]">
                            {doc.subjectCount || metrics.subjects} Subs
                          </span>
                          <span className="px-2 py-0.5 rounded bg-slate-100 text-slate-700 font-mono text-[11px]">
                            {doc.totalUnits || metrics.units} Units
                          </span>
                          <span className="px-2 py-0.5 rounded bg-indigo-50 text-indigo-700 font-mono text-[11px]">
                            {doc.totalCredits || metrics.credits} Cr
                          </span>
                        </div>
                      </td>
                      <td className="py-3.5 px-4">
                        <span
                          className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-semibold border ${
                            doc.status === 'APPROVED'
                              ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                              : doc.status === 'REVIEW_REQUIRED' || doc.status === 'REVIEW'
                              ? 'bg-blue-50 text-blue-700 border-blue-200'
                              : doc.status === 'PROCESSING'
                              ? 'bg-amber-50 text-amber-700 border-amber-200'
                              : 'bg-rose-50 text-rose-700 border-rose-200'
                          }`}
                        >
                          {doc.status === 'APPROVED' && <CheckCircle2 className="w-3 h-3 text-emerald-600" />}
                          {doc.status === 'REVIEW_REQUIRED' && <Clock className="w-3 h-3 text-blue-600" />}
                          {doc.status === 'PROCESSING' && <RefreshCw className="w-3 h-3 text-amber-600 animate-spin" />}
                          {doc.status === 'FAILED' && <AlertCircle className="w-3 h-3 text-rose-600" />}
                          {doc.status}
                        </span>
                      </td>
                      <td className="py-3.5 px-4">
                        {report ? (
                          <span
                            className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-md text-[11px] font-semibold ${
                              isValid
                                ? 'bg-emerald-50 text-emerald-700 border border-emerald-200'
                                : hasWarn
                                ? 'bg-amber-50 text-amber-700 border border-amber-200'
                                : 'bg-rose-50 text-rose-700 border border-rose-200'
                            }`}
                          >
                            {isValid ? 'Passed (8/8)' : hasWarn ? 'Warnings' : 'Errors'}
                          </span>
                        ) : (
                          <span className="text-slate-400 text-[11px]">Pending</span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          {doc.hasPdfOnDisk ? (
                            <button
                              onClick={() => handleReprocess(docId)}
                              disabled={reprocessing}
                              title="Reprocess with Gemini native multimodal extraction"
                              className="inline-flex items-center gap-1 px-2 py-1.5 rounded-xl text-xs font-semibold text-purple-700 bg-purple-50 hover:bg-purple-100 border border-purple-200 transition-all cursor-pointer"
                            >
                              <RefreshCw className={`w-3 h-3 ${reprocessing ? 'animate-spin' : ''}`} />
                              <span className="hidden sm:inline">Reprocess</span>
                            </button>
                          ) : (
                            <span className="text-[10px] text-slate-400 italic">No PDF</span>
                          )}
                          <button
                            onClick={() => loadDocumentDetails(docId)}
                            className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
                              isCurrent
                                ? 'bg-indigo-600 text-white shadow-xs'
                                : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                            }`}
                          >
                            <Edit3 className="w-3.5 h-3.5" />
                            {isCurrent ? 'Viewing' : 'Inspect'}
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* 4. Document Review & Inline Editing Workspace */}
      {loadingDoc ? (
        <div className="bg-white rounded-2xl border border-slate-200/80 p-12 text-center shadow-xs">
          <LoadingSpinner message="Loading full curriculum details, units, topics, and validation report..." />
        </div>
      ) : syllabuses.length === 0 ? (
        <div className="bg-white rounded-2xl border border-slate-200/80 p-12 shadow-xs">
          <EmptyState
            title="No syllabus uploaded yet"
            description="Upload an official syllabus PDF to get started."
            icon={BookOpen}
          />
        </div>
      ) : !selectedDoc ? (
        <div className="bg-white rounded-2xl border border-slate-200/80 p-8 shadow-xs">
          <EmptyState
            title="No syllabus document selected"
            description="Select a document from the table above to review and edit subjects, units, and topics."
            icon={BookOpen}
          />
        </div>
      ) : (
        <div className="space-y-6">
          {/* Document Header & Actions Bar */}
          <div className="bg-white rounded-2xl border border-slate-200/80 p-6 shadow-xs">
            <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 pb-6 border-b border-slate-100">
              <div>
                <div className="flex items-center gap-3 flex-wrap">
                  <h3 className="text-lg font-bold text-slate-900 tracking-tight flex items-center gap-2">
                    {selectedDoc.filename}
                  </h3>
                  <span
                    className={`inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-bold border ${
                      isApproved
                        ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                        : selectedDoc.status === 'REVIEW_REQUIRED' || selectedDoc.status === 'REVIEW'
                        ? 'bg-blue-50 text-blue-700 border-blue-200'
                        : 'bg-rose-50 text-rose-700 border-rose-200'
                    }`}
                  >
                    {isApproved ? <ShieldCheck className="w-3.5 h-3.5 text-emerald-600" /> : <Clock className="w-3.5 h-3.5 text-blue-600" />}
                    {selectedDoc.status}
                  </span>
                  {isDirty && (
                    <span className="text-xs font-semibold px-2 py-0.5 rounded-full bg-amber-50 text-amber-700 border border-amber-200 animate-pulse">
                      ● Unsaved edits
                    </span>
                  )}
                </div>
                <p className="text-xs text-slate-500 mt-1 flex items-center gap-2 flex-wrap">
                  <span>Document ID: #{selectedDocId}</span>
                  {selectedDoc.parentDocumentId && (
                    <span className="font-semibold text-indigo-600 bg-indigo-50 border border-indigo-200 px-2 py-0.5 rounded text-[10px]">
                      Derived Revision of Document #{selectedDoc.parentDocumentId}
                    </span>
                  )}
                  <span>• Uploaded: {selectedDoc.uploadedAt || 'N/A'}</span>
                  <span className="font-mono text-[10px] text-slate-600">
                    [{selectedDoc.extractionEngine === 'GEMINI_FLASH_LITE' ? 'Gemini Flash Lite' : 'Legacy Parser'}]
                  </span>
                </p>
              </div>

              {/* Action Buttons Toolbar */}
              <div className="flex items-center gap-2 flex-wrap">
                {!isApproved && (
                  <>
                    <button
                      onClick={handleSave}
                      disabled={saving || !isDirty}
                      className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-40 rounded-xl transition-all shadow-xs cursor-pointer"
                    >
                      <Save className="w-3.5 h-3.5" />
                      {saving ? 'Saving Changes...' : 'Save Draft Changes'}
                    </button>

                    <button
                      onClick={handleRevalidate}
                      disabled={revalidating}
                      className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold text-slate-700 bg-slate-50 hover:bg-slate-100 rounded-xl transition-colors border border-slate-200 cursor-pointer"
                    >
                      <RefreshCw className={`w-3.5 h-3.5 ${revalidating ? 'animate-spin' : ''}`} />
                      {revalidating ? 'Validating...' : 'Revalidate'}
                    </button>

                    {selectedDoc.hasPdfOnDisk && (
                      <button
                        onClick={() => handleReprocess(selectedDocId)}
                        disabled={reprocessing}
                        className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold text-purple-700 bg-purple-50 hover:bg-purple-100 border border-purple-200 rounded-xl transition-colors cursor-pointer"
                        title="Re-extract entire document from original PDF via Gemini"
                      >
                        <RefreshCw className={`w-3.5 h-3.5 ${reprocessing ? 'animate-spin' : ''}`} />
                        {reprocessing ? 'Reprocessing...' : 'Reprocess with Gemini'}
                      </button>
                    )}

                    <button
                      onClick={() => setShowApproveModal(true)}
                      className="inline-flex items-center gap-1.5 px-4 py-2 text-xs font-semibold text-emerald-700 bg-emerald-50 hover:bg-emerald-100 border border-emerald-200 rounded-xl transition-colors cursor-pointer"
                    >
                      <Check className="w-3.5 h-3.5" />
                      Approve Curriculum
                    </button>

                    <button
                      onClick={() => setShowRejectModal(true)}
                      className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold text-rose-700 bg-rose-50 hover:bg-rose-100 border border-rose-200 rounded-xl transition-colors cursor-pointer"
                    >
                      <X className="w-3.5 h-3.5" />
                      Reject Document
                    </button>
                  </>
                )}

                {isApproved && (
                  <div className="flex items-center gap-2 flex-wrap">
                    <div className="flex items-center gap-2 text-xs text-emerald-800 bg-emerald-50 border border-emerald-200 px-3.5 py-2 rounded-xl">
                      <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0" />
                      <span>Official Approved Curriculum • Historical Record (Read Only)</span>
                    </div>
                    {selectedDoc.hasPdfOnDisk && (
                      <button
                        onClick={() => handleReprocess(selectedDocId)}
                        disabled={reprocessing}
                        className="inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-semibold text-purple-700 bg-purple-50 hover:bg-purple-100 border border-purple-200 rounded-xl transition-colors cursor-pointer"
                        title="Create a fresh review revision extracted via Gemini without modifying this approved record"
                      >
                        <RefreshCw className={`w-3.5 h-3.5 ${reprocessing ? 'animate-spin' : ''}`} />
                        {reprocessing ? 'Reprocessing...' : 'Reprocess with Gemini'}
                      </button>
                    )}
                  </div>
                )}
              </div>
            </div>

            {/* Editable Document Metadata Fields */}
            <div className="mt-5 grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
              <div>
                <label className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-1">
                  Institution
                </label>
                <input
                  type="text"
                  disabled={isApproved}
                  value={selectedDoc.institution || ''}
                  onChange={(e) => updateSelectedDocMetadata('institution', e.target.value)}
                  placeholder="e.g. Vasavi College of Engg."
                  className="w-full text-xs px-3 py-2 rounded-xl border border-slate-200 bg-white disabled:bg-slate-50 text-slate-800 focus:outline-hidden focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-1">
                  Academic Scheme
                </label>
                <input
                  type="text"
                  disabled={isApproved}
                  value={selectedDoc.academicScheme || ''}
                  onChange={(e) => updateSelectedDocMetadata('academicScheme', e.target.value)}
                  placeholder="e.g. CBCS"
                  className="w-full text-xs px-3 py-2 rounded-xl border border-slate-200 bg-white disabled:bg-slate-50 text-slate-800 focus:outline-hidden focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-1">
                  Academic Year
                </label>
                <input
                  type="text"
                  disabled={isApproved}
                  value={selectedDoc.academicYear || ''}
                  onChange={(e) => updateSelectedDocMetadata('academicYear', e.target.value)}
                  placeholder="e.g. 2025-2026"
                  className="w-full text-xs px-3 py-2 rounded-xl border border-slate-200 bg-white disabled:bg-slate-50 text-slate-800 focus:outline-hidden focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-1">
                  Branch
                </label>
                <input
                  type="text"
                  disabled={isApproved}
                  value={selectedDoc.branch || ''}
                  onChange={(e) => updateSelectedDocMetadata('branch', e.target.value)}
                  placeholder="e.g. CSE"
                  className="w-full text-xs px-3 py-2 rounded-xl border border-slate-200 bg-white disabled:bg-slate-50 text-slate-800 focus:outline-hidden focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                />
              </div>

              <div>
                <label className="block text-[11px] font-semibold text-slate-500 uppercase tracking-wider mb-1">
                  Semester
                </label>
                <input
                  type="text"
                  disabled={isApproved}
                  value={selectedDoc.semester || ''}
                  onChange={(e) => updateSelectedDocMetadata('semester', e.target.value)}
                  placeholder="e.g. III"
                  className="w-full text-xs px-3 py-2 rounded-xl border border-slate-200 bg-white disabled:bg-slate-50 text-slate-800 focus:outline-hidden focus:ring-2 focus:ring-indigo-500/20 focus:border-indigo-500"
                />
              </div>
            </div>
          </div>

          {/* 5. Extraction Summary Metric Cards */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
            <StatCard
              title="Curriculum Subjects"
              value={`${selectedMetrics.subjects} / 8`}
              subtitle={selectedMetrics.subjects === 8 ? 'Complete 8 subjects' : 'Expected 8 subjects'}
              icon={BookOpen}
              colorScheme={selectedMetrics.subjects === 8 ? 'emerald' : 'indigo'}
            />
            <StatCard
              title="Curriculum Units"
              value={`${selectedMetrics.units} / 31`}
              subtitle={selectedMetrics.units === 31 ? 'All 31 units present' : 'Expected 31 units'}
              icon={Layers}
              colorScheme={selectedMetrics.units === 31 ? 'emerald' : 'indigo'}
            />
            <StatCard
              title="Total Topics"
              value={selectedMetrics.topics}
              subtitle="Fine-grained syllabus topics"
              icon={FileText}
              colorScheme="indigo"
            />
            <StatCard
              title="Semester Credits"
              value={`${selectedMetrics.credits} / 19`}
              subtitle={selectedMetrics.credits === 19 ? 'Standard 19 credits' : 'Expected 19 credits'}
              icon={GraduationCap}
              colorScheme={selectedMetrics.credits === 19 ? 'emerald' : 'amber'}
            />
          </div>

          {/* 6. Validation Panel (Collapsible Alert Center) */}
          {selectedReport && (
            <div
              className={`rounded-2xl border p-5 transition-all shadow-xs ${
                selectedReport.overallStatus === 'PASSED'
                  ? 'bg-emerald-50/50 border-emerald-200'
                  : selectedReport.overallStatus === 'PASSED_WITH_WARNINGS'
                  ? 'bg-amber-50/50 border-amber-200'
                  : 'bg-rose-50/50 border-rose-200'
              }`}
            >
              <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-2.5">
                  {selectedReport.overallStatus === 'PASSED' && <CheckCircle2 className="w-5 h-5 text-emerald-600" />}
                  {selectedReport.overallStatus === 'PASSED_WITH_WARNINGS' && (
                    <AlertTriangle className="w-5 h-5 text-amber-600" />
                  )}
                  {selectedReport.overallStatus === 'FAILED' && <AlertCircle className="w-5 h-5 text-rose-600" />}

                  <div>
                    <h4 className="text-xs font-bold text-slate-900 tracking-tight">
                      Validation Report: {selectedReport.overallStatus}
                    </h4>
                    <p className="text-[11px] text-slate-600 mt-0.5">
                      {selectedReport.errors?.length || 0} Error(s) • {selectedReport.warnings?.length || 0} Warning(s) •{' '}
                      {selectedReport.info?.length || 0} Notice(s)
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <span className="text-[11px] font-mono px-2.5 py-0.5 rounded-full bg-white border border-slate-200 text-slate-700">
                    Matched {selectedReport.matchedSubjectCount || 0} / {selectedReport.subjectCount || 0}
                  </span>
                </div>
              </div>

              {/* Messages Breakdown */}
              <div className="mt-4 space-y-2 text-xs">
                {selectedReport.errors && selectedReport.errors.length > 0 && (
                  <div className="space-y-1">
                    <span className="font-bold text-rose-800 text-[11px] uppercase tracking-wider block">
                      Errors (Must resolve before approval)
                    </span>
                    {selectedReport.errors.map((err, i) => (
                      <div key={i} className="p-2 rounded-lg bg-white border border-rose-200 text-rose-800 flex items-start gap-2">
                        <AlertCircle className="w-3.5 h-3.5 text-rose-600 shrink-0 mt-0.5" />
                        <span>{err}</span>
                      </div>
                    ))}
                  </div>
                )}

                {selectedReport.warnings && selectedReport.warnings.length > 0 && (
                  <div className="space-y-1">
                    <span className="font-bold text-amber-800 text-[11px] uppercase tracking-wider block">
                      Warnings & Catalog Advisories
                    </span>
                    {selectedReport.warnings.map((warn, i) => (
                      <div key={i} className="p-2 rounded-lg bg-white border border-amber-200 text-amber-800 flex items-start gap-2">
                        <AlertTriangle className="w-3.5 h-3.5 text-amber-600 shrink-0 mt-0.5" />
                        <span>{warn}</span>
                      </div>
                    ))}
                  </div>
                )}

                {selectedReport.info && selectedReport.info.length > 0 && (
                  <div className="space-y-1">
                    <span className="font-bold text-sky-800 text-[11px] uppercase tracking-wider block">
                      Catalog Mapping & Source Traceability Notices
                    </span>
                    {selectedReport.info.map((inf, i) => (
                      <div key={i} className="p-2 rounded-lg bg-white border border-sky-200 text-sky-800 flex items-start gap-2">
                        <Info className="w-3.5 h-3.5 text-sky-600 shrink-0 mt-0.5" />
                        <span>{inf}</span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* 7. Subject Accordions & Unit / Topic Inline Editors */}
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <div>
                <h3 className="text-sm font-bold text-slate-900">
                  Curriculum Subjects & Units ({selectedDoc.subjects?.length || 0})
                </h3>
                <p className="text-xs text-slate-500 mt-0.5">
                  Click any subject accordion to inspect and edit units, topics, and source page numbers.
                </p>
              </div>

              {!isApproved && (
                <button
                  onClick={() => setShowAddSubject(true)}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-indigo-700 bg-indigo-50 hover:bg-indigo-100 border border-indigo-200 rounded-xl transition-colors cursor-pointer"
                >
                  <Plus className="w-3.5 h-3.5" />
                  Add Subject
                </button>
              )}
            </div>

            {/* Add Subject Modal / Form */}
            {showAddSubject && (
              <form onSubmit={handleAddSubjectSubmit} className="bg-indigo-50/50 p-4 rounded-2xl border border-indigo-200 space-y-3">
                <div className="flex items-center justify-between">
                  <h4 className="text-xs font-bold text-indigo-950 flex items-center gap-1.5">
                    <Plus className="w-3.5 h-3.5" /> Add New Course Subject
                  </h4>
                  <button
                    type="button"
                    onClick={() => setShowAddSubject(false)}
                    className="text-slate-400 hover:text-slate-600 text-xs cursor-pointer"
                  >
                    Cancel
                  </button>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-4 gap-3">
                  <div className="sm:col-span-2">
                    <label className="block text-[11px] font-medium text-slate-700 mb-1">Subject Name *</label>
                    <input
                      type="text"
                      required
                      value={newSubjectName}
                      onChange={(e) => setNewSubjectName(e.target.value)}
                      placeholder="e.g. Operating Systems"
                      className="w-full text-xs px-3 py-1.5 rounded-xl border border-slate-300 bg-white"
                    />
                  </div>
                  <div>
                    <label className="block text-[11px] font-medium text-slate-700 mb-1">Course Code</label>
                    <input
                      type="text"
                      value={newSubjectCode}
                      onChange={(e) => setNewSubjectCode(e.target.value)}
                      placeholder="e.g. UI25PC302CS"
                      className="w-full text-xs px-3 py-1.5 rounded-xl border border-slate-300 bg-white font-mono"
                    />
                  </div>
                  <div>
                    <label className="block text-[11px] font-medium text-slate-700 mb-1">Credits</label>
                    <input
                      type="text"
                      value={newSubjectCredits}
                      onChange={(e) => setNewSubjectCredits(e.target.value)}
                      className="w-full text-xs px-3 py-1.5 rounded-xl border border-slate-300 bg-white"
                    />
                  </div>
                </div>
                <div className="flex justify-end gap-2 pt-1">
                  <button
                    type="button"
                    onClick={() => setShowAddSubject(false)}
                    className="px-3 py-1 text-xs text-slate-600 hover:bg-slate-200 rounded-lg cursor-pointer"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    className="px-4 py-1 text-xs font-semibold text-white bg-indigo-600 hover:bg-indigo-700 rounded-lg cursor-pointer shadow-xs"
                  >
                    Save Subject
                  </button>
                </div>
              </form>
            )}

            {/* Subjects List Accordions */}
            {selectedDoc.subjects?.map((subj, sIdx) => {
              const isExpanded = Boolean(expandedSubjects[sIdx]);
              const unitCount = subj.units?.length || 0;
              const topicCount = (subj.units || []).reduce((acc, u) => acc + (u.topicDetails?.length || u.topics?.length || 0), 0);
              const subName = (subj.subjectName || '').toLowerCase();
              const hasContamination = (selectedReport?.messages || []).some(
                (m) => m.severity === 'ERROR' && m.category === 'CONTAMINATION' && m.subjectName?.toLowerCase() === subName
              );

              return (
                <div
                  key={sIdx}
                  className="bg-white rounded-2xl border border-slate-200/80 shadow-xs overflow-hidden transition-all"
                >
                  {/* Subject Accordion Header */}
                  <div
                    onClick={() => toggleSubjectExpanded(sIdx)}
                    className="p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-slate-50/70 hover:bg-slate-100/70 cursor-pointer transition-colors"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 rounded-xl bg-indigo-50 text-indigo-700 font-bold text-xs flex items-center justify-center shrink-0">
                        {sIdx + 1}
                      </div>
                      <div>
                        <div className="flex items-center gap-2 flex-wrap">
                          <span className="font-bold text-sm text-slate-900">{subj.subjectName}</span>
                          {subj.courseCode && (
                            <span className="text-[11px] font-mono px-2 py-0.5 rounded bg-slate-200 text-slate-800">
                              {subj.courseCode}
                            </span>
                          )}
                          <span className="text-[11px] font-semibold px-2 py-0.5 rounded bg-indigo-50 text-indigo-700 border border-indigo-200">
                            {subj.credits || 0} Credits
                          </span>
                          <span className="text-[11px] font-semibold px-2 py-0.5 rounded bg-slate-100 text-slate-600">
                            {subj.category}
                          </span>
                          {subj.matchedSubjectDisplayName && (
                            <span className="text-[11px] font-semibold px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-700 border border-emerald-200">
                              ✓ {subj.matchedSubjectDisplayName}
                            </span>
                          )}
                          {hasContamination && (
                            <span className="text-[11px] font-bold px-2 py-0.5 rounded-md bg-rose-50 text-rose-700 border border-rose-200 animate-pulse">
                              ⚠ Contamination Alert
                            </span>
                          )}
                        </div>
                        <span className="text-xs text-slate-500 mt-0.5 block">
                          Sem {subj.semester || 'III'} • {unitCount} Unit(s) • {topicCount} Topic(s)
                        </span>
                      </div>
                    </div>

                    <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                      {!isApproved && (
                        <button
                          type="button"
                          onClick={() => deleteSubject(sIdx)}
                          title="Delete Subject"
                          className="p-1.5 text-slate-400 hover:text-rose-600 rounded-lg hover:bg-rose-50 transition-colors cursor-pointer"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      )}
                      <button
                        type="button"
                        onClick={() => toggleSubjectExpanded(sIdx)}
                        className="p-1.5 text-slate-500 hover:text-slate-800 rounded-lg transition-colors cursor-pointer"
                      >
                        {isExpanded ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                      </button>
                    </div>
                  </div>

                  {/* Expanded Subject Content (Metadata Form + Units & Topics) */}
                  {isExpanded && (
                    <div className="p-5 border-t border-slate-100 space-y-6">
                      {/* Subject Metadata Edit Row */}
                      <div className="p-4 rounded-xl bg-slate-50/70 border border-slate-200/60">
                        <span className="text-[11px] font-bold text-slate-600 uppercase tracking-wider block mb-3">
                          Subject Metadata Editor
                        </span>
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-3">
                          <div>
                            <label className="block text-[11px] font-medium text-slate-500 mb-1">Subject Name</label>
                            <input
                              type="text"
                              disabled={isApproved}
                              value={subj.subjectName || ''}
                              onChange={(e) => updateSubjectField(sIdx, 'subjectName', e.target.value)}
                              className="w-full text-xs px-3 py-1.5 rounded-xl border border-slate-200 bg-white disabled:bg-slate-50 text-slate-800 font-medium"
                            />
                          </div>

                          <div>
                            <label className="block text-[11px] font-medium text-slate-500 mb-1">Course Code</label>
                            <input
                              type="text"
                              disabled={isApproved}
                              value={subj.courseCode || ''}
                              onChange={(e) => updateSubjectField(sIdx, 'courseCode', e.target.value)}
                              className="w-full text-xs px-3 py-1.5 rounded-xl border border-slate-200 bg-white disabled:bg-slate-50 text-slate-800 font-mono"
                            />
                          </div>

                          <div>
                            <label className="block text-[11px] font-medium text-slate-500 mb-1">Credits</label>
                            <input
                              type="text"
                              disabled={isApproved}
                              value={subj.credits || ''}
                              onChange={(e) => updateSubjectField(sIdx, 'credits', e.target.value)}
                              className="w-full text-xs px-3 py-1.5 rounded-xl border border-slate-200 bg-white disabled:bg-slate-50 text-slate-800"
                            />
                          </div>

                          <div>
                            <label className="block text-[11px] font-medium text-slate-500 mb-1">Category</label>
                            <select
                              disabled={isApproved}
                              value={subj.category || 'THEORY'}
                              onChange={(e) => updateSubjectField(sIdx, 'category', e.target.value)}
                              className="w-full text-xs px-3 py-1.5 rounded-xl border border-slate-200 bg-white disabled:bg-slate-50 text-slate-800 cursor-pointer"
                            >
                              <option value="THEORY">THEORY</option>
                              <option value="OPEN_ELECTIVE">OPEN_ELECTIVE</option>
                              <option value="LAB">LAB</option>
                              <option value="MANDATORY_NON_CREDIT">MANDATORY_NON_CREDIT</option>
                              <option value="OTHER">OTHER</option>
                            </select>
                          </div>

                          <div>
                            <label className="block text-[11px] font-medium text-slate-500 mb-1">Semester</label>
                            <input
                              type="text"
                              disabled={isApproved}
                              value={subj.semester || 'III'}
                              onChange={(e) => updateSubjectField(sIdx, 'semester', e.target.value)}
                              className="w-full text-xs px-3 py-1.5 rounded-xl border border-slate-200 bg-white disabled:bg-slate-50 text-slate-800"
                            />
                          </div>
                        </div>
                      </div>

                      {/* Units & Topics List */}
                      <div className="space-y-4">
                        <div className="flex items-center justify-between">
                          <h5 className="text-xs font-bold text-slate-800 uppercase tracking-wider">
                            Units ({subj.units?.length || 0})
                          </h5>
                          {!isApproved && (
                            <button
                              type="button"
                              onClick={() => addUnit(sIdx)}
                              className="inline-flex items-center gap-1 px-2.5 py-1 text-xs font-semibold text-indigo-700 bg-indigo-50 hover:bg-indigo-100 rounded-lg transition-colors cursor-pointer"
                            >
                              <Plus className="w-3 h-3" /> Add Unit
                            </button>
                          )}
                        </div>

                        {subj.units?.length === 0 ? (
                          <div className="p-4 rounded-xl bg-slate-50 border border-slate-200/60 text-slate-400 text-xs text-center">
                            {subj.category === 'OPEN_ELECTIVE' || subj.isOpenElective
                              ? 'Open Elective course units remain unassigned until student elective choice is confirmed.'
                              : 'No units defined for this subject yet.'}
                          </div>
                        ) : (
                          <div className="space-y-4">
                            {subj.units?.map((u, uIdx) => (
                              <div
                                key={uIdx}
                                className="p-4 rounded-xl bg-slate-50/80 border border-slate-200/80 space-y-3"
                              >
                                {/* Unit Header & Inputs */}
                                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                                  <div className="flex items-center gap-2 flex-1">
                                    <span className="text-xs font-bold text-indigo-700 px-2 py-1 rounded bg-indigo-100 shrink-0">
                                      Unit {u.unitNumber}
                                    </span>
                                    <input
                                      type="text"
                                      disabled={isApproved}
                                      value={u.unitTitle || ''}
                                      onChange={(e) => updateUnitField(sIdx, uIdx, 'unitTitle', e.target.value)}
                                      placeholder="Unit Title"
                                      className="flex-1 text-xs font-semibold px-3 py-1.5 rounded-lg border border-slate-200 bg-white disabled:bg-slate-50 text-slate-800"
                                    />
                                  </div>

                                  <div className="flex items-center gap-2">
                                    <div className="flex items-center gap-1.5 text-xs text-slate-500">
                                      <span className="text-[11px] text-slate-400">PDF Page:</span>
                                      <input
                                        type="number"
                                        disabled={isApproved}
                                        value={u.pageNumber ?? ''}
                                        onChange={(e) =>
                                          updateUnitField(
                                            sIdx,
                                            uIdx,
                                            'pageNumber',
                                            e.target.value ? parseInt(e.target.value, 10) : null
                                          )
                                        }
                                        placeholder="pg #"
                                        className="w-16 text-xs px-2 py-1 rounded-lg border border-slate-200 bg-white disabled:bg-slate-50 text-slate-800 text-center"
                                      />
                                    </div>

                                    {!isApproved && (
                                      <button
                                        type="button"
                                        onClick={() => deleteUnit(sIdx, uIdx)}
                                        title="Delete Unit"
                                        className="p-1.5 text-slate-400 hover:text-rose-600 rounded-lg hover:bg-rose-50 transition-colors cursor-pointer"
                                      >
                                        <Trash2 className="w-3.5 h-3.5" />
                                      </button>
                                    )}
                                  </div>
                                </div>

                                {/* Unit Topics Editor */}
                                <div className="pt-2 border-t border-slate-200/50 space-y-2">
                                  <div className="flex items-center justify-between">
                                    <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">
                                      Topics ({u.topicDetails?.length || 0})
                                    </span>
                                    {!isApproved && (
                                      <button
                                        type="button"
                                        onClick={() => addTopic(sIdx, uIdx)}
                                        className="inline-flex items-center gap-1 text-[11px] font-semibold text-indigo-600 hover:text-indigo-800 cursor-pointer"
                                      >
                                        <Plus className="w-3 h-3" /> Add Topic
                                      </button>
                                    )}
                                  </div>

                                  {u.topicDetails?.length === 0 ? (
                                    <div className="text-slate-400 text-xs italic">No topics added yet.</div>
                                  ) : (
                                    <div className="space-y-1.5">
                                      {u.topicDetails?.map((topic, tIdx) => (
                                        <div
                                          key={tIdx}
                                          className="flex items-center gap-2 bg-white p-2 rounded-lg border border-slate-200/70"
                                        >
                                          <span className="text-[10px] text-slate-400 w-5 text-right font-mono">
                                            {tIdx + 1}.
                                          </span>
                                          <input
                                            type="text"
                                            disabled={isApproved}
                                            value={topic.text || ''}
                                            onChange={(e) =>
                                              updateTopicField(sIdx, uIdx, tIdx, 'text', e.target.value)
                                            }
                                            placeholder="Topic description (verbatim wording)"
                                            className="flex-1 text-xs px-2 py-1 rounded border border-transparent hover:border-slate-200 focus:border-indigo-500 bg-transparent disabled:bg-slate-50 text-slate-800"
                                          />

                                          <div className="flex items-center gap-1 text-[11px] text-slate-400 shrink-0">
                                            <span>pg</span>
                                            <input
                                              type="number"
                                              disabled={isApproved}
                                              value={topic.pageNumber ?? ''}
                                              onChange={(e) =>
                                                updateTopicField(
                                                  sIdx,
                                                  uIdx,
                                                  tIdx,
                                                  'pageNumber',
                                                  e.target.value ? parseInt(e.target.value, 10) : null
                                                )
                                              }
                                              placeholder="pg"
                                              className="w-12 text-xs px-1.5 py-0.5 rounded border border-slate-200 text-center disabled:bg-slate-50"
                                            />
                                          </div>

                                          {!isApproved && (
                                            <button
                                              type="button"
                                              onClick={() => deleteTopic(sIdx, uIdx, tIdx)}
                                              title="Delete Topic"
                                              className="p-1 text-slate-300 hover:text-rose-600 rounded transition-colors cursor-pointer"
                                            >
                                              <Trash2 className="w-3 h-3" />
                                            </button>
                                          )}
                                        </div>
                                      ))}
                                    </div>
                                  )}
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* 8. Approval Confirmation Modal */}
      {showApproveModal && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-slate-200 space-y-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
                <ShieldCheck className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-bold text-slate-900">Approve Syllabus Curriculum</h3>
                <p className="text-xs text-slate-500">Document #{selectedDocId}</p>
              </div>
            </div>

            <div className="p-4 rounded-xl bg-slate-50 border border-slate-100 text-xs space-y-2 text-slate-700">
              <p>
                <strong>Filename:</strong> {selectedDoc?.filename}
              </p>
              <p>
                <strong>Semester:</strong> {selectedDoc?.semester || 'III'} • <strong>Scope:</strong>{' '}
                {selectedMetrics.subjects} Subjects • {selectedMetrics.units} Units • {selectedMetrics.credits} Credits
              </p>
              <div className="pt-2 border-t border-slate-200 text-amber-800 bg-amber-50/50 p-2 rounded-lg">
                <p className="font-semibold text-amber-900">Authoritative Source of Truth Notice:</p>
                Once approved, this document will be locked. It grounds Google Gemini's personalized student recommendations, SGPA, and SEE predictions.
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowApproveModal(false)}
                disabled={approving}
                className="px-4 py-2 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-xl cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleApprove}
                disabled={approving}
                className="inline-flex items-center gap-1.5 px-5 py-2 text-xs font-semibold text-white bg-emerald-600 hover:bg-emerald-700 rounded-xl shadow-xs cursor-pointer"
              >
                {approving ? (
                  <>
                    <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                    Approving...
                  </>
                ) : (
                  <>
                    <Check className="w-3.5 h-3.5" />
                    Confirm & Approve Curriculum
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* 9. Rejection Reason Modal */}
      {showRejectModal && (
        <div className="fixed inset-0 z-50 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-xl border border-slate-200 space-y-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 rounded-xl bg-rose-50 text-rose-600 flex items-center justify-center shrink-0">
                <AlertCircle className="w-6 h-6" />
              </div>
              <div>
                <h3 className="text-base font-bold text-slate-900">Reject Syllabus Document</h3>
                <p className="text-xs text-slate-500">Document #{selectedDocId}</p>
              </div>
            </div>

            <p className="text-xs text-slate-600">
              Please state why this syllabus document is being rejected (e.g., missing course details, incorrect academic scheme, corrupted page layout):
            </p>

            <textarea
              rows={3}
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="Enter reason for rejection..."
              className="w-full text-xs p-3 rounded-xl border border-slate-200 focus:outline-hidden focus:ring-2 focus:ring-rose-500/20 focus:border-rose-500"
            />

            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowRejectModal(false)}
                disabled={rejecting}
                className="px-4 py-2 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-xl cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleReject}
                disabled={rejecting}
                className="inline-flex items-center gap-1.5 px-5 py-2 text-xs font-semibold text-white bg-rose-600 hover:bg-rose-700 rounded-xl shadow-xs cursor-pointer"
              >
                {rejecting ? (
                  <>
                    <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                    Rejecting...
                  </>
                ) : (
                  <>
                    <X className="w-3.5 h-3.5" />
                    Confirm Rejection
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
