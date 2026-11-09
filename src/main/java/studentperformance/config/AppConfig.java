package studentperformance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import studentperformance.*;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Spring Boot Configuration registering existing domain and repository services as beans.
 * Reuses existing implementations without duplicating logic.
 */
@Configuration
public class AppConfig {

    @Value("${spring.datasource.url:jdbc:mysql://localhost:3306/student_performance?serverTimezone=UTC}")
    private String dbUrl;

    @Value("${spring.datasource.username:root}")
    private String dbUser;

    @Value("${spring.datasource.password:Teja.J@2007}")
    private String dbPassword;

    @PostConstruct
    public void initDatabase() {
        // Automatically ensure schema and migrations are initialized on application startup
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
            DatabaseMigrator migrator = new DatabaseMigrator();
            migrator.migrate(conn);
        } catch (Exception e) {
            System.err.println("Database migration check on startup: " + e.getMessage());
        }
    }

    @Bean
    public StudentRepository studentRepository() {
        return new StudentRepository(dbUrl, dbUser, dbPassword);
    }

    @Bean
    public DatasetRepository datasetRepository() {
        return new DatasetRepository(dbUrl, dbUser, dbPassword);
    }

    @Bean
    public SyllabusRepository syllabusRepository() {
        return new SyllabusRepository(dbUrl, dbUser, dbPassword);
    }

    @Bean
    public PerformanceAnalyzer performanceAnalyzer() {
        return new PerformanceAnalyzer();
    }

    @Bean
    public AiService aiService() {
        return AiServiceFactory.createService();
    }

    @Bean
    public ExcelImporter excelImporter() {
        return new ExcelImporter();
    }

    @Bean
    public SyllabusParser syllabusParser() {
        return new SyllabusParser();
    }

    @Bean
    public PdfReportGenerator pdfReportGenerator() {
        return new PdfReportGenerator();
    }
}
