package studentperformance;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Scanner;
import java.util.stream.Collectors;

/** Console entry point: collects input, calls the repository, and displays reports. */
public class StudentPerformanceSystem {
    private final Scanner scanner = new Scanner(System.in);
    private final StudentRepository repository = new StudentRepository();
    private final PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
    private final CsvExporter csvExporter = new CsvExporter();
    private final AiPredictionService aiPredictionService = new AiPredictionService();
    private AiService aiService = AiServiceFactory.createService();
    private final PdfReportGenerator pdfReportGenerator = new PdfReportGenerator();
    private final SyllabusParser syllabusParser = new SyllabusParser();
    private final SyllabusRepository syllabusRepository = new SyllabusRepository();
    private final ExcelImporter excelImporter = new ExcelImporter();
    private List<Student> students = new ArrayList<>();
    private Map<Integer, List<Integer>> internalUnitMapping = Map.of(1, List.of(1, 2), 2, List.of(3, 4, 5));

    public static void main(String[] args) {
        new StudentPerformanceSystem().run();
    }

    private void run() {
        try {
            repository.initializeSchema();
            internalUnitMapping = syllabusRepository.getInternalUnitMapping();
            System.out.println("Database connected. Student tables are ready.");
            DatabaseMigrator.MigrationReport migration = repository.getMigrationReport();
            if (migration.archivedLegacyRows() > 0)
                System.out.println("Legacy migration: archived " + migration.archivedLegacyRows()
                        + " original mark row(s); review these historical values before entering modern-format marks.");
        } catch (SQLException ex) {
            System.out.println("Could not connect to MySQL: " + ex.getMessage());
            System.out.println("Follow the database setup steps in README.md, then start the program again.");
            return;
        }

        boolean running = true;
        while (running) {
            System.out.println("\n=== STUDENT PERFORMANCE SYSTEM ===");
            System.out.println("1. Add student");
            System.out.println("2. View all students");
            System.out.println("3. Search and view student profile");
            System.out.println("4. Update student details");
            System.out.println("5. Update academic marks");
            System.out.println("6. Delete student");
            System.out.println("7. Compare two students");
            System.out.println("8. Rank students by overall percentage");
            System.out.println("9. Show students requiring support");
            System.out.println("10. Export CSV report");
            System.out.println("11. Get AI performance analysis");
            System.out.println("12. Upload, extract and review syllabus PDF");
            System.out.println("13. View approved syllabuses");
            System.out.println("14. Configure internal-to-unit mapping");
            System.out.println("15. Bulk import students from Excel (.xlsx/.xls)");
            System.out.println("16. Generate sample Excel import template");
            System.out.println("0. Exit");
            int choice = readInt("Choose: ");
            try {
                switch (choice) {
                    case 1 -> addStudent();
                    case 2 -> showAllStudents();
                    case 3 -> searchStudent();
                    case 4 -> updateStudentDetails();
                    case 5 -> updateAcademicMarks();
                    case 6 -> deleteStudent();
                    case 7 -> compareStudents();
                    case 8 -> rankStudents();
                    case 9 -> showSupportList();
                    case 10 -> exportCsv();
                    case 11 -> getAiAnalysis();
                    case 12 -> uploadSyllabus();
                    case 13 -> viewSyllabuses();
                    case 14 -> configureUnitMapping();
                    case 15 -> bulkImportExcel();
                    case 16 -> generateExcelTemplate();
                    case 0 -> running = false;
                    default -> System.out.println("Choose one of the menu options.");
                }
            } catch (SQLException | IllegalArgumentException ex) {
                System.out.println("Could not complete that action: " + ex.getMessage());
            }
        }
        System.out.println("Goodbye.");
    }

    private void uploadSyllabus() throws SQLException {
        System.out.print("PDF file path: ");
        String value = scanner.nextLine().trim().replaceAll("^\"|\"$", "");
        try {
            SyllabusDocument document = syllabusParser.parse(Path.of(value));
            System.out.println("Extracted syllabus candidates:");
            String department = readText("Department code to import [CSE]: ", "CSE").toUpperCase();
            String semester = readText("Semester to import [III]: ", "III").toUpperCase();
            List<SyllabusSubject> allSubjects = document.getSubjects();
            List<SyllabusSubject> eligible = SyllabusSelector.relevantSubjects(document, department, semester);

            System.out.println("\nAll detected subjects in PDF (" + allSubjects.size() + " total):");
            for (int i = 0; i < allSubjects.size(); i++) {
                SyllabusSubject s = allSubjects.get(i);
                String exclusion = SyllabusSelector.exclusionReason(s, department, semester);
                String status = (exclusion == null) ? "[RECOMMENDED / ELIGIBLE]" : "[EXCLUDED: " + exclusion + "]";
                System.out.println((i + 1) + ". " + s.getName() + " (" + s.getCourseCode() + ") [" + s.getCategory() + "] "
                        + "| Dept=" + s.getDepartmentCode() + ", Sem=" + s.getSemester() + " " + status);
            }

            if (allSubjects.isEmpty()) {
                System.out.println("No subjects were detected automatically. Nothing will be imported automatically.");
                showRawText(document);
                System.out.print("Build a subject structure manually from the PDF? Type YES: ");
                if (!scanner.nextLine().trim().equals("YES")) return;
                document.getSubjects().clear();
                document.getSubjects().add(readManualSubject(department, semester));
            } else {
                System.out.println("\nImport options:");
                System.out.println("- 'ALL': Import recommended/eligible subjects (" + eligible.size() + " subjects)");
                System.out.println("- 'ALL_DETECTED': Import all " + allSubjects.size() + " detected subjects");
                System.out.println("- Or enter comma-separated subject numbers (e.g. 1, 2, 4) to include any specific subjects manually");
                System.out.print("Your choice: ");
                String selection = scanner.nextLine().trim();
                List<SyllabusSubject> selected = new ArrayList<>();
                if (selection.equalsIgnoreCase("ALL")) {
                    selected.addAll(eligible);
                } else if (selection.equalsIgnoreCase("ALL_DETECTED")) {
                    selected.addAll(allSubjects);
                } else {
                    for (String token : selection.split(",")) {
                        if (token.isBlank()) continue;
                        try {
                            int index = Integer.parseInt(token.trim());
                            if (index < 1 || index > allSubjects.size()) throw new NumberFormatException();
                            if (!selected.contains(allSubjects.get(index - 1))) {
                                selected.add(allSubjects.get(index - 1));
                            }
                        } catch (NumberFormatException ex) {
                            throw new IllegalArgumentException("Select ALL, ALL_DETECTED, or valid subject numbers (1-" + allSubjects.size() + ") separated by commas.");
                        }
                    }
                }
                if (selected.isEmpty()) {
                    System.out.println("No subjects selected; nothing was saved.");
                    return;
                }
                document.getSubjects().clear();
                document.getSubjects().addAll(selected);
            }
            System.out.println("Selected syllabus structure (review before saving):");
            displaySyllabus(document);
            boolean reviewing = true;
            while (reviewing) {
                System.out.println("Review: 0 source text, 1 subject name, 2 unit title, 3 topic, 4 add topic, 5 delete topic, 6 finish, 7 metadata, 8 add missing subject, 9 correct course type");
                int action = readInt("Choose: ");
                switch (action) {
                    case 0 -> showRawText(document);
                    case 1 -> editSyllabusSubject(document);
                    case 2 -> editSyllabusUnit(document);
                    case 3 -> editSyllabusTopic(document);
                    case 4 -> addSyllabusTopic(document);
                    case 5 -> deleteSyllabusTopic(document);
                    case 6 -> reviewing = false;
                    case 7 -> editSyllabusMetadata(document);
                    case 8 -> document.getSubjects().add(readManualSubject(department, semester));
                    case 9 -> editSyllabusCategory(document);
                    default -> System.out.println("Choose one of the review options.");
                }
                if (reviewing) displaySyllabus(document);
            }
            System.out.print("Save this reviewed structure as approved? Type YES: ");
            if (scanner.nextLine().trim().equals("YES")) {
                long id = syllabusRepository.saveApproved(document);
                System.out.println("Approved syllabus saved to MySQL with ID " + id + ".");
            } else System.out.println("Syllabus was not saved.");
        } catch (java.io.IOException ex) {
            System.out.println("Could not extract syllabus: " + ex.getMessage());
        }
    }

    private void displaySyllabus(SyllabusDocument document) {
        System.out.println("\nSyllabus: " + document.getFilename());
        for (int s=0;s<document.getSubjects().size();s++) {
            SyllabusSubject subject=document.getSubjects().get(s);
            displaySubjectSummary(s+1, subject);
            for (int u=0;u<subject.getUnits().size();u++) {
                SyllabusUnit unit=subject.getUnits().get(u);
                System.out.println("  Unit " + unit.getNumber() + " - " + unit.getTitle());
                for (int t=0;t<unit.getTopics().size();t++) displayTopic(unit.getTopics().get(t), "    " + (t+1) + ". ");
            }
        }
    }
    private void displaySubjectSummary(int index,SyllabusSubject subject){
        System.out.println(index+". "+subject.getName()+" | department="+subject.getDepartmentCode()+" | semester="+subject.getSemester()
                +" | code="+subject.getCourseCode()+" | credits="+subject.getCredits()+" | marks="+subject.getTotalMarks()
                +" | type="+subject.getCategory()+" | reason="+subject.getClassificationReason());
    }
    private void showRawText(SyllabusDocument document){System.out.println("\n--- Original extracted PDF text ---\n"+document.getRawExtractedText()+"\n--- End extracted text ---");}
    private SyllabusSubject readManualSubject(String department,String semester){
        System.out.print("Subject name exactly as printed: ");SyllabusSubject subject=new SyllabusSubject(scanner.nextLine());
        subject.setDepartmentCode(readText("Department code ["+department+"]: ",department));subject.setSemester(readText("Semester ["+semester+"]: ",semester));
        subject.setCourseCode(readText("Course code if visible (blank if unavailable): ",""));
        int count=readInt("Number of units to enter: ",1,20);
        for(int i=0;i<count;i++){
            int number=readInt("Unit number: ",1,20);System.out.print("Unit heading/title exactly as printed: ");SyllabusUnit unit=new SyllabusUnit(number,scanner.nextLine());
            System.out.println("Enter topic lines separated by |, preserving the PDF wording (leave blank if none): ");String topics=scanner.nextLine();
            for(String topic:topics.split("\\|"))if(!topic.isBlank())unit.getTopics().add(new SyllabusTopic(topic.trim()));subject.getUnits().add(unit);
        }
        return subject;
    }
    private void displayTopic(SyllabusTopic topic,String prefix){
        System.out.println(prefix+topic.getText());
        for(int i=0;i<topic.getSubtopics().size();i++)displayTopic(topic.getSubtopics().get(i),prefix+"  ");
    }
    private SyllabusSubject chooseSyllabusSubject(SyllabusDocument d){
        if(d.getSubjects().isEmpty())return null;
        int i=readInt("Subject number (1-"+d.getSubjects().size()+"): ",1,d.getSubjects().size()); return d.getSubjects().get(i-1);
    }
    private SyllabusUnit chooseSyllabusUnit(SyllabusSubject s){
        if(s==null||s.getUnits().isEmpty())return null;
        int i=readInt("Unit list position (1-"+s.getUnits().size()+"): ",1,s.getUnits().size()); return s.getUnits().get(i-1);
    }
    private void editSyllabusSubject(SyllabusDocument d){SyllabusSubject s=chooseSyllabusSubject(d);if(s!=null){System.out.print("Correct subject name: ");s.setName(scanner.nextLine());}}
    private void editSyllabusMetadata(SyllabusDocument d){SyllabusSubject s=chooseSyllabusSubject(d);if(s==null)return;
        s.setDepartmentCode(readText("Department ["+s.getDepartmentCode()+"]: ",s.getDepartmentCode()));s.setSemester(readText("Semester ["+s.getSemester()+"]: ",s.getSemester()));
        s.setCourseCode(readText("Course code ["+s.getCourseCode()+"]: ",s.getCourseCode()));s.setCredits(readText("Credits ["+s.getCredits()+"]: ",s.getCredits()));s.setTotalMarks(readText("Marks ["+s.getTotalMarks()+"]: ",s.getTotalMarks()));}
    private void editSyllabusCategory(SyllabusDocument d){SyllabusSubject s=chooseSyllabusSubject(d);if(s==null)return;
        SyllabusCategory[] categories=SyllabusCategory.values();for(int i=0;i<categories.length;i++)System.out.println((i+1)+". "+categories[i]);
        int choice=readInt("Correct course type: ",1,categories.length);s.setCategory(categories[choice-1]);}
    private void editSyllabusUnit(SyllabusDocument d){SyllabusUnit u=chooseSyllabusUnit(chooseSyllabusSubject(d));if(u!=null){System.out.print("Correct unit title: ");u.setTitle(scanner.nextLine());}}
    private void editSyllabusTopic(SyllabusDocument d){SyllabusUnit u=chooseSyllabusUnit(chooseSyllabusSubject(d));if(u==null||u.getTopics().isEmpty())return;int i=readInt("Topic position (1-"+u.getTopics().size()+"): ",1,u.getTopics().size());System.out.print("Correct topic wording: ");u.getTopics().get(i-1).setText(scanner.nextLine());}
    private void addSyllabusTopic(SyllabusDocument d){SyllabusUnit u=chooseSyllabusUnit(chooseSyllabusSubject(d));if(u!=null){System.out.print("Topic to add: ");u.getTopics().add(new SyllabusTopic(scanner.nextLine()));}}
    private void deleteSyllabusTopic(SyllabusDocument d){SyllabusUnit u=chooseSyllabusUnit(chooseSyllabusSubject(d));if(u!=null&&!u.getTopics().isEmpty()){int i=readInt("Topic position to delete (1-"+u.getTopics().size()+"): ",1,u.getTopics().size());u.getTopics().remove(i-1);}}
    private void viewSyllabuses() throws SQLException {
        List<SyllabusDocument> documents=syllabusRepository.findApproved();
        if(documents.isEmpty())System.out.println("No approved syllabuses stored yet.");else documents.forEach(this::displaySyllabus);
        System.out.println("Internal-unit mapping: "+syllabusRepository.getInternalUnitMapping());
    }
    private void configureUnitMapping() throws SQLException {
        int internal=readInt("Internal number (1 or 2): ",1,2);System.out.print("Unit numbers separated by commas: ");
        List<Integer> units=new ArrayList<>();for(String part:scanner.nextLine().split(","))try{units.add(Integer.parseInt(part.trim()));}catch(NumberFormatException ex){throw new IllegalArgumentException("Enter unit numbers separated by commas.");}
        syllabusRepository.setInternalUnitMapping(internal,units);internalUnitMapping=syllabusRepository.getInternalUnitMapping();System.out.println("Updated mapping: "+internalUnitMapping);
    }

    private void addStudent() throws SQLException {
        System.out.print("Name: ");
        String name = scanner.nextLine().trim();
        System.out.print("Roll number (1602-25-733-XXX): ");
        String roll = scanner.nextLine().trim();
        if (repository.findByRollNumber(roll) != null) {
            System.out.println("That roll number already exists. Use the update options instead.");
            return;
        }
        double cgpa = readDouble("CGPA (0-10): ", 0, 10);
        double sgpa = readDouble("Last semester SGPA (0-10): ", 0, 10);
        EnumMap<Subject, SubjectAssessment> first = readMarks("Internal 1");
        EnumMap<Subject, SubjectAssessment> second = readMarks("Internal 2");

        Student student = new Student(name, roll, cgpa, sgpa, first, second);
        repository.save(student);
        System.out.println("Student added to MySQL.");
        displayStudent(student);
    }

    private EnumMap<Subject, SubjectAssessment> readMarks(String examName) {
        System.out.println("Enter " + examName + " scores. The three assignments and three quizzes are each out of 5.");
        EnumMap<Subject, SubjectAssessment> marks = new EnumMap<>(Subject.class);
        for (Subject subject : Subject.values()) {
            int exam = readInt(subject.getDisplayName() + " exam marks (out of " + subject.getMaximumExamMarks() + "): ", 0, subject.getMaximumExamMarks());
            List<Integer> assignments = new ArrayList<>();
            List<Integer> quizzes = new ArrayList<>();
            for (int i = 1; i <= 3; i++) assignments.add(readInt(subject.getDisplayName() + " Assignment " + i + " (out of 5): ", 0, 5));
            for (int i = 1; i <= 3; i++) quizzes.add(readInt(subject.getDisplayName() + " Quiz " + i + " (out of 5): ", 0, 5));
            marks.put(subject, new SubjectAssessment(exam, assignments, quizzes));
        }
        return marks;
    }

    private void showAllStudents() throws SQLException {
        students = repository.findAll();
        if (students.isEmpty()) System.out.println("No student records yet.");
        else for (Student student : students) displayStudent(student);
    }

    private void searchStudent() throws SQLException {
        System.out.print("Roll number to search: ");
        Student student = repository.findByRollNumber(scanner.nextLine().trim());
        if (student == null) System.out.println("Student not found.");
        else displayStudent(student);
    }

    private void updateStudentDetails() throws SQLException {
        System.out.print("Roll number to update: ");
        String roll = scanner.nextLine().trim();
        Student student = repository.findByRollNumber(roll);
        if (student == null) { System.out.println("Student not found."); return; }
        System.out.print("New name (press Enter to keep " + student.getName() + "): ");
        String name = scanner.nextLine().trim();
        if (name.isBlank()) name = student.getName();
        double cgpa = readDouble("CGPA (0-10): ", 0, 10);
        double sgpa = readDouble("Last semester SGPA (0-10): ", 0, 10);
        System.out.println(repository.updateDetails(roll, name, cgpa, sgpa) ? "Student details updated." : "Student not found.");
    }

    private void updateAcademicMarks() throws SQLException {
        System.out.print("Roll number to update marks: ");
        String roll = scanner.nextLine().trim();
        Student oldStudent = repository.findByRollNumber(roll);
        if (oldStudent == null) { System.out.println("Student not found."); return; }
        EnumMap<Subject, SubjectAssessment> first = readMarks("Internal 1");
        EnumMap<Subject, SubjectAssessment> second = readMarks("Internal 2");
        Student updated = Student.fromDatabase(oldStudent.getName(), roll, oldStudent.getCgpa(), oldStudent.getLastSemesterSgpa(), first, second, List.of());
        repository.updateMarks(updated);
        System.out.println("Academic marks updated.");
        displayStudent(repository.findByRollNumber(roll));
    }

    private void showSupportList() throws SQLException {
        students = repository.findAll();
        List<Student> supportList = students.stream()
                .filter(analyzer::requiresSupport)
                .collect(Collectors.toList());
        System.out.println("Students below the configured " + analyzer.getSupportThresholdPercent() + "% threshold (complete marks only):");
        if (supportList.isEmpty()) System.out.println("No students currently meet the support rule.");
        else for (Student student : supportList) displayStudent(student);
    }

    private void deleteStudent() throws SQLException {
        System.out.print("Roll number to delete: ");
        String roll = scanner.nextLine().trim();
        Student student = repository.findByRollNumber(roll);
        if (student == null) { System.out.println("Roll number not found."); return; }
        System.out.print("Delete " + student.getName() + " (" + roll + ")? Type YES to confirm: ");
        if (!scanner.nextLine().trim().equals("YES")) { System.out.println("Delete cancelled."); return; }
        System.out.println(repository.delete(roll) ? "Student deleted. Archived legacy marks remain preserved." : "Roll number not found.");
    }

    private void compareStudents() throws SQLException {
        System.out.print("First roll number: ");
        Student first = repository.findByRollNumber(scanner.nextLine().trim());
        System.out.print("Second roll number: ");
        Student second = repository.findByRollNumber(scanner.nextLine().trim());
        if (first == null || second == null) { System.out.println("Both students must exist to compare."); return; }
        System.out.println("\nComparison: " + first.getName() + " vs " + second.getName());
        System.out.println("Recorded overall internal: " + formatPercentage(first) + " vs " + formatPercentage(second));
        for (Subject subject : Subject.values())
            System.out.printf("%-6s %s: %s | %s: %s%n", subject.name(), first.getName(), formatSubjectPercent(first, subject),
                    second.getName(), formatSubjectPercent(second, subject));
    }

    private double averageSubjectPercent(Student student, Subject subject) {
        double first = analyzer.subjectPercentage(student, 1, subject), second = analyzer.subjectPercentage(student, 2, subject);
        if (Double.isNaN(first)) return Double.isNaN(second) ? Double.NaN : second;
        if (Double.isNaN(second)) return first;
        return (first + second) / 2;
    }

    private String formatSubjectPercent(Student student, Subject subject) {
        double value = averageSubjectPercent(student, subject);
        return Double.isNaN(value) ? "incomplete" : String.format(java.util.Locale.ROOT, "%.2f%%", value);
    }

    private String formatPercentage(Student student) {
        String value = String.format(java.util.Locale.ROOT, "%.2f%%", analyzer.currentInternalPercentage(student));
        return analyzer.hasBothInternals(student) ? value : value + " (partial marks)";
    }

    private void rankStudents() throws SQLException {
        students = repository.findAll();
        List<Student> ranked = students.stream().filter(analyzer::hasBothInternals)
                .sorted(java.util.Comparator.comparingDouble(analyzer::currentInternalPercentage).reversed()).toList();
        if (ranked.isEmpty()) { System.out.println("No student has complete marks in both internals yet."); return; }
        System.out.println("Ranking (complete assessments only):");
        for (int i = 0; i < ranked.size(); i++)
            System.out.printf("%d. %s (%s) - %.2f%%%n", i + 1, ranked.get(i).getName(), ranked.get(i).getRollNumber(), analyzer.currentInternalPercentage(ranked.get(i)));
    }

    private void exportCsv() throws SQLException {
        students = repository.findAll();
        try {
            Path output = Path.of("student-performance-report.csv");
            csvExporter.export(students, output);
            System.out.println("CSV report written to " + output.toAbsolutePath());
        } catch (java.io.IOException ex) {
            System.out.println("Could not write CSV: " + ex.getMessage());
        }
    }

    private void getAiAnalysis() throws SQLException {
        if (!aiService.isConfigured() && !aiPredictionService.isConfigured()) {
            System.out.println("AI provider (" + aiService.getProviderName() + ") is not configured.");
            System.out.println("For Google Gemini (Free Tier):");
            System.out.println("  1. Obtain a free API key at https://aistudio.google.com/app/apikey");
            System.out.println("  2. In Windows PowerShell run: [System.Environment]::SetEnvironmentVariable(\"GEMINI_API_KEY\", \"your_key\", \"User\")");
            System.out.println("     or create a .env file with GEMINI_API_KEY=your_key");
            System.out.println("  3. (Optional) Set GEMINI_MODEL (defaults to " + GeminiService.DEFAULT_MODEL + ").");
            System.out.println("For OpenAI: Set AI_PROVIDER=openai and OPENAI_API_KEY in environment.");
            System.out.print("\nEnter your API key now to continue for this session (or press Enter to exit): ");
            String manualKey = scanner.nextLine().trim();
            if (manualKey.isEmpty()) {
                return;
            }
            if (aiService.getProviderName().equalsIgnoreCase("Google Gemini")) {
                aiService = new GeminiService(null, manualKey, EnvConfig.get("GEMINI_MODEL", GeminiService.DEFAULT_MODEL));
            } else {
                aiService = new OpenAIService(null, manualKey, EnvConfig.get("OPENAI_MODEL", OpenAIService.DEFAULT_MODEL));
            }
        }
        students = repository.findAll();
        if (students.isEmpty()) { System.out.println("No student records yet."); return; }
        System.out.print("Enter student roll number for AI analysis: ");
        String roll = scanner.nextLine().trim();
        Student selected = students.stream().filter(student -> student.getRollNumber().equals(roll)).findFirst().orElse(null);
        if (selected == null) { System.out.println("Roll number not found."); return; }
        if (!analyzer.hasBothInternals(selected)) {
            System.out.println("Complete modern marks are required for AI analysis. This student's assessment data is incomplete.");
            return;
        }

        List<SyllabusDocument> approvedSyllabuses = syllabusRepository.findApproved();
        System.out.println("Contacting " + aiService.getProviderName() + " (" + aiService.getModel() + ") with anonymized performance metrics...");
        System.out.println("Note: Student name and roll number are NOT sent to ensure PII privacy.");

        try {
            AiAnalysisResult result = aiService.analyzeStudent(selected, analyzer, approvedSyllabuses);
            System.out.println(result.formatConsoleReport());

            System.out.print("Export comprehensive report as PDF? Type YES: ");
            if (scanner.nextLine().trim().equalsIgnoreCase("YES")) {
                Path pdfPath = Path.of("student-performance-report-" + selected.getRollNumber().replace(":", "-") + ".pdf");
                pdfReportGenerator.generateReport(selected, analyzer, result, pdfPath);
                System.out.println("PDF report generated successfully at: " + pdfPath.toAbsolutePath());
            }
        } catch (java.io.IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
            System.out.println(aiService.getProviderName() + " structured analysis failed: " + ex.getMessage());
            System.out.println("Falling back to standard prediction service...");
            try {
                System.out.println(aiPredictionService.predict(selected, analyzer));
            } catch (Exception fallbackEx) {
                System.out.println("Local rule-based category: " + analyzer.prediction(selected));
            }
        }
    }

    private void displayStudent(Student student) {
        System.out.println("\n----------------------------------------");
        System.out.println("Name: " + student.getName() + " | Roll number: " + student.getRollNumber());
        System.out.printf("CGPA: %.2f | Last semester SGPA: %.2f%n", student.getCgpa(), student.getLastSemesterSgpa());
        for (int exam = 1; exam <= 2; exam++) {
            System.out.println("Internal " + exam + " subject marks: " + subjectMarks(student, exam));
            if (analyzer.isInternalComplete(student, exam))
                System.out.printf("Internal %d total: %.2f/%d (%.2f%%)%n", exam, analyzer.totalMarks(student, exam),
                        Subject.maximumTotal(), analyzer.percentage(student, exam));
            else System.out.printf("Internal %d provisional percentage: %.2f%% (incomplete recorded marks)%n", exam, analyzer.percentage(student, exam));
        }
        System.out.println("Recorded overall internal percentage: " + formatPercentage(student));
        System.out.println("Unit review: " + analyzer.unitAdvice(student, internalUnitMapping));
        System.out.println("Rule-based prediction: " + analyzer.prediction(student));
        System.out.println("Strongest recorded subject: " + displaySubject(analyzer.strongestSubject(student)));
        System.out.println("Weakest recorded subject: " + displaySubject(analyzer.weakestSubject(student)));
        System.out.println("Subject trend (Internal 2 minus Internal 1): " + subjectTrends(student));
        if (!student.getLegacyMarks().isEmpty()) {
            System.out.println("Archived original legacy marks:");
            for (LegacyMark mark : student.getLegacyMarks())
                System.out.println("  Internal " + mark.internalNumber() + " / " + mark.subjectName() + ": " + mark.marks());
        }
        System.out.println("----------------------------------------");
    }

    private String subjectMarks(Student student, int exam) {
        StringBuilder result = new StringBuilder();
        for (Subject subject : Subject.values()) {
            if (!result.isEmpty()) result.append(", ");
            SubjectAssessment assessment = student.getMarks(exam).get(subject);
            if (assessment == null) { result.append(subject.getDisplayName()).append(" missing"); continue; }
            result.append(subject.getDisplayName()).append(" exam ").append(assessment.getExamMarks())
                    .append(" + assignment marks ").append(assessment.getAssignmentMarks())
                    .append(" (avg ").append(String.format(java.util.Locale.ROOT, "%.2f", assessment.assignmentAverage()))
                    .append(") + quiz marks ").append(assessment.getQuizMarks())
                    .append(" (avg ").append(String.format(java.util.Locale.ROOT, "%.2f", assessment.quizAverage()))
                    .append(") = ").append(String.format(java.util.Locale.ROOT, "%.2f/%d",
                            assessment.totalMarks(), subject.getMaximumTotalMarks()));
        }
        return result.toString();
    }

    private String displaySubject(Subject subject) { return subject == null ? "Not enough marks" : subject.getDisplayName(); }

    private String subjectTrends(Student student) {
        StringBuilder result = new StringBuilder();
        for (Subject subject : Subject.values()) {
            if (!result.isEmpty()) result.append(", ");
            double change = analyzer.subjectChange(student, subject);
            result.append(subject.getDisplayName()).append(' ');
            if (Double.isNaN(change)) result.append("not comparable");
            else result.append(String.format(java.util.Locale.ROOT, "%+.2f percentage points", change));
        }
        return result.toString();
    }

    private int readInt(String prompt) {
        while (true) {
            try { System.out.print(prompt); return Integer.parseInt(scanner.nextLine().trim()); }
            catch (NumberFormatException ex) { System.out.println("Enter a whole number."); }
        }
    }

    private String readText(String prompt,String fallback){System.out.print(prompt);String value=scanner.nextLine().trim();return value.isBlank()?fallback:value;}

    private int readInt(String prompt, int min, int max) {
        while (true) {
            int value = readInt(prompt);
            if (value >= min && value <= max) return value;
            System.out.println("Enter a mark from " + min + " to " + max + ".");
        }
    }

    private double readDouble(String prompt) {
        while (true) {
            try { System.out.print(prompt); return Double.parseDouble(scanner.nextLine().trim()); }
            catch (NumberFormatException ex) { System.out.println("Enter a number, for example 8.25."); }
        }
    }

    private double readDouble(String prompt, double min, double max) {
        while (true) {
            double value = readDouble(prompt);
            if (Double.isFinite(value) && value >= min && value <= max) return value;
            System.out.println("Enter a value from " + min + " to " + max + ".");
        }
    }

    private void generateExcelTemplate() {
        System.out.print("Enter template destination file name or path [student_import_template.xlsx]: ");
        String pathStr = scanner.nextLine().trim();
        if (pathStr.isBlank()) pathStr = "student_import_template.xlsx";
        try {
            Path path = Path.of(pathStr);
            excelImporter.generateTemplate(path);
            System.out.println("Excel template successfully generated at: " + path.toAbsolutePath());
            System.out.println("Fill student data following the embedded 'Instructions' tab and use menu option 15 to import.");
        } catch (java.io.IOException ex) {
            System.out.println("Could not create template: " + ex.getMessage());
        }
    }

    private void bulkImportExcel() throws SQLException {
        System.out.print("Enter Excel file path (.xlsx or .xls): ");
        String inputPath = scanner.nextLine().trim().replaceAll("^\"|\"$", "");
        if (inputPath.isBlank()) {
            System.out.println("File path cannot be blank.");
            return;
        }
        Path path = Path.of(inputPath);
        if (!java.nio.file.Files.isRegularFile(path)) {
            System.out.println("File not found: " + path);
            return;
        }

        try {
            Set<String> existingRolls = repository.findAllRollNumbers();
            ExcelImporter.ExcelImportResult result = excelImporter.parseAndValidate(path, existingRolls);

            System.out.println("\n=== EXCEL BULK IMPORT PREVIEW ===");
            System.out.println("File: " + path.getFileName());
            System.out.println("Total student rows read: " + result.getTotalRowsRead());
            System.out.println("Valid records ready to import: " + result.getValidCount());
            System.out.println("Invalid records with errors: " + result.getInvalidCount());
            System.out.println("Duplicate roll numbers in file: " + result.getDuplicateRollsInFile().size());
            System.out.println("Conflicts with existing database roll numbers: " + result.getExistingRollsInDatabase().size());

            if (result.hasErrors()) {
                System.out.println("\n--- Validation Errors Detected (" + result.getInvalidCount() + " rows) ---");
                int limit = Math.min(10, result.getInvalidRows().size());
                for (int i = 0; i < limit; i++) {
                    System.out.println("  " + result.getInvalidRows().get(i).format());
                }
                if (result.getInvalidRows().size() > 10) {
                    System.out.println("  ... and " + (result.getInvalidRows().size() - 10) + " more error rows.");
                }

                System.out.print("\nExport detailed error report to CSV? Type YES: ");
                if (scanner.nextLine().trim().equalsIgnoreCase("YES")) {
                    Path errPath = Path.of("excel-import-errors.csv");
                    excelImporter.exportErrorReport(result.getInvalidRows(), errPath);
                    System.out.println("Error report written to: " + errPath.toAbsolutePath());
                }
            }

            if (result.getValidCount() == 0) {
                System.out.println("\nNo valid student records found to import.");
                return;
            }

            System.out.println("\nValid records preview:");
            for (int i = 0; i < Math.min(5, result.getValidCount()); i++) {
                Student s = result.getValidStudents().get(i);
                System.out.printf("  %d. %s (%s) | CGPA: %.2f | SGPA: %.2f%n", i + 1, s.getName(), s.getRollNumber(), s.getCgpa(), s.getLastSemesterSgpa());
            }
            if (result.getValidCount() > 5) {
                System.out.println("  ... and " + (result.getValidCount() - 5) + " more valid records.");
            }

            System.out.print("\nProceed to import " + result.getValidCount() + " valid student records into MySQL? Type YES to confirm: ");
            if (scanner.nextLine().trim().equals("YES")) {
                int imported = repository.saveBatch(result.getValidStudents());
                System.out.println("Successfully imported " + imported + " students into MySQL with full transaction safety!");
            } else {
                System.out.println("Import cancelled. No records were added to MySQL.");
            }
        } catch (java.io.IOException ex) {
            System.out.println("Failed to read Excel file: " + ex.getMessage());
        }
    }
}
