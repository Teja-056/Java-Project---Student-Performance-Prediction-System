package studentperformance;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Only touches an explicitly configured database with a _test suffix. */
@EnabledIfEnvironmentVariable(named="PHASE2_TEST_DB_URL",matches=".*[/=][a-z0-9_]*_test([?;].*)?")
class SyllabusRepositoryIntegrationTest {
    private static String url,user,password;
    private static SyllabusRepository repository;
    @BeforeAll static void init() throws SQLException {
        url=System.getenv("PHASE2_TEST_DB_URL");
        Assumptions.assumeTrue(url!=null&&url.toLowerCase().matches(".*[/=][a-z0-9_]*_test([?;].*)?$"));
        user=System.getenv().getOrDefault("PHASE2_TEST_DB_USER","root"); password=System.getenv().getOrDefault("PHASE2_TEST_DB_PASSWORD","");
        repository=new SyllabusRepository(url,user,password);
        try(Connection c=DriverManager.getConnection(url,user,password)){new DatabaseMigrator().migrate(c);}
    }

    @Test void savesAndRetrievesApprovedSyllabusAndChangesUnitMapping() throws SQLException {
        String filename="phase3-test-"+UUID.randomUUID()+".pdf";
        SyllabusDocument document=new SyllabusDocument(filename);
        SyllabusSubject subject=new SyllabusSubject("Test Subject");
        subject.setDepartmentCode("CSE");subject.setSemester("III");subject.setCourseCode("UI25PC310CS");subject.setCredits("3");subject.setTotalMarks("40");
        SyllabusUnit unit=new SyllabusUnit(1,"Unit One");
        SyllabusTopic topic=new SyllabusTopic("Original topic wording"); topic.getSubtopics().add(new SyllabusTopic("Subtopic"));
        unit.getTopics().add(topic);subject.getUnits().add(unit);document.getSubjects().add(subject);
        long id=repository.saveApproved(document);
        try {
            SyllabusDocument loaded=repository.findApproved().stream().filter(d->d.getFilename().equals(filename)).findFirst().orElseThrow();
            assertEquals("CSE",loaded.getSubjects().get(0).getDepartmentCode());
            assertEquals("III",loaded.getSubjects().get(0).getSemester());
            assertEquals("UI25PC310CS",loaded.getSubjects().get(0).getCourseCode());
            assertEquals("Original topic wording",loaded.getSubjects().get(0).getUnits().get(0).getTopics().get(0).getText());
            assertEquals("Subtopic",loaded.getSubjects().get(0).getUnits().get(0).getTopics().get(0).getSubtopics().get(0).getText());
            repository.setInternalUnitMapping(1,List.of(1,3));
            assertEquals(List.of(1,3),repository.getInternalUnitMapping().get(1));
        } finally {
            try(Connection c=DriverManager.getConnection(url,user,password);PreparedStatement p=c.prepareStatement("DELETE FROM syllabus_documents WHERE document_id=?")){p.setLong(1,id);p.executeUpdate();}
            repository.setInternalUnitMapping(1,List.of(1,2));
        }
    }

    @Test void repeatedPhaseThreeMigrationPreservesExistingStudentRows() throws SQLException {
        String roll="1602-25-733-"+String.format("%03d",new java.util.Random().nextInt(1000));
        try(Connection c=DriverManager.getConnection(url,user,password);PreparedStatement p=c.prepareStatement(
                "INSERT INTO students(roll_number,name,cgpa,last_sgpa) VALUES(?,?,?,?)")){
            p.setString(1,roll);p.setString(2,"Migration Preservation Test");p.setDouble(3,7);p.setDouble(4,6.5);p.executeUpdate();
        }
        try {
            try(Connection c=DriverManager.getConnection(url,user,password)){new DatabaseMigrator().migrate(c);}
            try(Connection c=DriverManager.getConnection(url,user,password)){new DatabaseMigrator().migrate(c);}
            try(Connection c=DriverManager.getConnection(url,user,password);PreparedStatement p=c.prepareStatement("SELECT name FROM students WHERE roll_number=?")){
                p.setString(1,roll);try(ResultSet rows=p.executeQuery()){assertTrue(rows.next());assertEquals("Migration Preservation Test",rows.getString(1));}
            }
        } finally {
            try(Connection c=DriverManager.getConnection(url,user,password);PreparedStatement p=c.prepareStatement("DELETE FROM students WHERE roll_number=?")){p.setString(1,roll);p.executeUpdate();}
        }
    }
}
