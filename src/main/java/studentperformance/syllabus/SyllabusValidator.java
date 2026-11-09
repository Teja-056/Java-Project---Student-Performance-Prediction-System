package studentperformance.syllabus;

import studentperformance.Subject;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Deterministic Java validation layer for extracted syllabuses.
 * Validates subjects, course codes, credits, units, topics, and source page references
 * against the official Semester III catalog.
 */
@Component
public class SyllabusValidator {

    public static final int EXPECTED_SEMESTER_CREDITS = 19;
    public static final int EXPECTED_TOTAL_UNITS = 31;
    public static final int EXPECTED_SUBJECT_COUNT = 8;

    private static final List<String> CT_KEYWORDS = List.of(
            "fallacies", "fallacy", "logical reasoning", "opinion stating", "critical thinking",
            "cognitive bias", "evaluating arguments", "syllogisms", "syllogism", "identifying assumptions",
            "soundness and validity", "deductive reasoning", "inductive reasoning", "bias and fallacies"
    );

    private static final List<String> CS_KEYWORDS = List.of(
            "tuckman", "etiquette", "roald dahl", "interpersonal communication", "listening comprehension",
            "group discussion", "reading comprehension", "phonetics", "skimming and scanning", "oral presentation",
            "formal letter writing", "email etiquette"
    );

    private static final List<String> DS_KEYWORDS = List.of(
            "binary search tree", "linked list", "avl tree", "hash table", "dijkstra", "spanning tree",
            "quicksort", "stack operations", "arrays adt", "infix to postfix"
    );

    private static final List<String> TTPS_KEYWORDS = List.of(
            "poisson distribution", "fourier transform", "laplace transform", "normal distribution",
            "bayes theorem", "chi-square"
    );

    /**
     * Map of official Semester III expected unit counts per subject.
     */
    private static final Map<Subject, Integer> EXPECTED_UNIT_COUNTS = Map.of(
            Subject.DS, 5,
            Subject.JAVA, 5,
            Subject.CA, 5,
            Subject.TTPS, 5,
            Subject.OE, 0,
            Subject.CT, 4,
            Subject.HVPE, 3,
            Subject.CS, 4
    );

    public ValidationReport validate(ExtractedCurriculum curriculum) {
        boolean isSemester3 = true;
        if (curriculum != null && curriculum.getDocument() != null && curriculum.getDocument().getSemester() != null
                && !curriculum.getDocument().getSemester().isBlank()) {
            String sem = curriculum.getDocument().getSemester().trim().toUpperCase(Locale.ROOT);
            isSemester3 = sem.contains("III") || sem.equals("3") || sem.contains("THIRD");
        }
        boolean expectFullSemester = curriculum != null && curriculum.getSubjects().size() > 1 && isSemester3;
        return validate(curriculum, expectFullSemester);
    }

    public ValidationReport validate(ExtractedCurriculum curriculum, boolean expectFullSemester) {
        ValidationReport report = new ValidationReport();

        if (curriculum == null) {
            report.addMessage(ValidationMessage.error("DOCUMENT", "Curriculum cannot be null"));
            return report;
        }

        // 1. Document Metadata Validation
        validateDocumentMetadata(curriculum.getDocument(), report);

        List<ExtractedSubject> subjects = curriculum.getSubjects();
        report.setSubjectCount(subjects.size());

        if (subjects.isEmpty()) {
            report.addMessage(ValidationMessage.error("SUBJECT", "Curriculum contains no extracted subjects"));
            return report;
        }

        // 2. Catalog Mapping & Subject Completeness
        Map<Subject, ExtractedSubject> matchedSubjects = new EnumMap<>(Subject.class);
        Map<String, ExtractedSubject> courseCodeMap = new HashMap<>();
        int matchedCount = 0;
        int unresolvedCount = 0;
        int totalUnits = 0;
        int totalTopics = 0;
        int totalCredits = 0;

        for (ExtractedSubject subject : subjects) {
            SubjectValidationResult subResult = new SubjectValidationResult(
                    subject.getSubjectName(), subject.getCourseCode(), null);

            Subject matched = matchOfficialSubject(subject);
            subResult.setMatchedSubject(matched);

            // Course code validation
            String courseCode = subject.getCourseCode() == null ? "" : subject.getCourseCode().trim();
            if (courseCode.isEmpty()) {
                if (subject.isOpenElective()) {
                    subResult.addInfo("Open Elective-I course code is unresolved/placeholder pending student course selection");
                    report.addMessage(ValidationMessage.info("SUBJECT", "Open Elective-I course code unresolved",
                            subject.getSubjectName(), courseCode, null, subject.getStartPage()));
                } else {
                    subResult.addWarning("Course code is empty or missing");
                    report.addMessage(ValidationMessage.warning("SUBJECT", "Course code is empty or missing",
                            subject.getSubjectName(), courseCode, null, subject.getStartPage()));
                }
            } else {
                if (courseCodeMap.containsKey(courseCode)) {
                    ExtractedSubject prev = courseCodeMap.get(courseCode);
                    String err = String.format("Duplicate course code '%s' found in both '%s' and '%s'",
                            courseCode, prev.getSubjectName(), subject.getSubjectName());
                    subResult.addError(err);
                    report.addMessage(ValidationMessage.error("SUBJECT", err,
                            subject.getSubjectName(), courseCode, null, subject.getStartPage()));
                } else {
                    courseCodeMap.put(courseCode, subject);
                }
            }

            // Subject mapping check
            if (matched == null) {
                unresolvedCount++;
                String warn = String.format("Subject '%s' (%s) does not match any official Semester III subject",
                        subject.getSubjectName(), courseCode);
                subResult.addWarning(warn);
                report.addMessage(ValidationMessage.warning("SUBJECT", warn,
                        subject.getSubjectName(), courseCode, null, subject.getStartPage()));
            } else {
                if (matchedSubjects.containsKey(matched)) {
                    ExtractedSubject prev = matchedSubjects.get(matched);
                    String err = String.format("Duplicate subject mapping for %s: both '%s' and '%s' map to %s",
                            matched.name(), prev.getSubjectName(), subject.getSubjectName(), matched.getFullName());
                    subResult.addError(err);
                    report.addMessage(ValidationMessage.error("SUBJECT", err,
                            subject.getSubjectName(), courseCode, null, subject.getStartPage()));
                } else {
                    matchedSubjects.put(matched, subject);
                    matchedCount++;
                }
            }

            // 3. Credit Validation
            Integer extractedCredits = parseCredits(subject.getCredits());
            if (extractedCredits == null || extractedCredits <= 0) {
                String err = String.format("Invalid or unparseable credit value '%s' for %s",
                        subject.getCredits(), subject.getSubjectName());
                subResult.addError(err);
                report.addMessage(ValidationMessage.error("CREDITS", err,
                        subject.getSubjectName(), courseCode, null, subject.getStartPage()));
            } else {
                subResult.setExtractedCredits(extractedCredits);
                totalCredits += extractedCredits;

                if (matched != null) {
                    subResult.setExpectedCredits(matched.getCredits());
                    if (!extractedCredits.equals(matched.getCredits())) {
                        String err = String.format("Credit mismatch for %s: extracted %d credits, but official catalog requires %d",
                                subject.getSubjectName(), extractedCredits, matched.getCredits());
                        subResult.addError(err);
                        report.addMessage(ValidationMessage.error("CREDITS", err,
                                subject.getSubjectName(), courseCode, null, subject.getStartPage()));
                    }
                }
            }

            // 4. Unit & Topic Validation
            validateUnitsAndTopics(subject, matched, subResult, report);

            totalUnits += subResult.getUnitCount();
            totalTopics += subResult.getTopicCount();

            report.addSubjectResult(subResult);
        }

        report.setMatchedSubjectCount(matchedCount);
        report.setUnresolvedSubjectCount(unresolvedCount);
        report.setTotalUnits(totalUnits);
        report.setTotalTopics(totalTopics);
        report.setTotalCredits(totalCredits);

        // 5. Semester-Level Completeness Checks
        if (expectFullSemester) {
            for (Subject canonical : Subject.values()) {
                if (!matchedSubjects.containsKey(canonical)) {
                    String err = String.format("Missing required official Semester III subject: %s (%s)",
                            canonical.getFullName(), canonical.getCourseCode());
                    report.addMessage(ValidationMessage.error("SUBJECT", err));
                }
            }

            if (totalCredits != EXPECTED_SEMESTER_CREDITS) {
                String err = String.format("Total semester credits mismatch: expected %d credits, but sum of extracted subjects is %d",
                        EXPECTED_SEMESTER_CREDITS, totalCredits);
                report.addMessage(ValidationMessage.error("CREDITS", err));
            }

            if (totalUnits != EXPECTED_TOTAL_UNITS) {
                String warn = String.format("Total semester units count (%d) differs from expected catalog total (%d)",
                        totalUnits, EXPECTED_TOTAL_UNITS);
                report.addMessage(ValidationMessage.warning("UNIT", warn));
            }
        }

        return report;
    }

    private void validateDocumentMetadata(ExtractedDocumentMetadata doc, ValidationReport report) {
        if (doc == null) {
            report.addMessage(ValidationMessage.warning("DOCUMENT", "Document metadata is completely missing"));
            return;
        }

        if (doc.getInstitution() == null || doc.getInstitution().isBlank()) {
            report.addMessage(ValidationMessage.warning("DOCUMENT", "Institution name is missing or blank"));
        }

        if (doc.getSemester() != null && !doc.getSemester().isBlank()) {
            String sem = doc.getSemester().toUpperCase(Locale.ROOT).trim();
            if (!sem.contains("III") && !sem.equals("3")) {
                report.addMessage(ValidationMessage.warning("DOCUMENT",
                        "Extracted semester '" + doc.getSemester() + "' does not match expected Semester III"));
            }
        } else {
            report.addMessage(ValidationMessage.warning("DOCUMENT", "Semester metadata is missing or blank"));
        }
    }

    private void validateUnitsAndTopics(ExtractedSubject subject, Subject matched,
                                        SubjectValidationResult subResult, ValidationReport report) {
        boolean isOpenElective = subject.isOpenElective() || (matched == Subject.OE);
        subResult.setOpenElective(isOpenElective);

        // Open Elective-I handling
        if (isOpenElective) {
            if (subject.getUnits().isEmpty()) {
                subResult.addInfo("Open Elective-I is unresolved (0 units) pending student course selection");
                report.addMessage(ValidationMessage.info("UNIT", "Open Elective-I unresolved with 0 units as expected",
                        subject.getSubjectName(), subject.getCourseCode(), null, subject.getStartPage()));
            } else {
                subResult.addInfo("Open Elective-I contains " + subject.getUnits().size() + " preliminary units");
            }
            subResult.setUnitCount(subject.getUnits().size());
            subResult.setTopicCount(0);
            return;
        }

        // Standard Subjects
        List<ExtractedUnit> units = subject.getUnits();
        subResult.setUnitCount(units.size());

        Integer expectedUnits = matched != null ? EXPECTED_UNIT_COUNTS.get(matched) : null;
        if (expectedUnits != null && units.size() != expectedUnits) {
            String err = String.format("%s expects exactly %d units according to verified catalog, but found %d units",
                    subject.getSubjectName(), expectedUnits, units.size());
            subResult.addError(err);
            report.addMessage(ValidationMessage.error("UNIT", err,
                    subject.getSubjectName(), subject.getCourseCode(), null, subject.getStartPage()));
        }

        Set<Integer> seenUnitNumbers = new HashSet<>();
        int prevUnitNum = 0;
        int topicCount = 0;

        for (ExtractedUnit unit : units) {
            int unitNum = unit.getUnitNumber();

            // Duplicate unit number
            if (seenUnitNumbers.contains(unitNum)) {
                String err = String.format("Duplicate unit number %d in %s", unitNum, subject.getSubjectName());
                subResult.addError(err);
                report.addMessage(ValidationMessage.error("UNIT", err,
                        subject.getSubjectName(), subject.getCourseCode(), unitNum, unit.getPageNumber()));
            } else {
                seenUnitNumbers.add(unitNum);
            }

            // Ordering check
            if (unitNum < prevUnitNum) {
                String warn = String.format("Units in %s are not in ascending order (Unit %d appeared after Unit %d)",
                        subject.getSubjectName(), unitNum, prevUnitNum);
                subResult.addWarning(warn);
                report.addMessage(ValidationMessage.warning("UNIT", warn,
                        subject.getSubjectName(), subject.getCourseCode(), unitNum, unit.getPageNumber()));
            }
            prevUnitNum = unitNum;

            // Empty unit title check
            if (unit.getUnitTitle() == null || unit.getUnitTitle().trim().isEmpty()) {
                String warn = String.format("Unit %d in %s has an empty or blank title", unitNum, subject.getSubjectName());
                subResult.addWarning(warn);
                report.addMessage(ValidationMessage.warning("UNIT", warn,
                        subject.getSubjectName(), subject.getCourseCode(), unitNum, unit.getPageNumber()));
            }

            // Empty topic list check
            if (unit.getTopics().isEmpty()) {
                String err = String.format("Unit %d (%s) in %s has no topics",
                        unitNum, unit.getUnitTitle(), subject.getSubjectName());
                subResult.addError(err);
                report.addMessage(ValidationMessage.error("UNIT", err,
                        subject.getSubjectName(), subject.getCourseCode(), unitNum, unit.getPageNumber()));
            }

            // Missing source page reference check
            if (unit.getPageNumber() == null || unit.getPageNumber() <= 0) {
                String warn = String.format("Unit %d in %s is missing a source page reference",
                        unitNum, subject.getSubjectName());
                subResult.addWarning(warn);
                report.addMessage(ValidationMessage.warning("UNIT", warn,
                        subject.getSubjectName(), subject.getCourseCode(), unitNum, null));
            }

            // Topic validation
            Set<String> normalizedTopicsInUnit = new HashSet<>();
            for (ExtractedTopic topic : unit.getTopics()) {
                topicCount++;
                String text = topic.getTopicText();

                if (text == null || text.trim().isEmpty()) {
                    String err = String.format("Unit %d in %s contains an empty topic text",
                            unitNum, subject.getSubjectName());
                    subResult.addError(err);
                    report.addMessage(ValidationMessage.error("TOPIC", err,
                            subject.getSubjectName(), subject.getCourseCode(), unitNum, topic.getPageNumber()));
                    continue;
                }

                String trimmed = text.trim();
                // Suspiciously short topic text check
                if (trimmed.length() < 3 && !trimmed.matches("(?i)[a-z0-9]{3,}")) {
                    String warn = String.format("Unit %d in %s contains suspiciously short topic text: '%s'",
                            unitNum, subject.getSubjectName(), trimmed);
                    subResult.addWarning(warn);
                    report.addMessage(ValidationMessage.warning("TOPIC", warn,
                            subject.getSubjectName(), subject.getCourseCode(), unitNum, topic.getPageNumber()));
                }

                // Duplicate topic within unit check
                String norm = trimmed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                if (!norm.isEmpty() && !normalizedTopicsInUnit.add(norm)) {
                    String warn = String.format("Unit %d in %s contains duplicate topic: '%s'",
                            unitNum, subject.getSubjectName(), trimmed);
                    subResult.addWarning(warn);
                    report.addMessage(ValidationMessage.warning("TOPIC", warn,
                            subject.getSubjectName(), subject.getCourseCode(), unitNum, topic.getPageNumber()));
                }

                // Topic page reference check
                if (topic.getPageNumber() == null || topic.getPageNumber() <= 0) {
                    if (unit.getPageNumber() == null || unit.getPageNumber() <= 0) {
                        String warn = String.format("Topic '%s' in Unit %d of %s is missing a page reference",
                                trimmed, unitNum, subject.getSubjectName());
                        subResult.addWarning(warn);
                        report.addMessage(ValidationMessage.warning("TOPIC", warn,
                                subject.getSubjectName(), subject.getCourseCode(), unitNum, null));
                    }
                }

                // Cross-subject contamination check
                checkCrossSubjectContamination(subject, matched, unitNum, topic, subResult, report);
            }
        }

        subResult.setTopicCount(topicCount);

        // Contiguous unit check (e.g. 1..expectedUnits)
        if (expectedUnits != null) {
            for (int u = 1; u <= expectedUnits; u++) {
                if (!seenUnitNumbers.contains(u)) {
                    String err = String.format("Missing Unit %d in %s (expected %d units)",
                            u, subject.getSubjectName(), expectedUnits);
                    subResult.addError(err);
                    report.addMessage(ValidationMessage.error("UNIT", err,
                            subject.getSubjectName(), subject.getCourseCode(), u, subject.getStartPage()));
                }
            }
        }
    }

    private void checkCrossSubjectContamination(ExtractedSubject subject, Subject matched, int unitNum,
                                                ExtractedTopic topic, SubjectValidationResult subResult, ValidationReport report) {
        if (matched == null || topic == null || topic.getTopicText() == null) return;
        String textLower = topic.getTopicText().toLowerCase(Locale.ROOT);

        switch (matched) {
            case DS, JAVA, CA -> {
                checkKeyword(textLower, CT_KEYWORDS, "Critical Thinking", subject, unitNum, topic, subResult, report);
                checkKeyword(textLower, CS_KEYWORDS, "Communication Skills", subject, unitNum, topic, subResult, report);
            }
            case TTPS -> {
                checkKeyword(textLower, CT_KEYWORDS, "Critical Thinking", subject, unitNum, topic, subResult, report);
                checkKeyword(textLower, CS_KEYWORDS, "Communication Skills", subject, unitNum, topic, subResult, report);
            }
            case CT -> {
                checkKeyword(textLower, DS_KEYWORDS, "Data Structures", subject, unitNum, topic, subResult, report);
            }
            case CS -> {
                checkKeyword(textLower, DS_KEYWORDS, "Data Structures", subject, unitNum, topic, subResult, report);
                checkKeyword(textLower, TTPS_KEYWORDS, "Transform Techniques & Statistics", subject, unitNum, topic, subResult, report);
            }
            default -> {}
        }
    }

    private void checkKeyword(String textLower, List<String> keywords, String sourceSubject,
                              ExtractedSubject subject, int unitNum, ExtractedTopic topic,
                              SubjectValidationResult subResult, ValidationReport report) {
        for (String kw : keywords) {
            if (textLower.contains(kw)) {
                String err = String.format("Cross-subject contamination detected in %s: topic '%s' contains keyword '%s' belonging to %s",
                        subject.getSubjectName(), topic.getTopicText().trim(), kw, sourceSubject);
                subResult.addError(err);
                report.addMessage(ValidationMessage.error("CONTAMINATION", err,
                        subject.getSubjectName(), subject.getCourseCode(), unitNum, topic.getPageNumber()));
                break;
            }
        }
    }

    private Subject matchOfficialSubject(ExtractedSubject subject) {
        if (subject == null) return null;
        if (subject.isOpenElective()) return Subject.OE;

        // 1. Match by Subject Name
        Subject matched = Subject.matchByText(subject.getSubjectName());
        if (matched != null) return matched;

        // 2. Match by Course Code
        if (subject.getCourseCode() != null && !subject.getCourseCode().isBlank()) {
            matched = Subject.matchByText(subject.getCourseCode());
            if (matched != null) return matched;
        }

        return null;
    }

    public static Integer parseCredits(String creditsStr) {
        if (creditsStr == null || creditsStr.isBlank()) return null;
        String digits = creditsStr.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer getExpectedUnitCount(Subject subject) {
        return EXPECTED_UNIT_COUNTS.get(subject);
    }
}
