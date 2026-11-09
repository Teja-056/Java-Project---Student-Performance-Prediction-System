package studentperformance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles persistence, status lifecycle, and transactional switching of student datasets in MySQL.
 */
@Repository
public class DatasetRepository {

    private final String url;
    private final String user;
    private final String password;

    public DatasetRepository(
            @Value("${spring.datasource.url:jdbc:mysql://localhost:3306/student_performance?serverTimezone=UTC}") String url,
            @Value("${spring.datasource.username:root}") String user,
            @Value("${spring.datasource.password:Teja.J@2007}") String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    public List<Dataset> findAll() throws SQLException {
        String sql = "SELECT * FROM datasets ORDER BY is_active DESC, dataset_id DESC";
        List<Dataset> list = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        }
        return list;
    }

    public Dataset findById(Long id) throws SQLException {
        if (id == null) return null;
        String sql = "SELECT * FROM datasets WHERE dataset_id = ?";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public Dataset findActive() throws SQLException {
        String sql = "SELECT * FROM datasets WHERE is_active = TRUE LIMIT 1";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return mapRow(rs);
            }
        }
        return null;
    }

    public Long save(Dataset dataset) throws SQLException {
        if (dataset.getDatasetId() == null) {
            String sql = "INSERT INTO datasets (dataset_code, name, original_filename, storage_path, semester, academic_year, "
                    + "total_rows, valid_count, invalid_count, is_active, status, validation_report, error_message) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection conn = DriverManager.getConnection(url, user, password);
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, dataset.getDatasetCode());
                ps.setString(2, dataset.getName());
                ps.setString(3, dataset.getOriginalFilename());
                ps.setString(4, dataset.getStoragePath());
                ps.setString(5, dataset.getSemester());
                ps.setString(6, dataset.getAcademicYear());
                ps.setInt(7, dataset.getTotalRows());
                ps.setInt(8, dataset.getValidCount());
                ps.setInt(9, dataset.getInvalidCount());
                ps.setBoolean(10, dataset.isActive());
                ps.setString(11, dataset.getStatus());
                ps.setString(12, dataset.getValidationReport());
                ps.setString(13, dataset.getErrorMessage());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        long id = keys.getLong(1);
                        dataset.setDatasetId(id);
                        return id;
                    }
                }
            }
        } else {
            String sql = "UPDATE datasets SET dataset_code=?, name=?, original_filename=?, storage_path=?, semester=?, academic_year=?, "
                    + "total_rows=?, valid_count=?, invalid_count=?, is_active=?, status=?, validation_report=?, error_message=? "
                    + "WHERE dataset_id=?";
            try (Connection conn = DriverManager.getConnection(url, user, password);
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dataset.getDatasetCode());
                ps.setString(2, dataset.getName());
                ps.setString(3, dataset.getOriginalFilename());
                ps.setString(4, dataset.getStoragePath());
                ps.setString(5, dataset.getSemester());
                ps.setString(6, dataset.getAcademicYear());
                ps.setInt(7, dataset.getTotalRows());
                ps.setInt(8, dataset.getValidCount());
                ps.setInt(9, dataset.getInvalidCount());
                ps.setBoolean(10, dataset.isActive());
                ps.setString(11, dataset.getStatus());
                ps.setString(12, dataset.getValidationReport());
                ps.setString(13, dataset.getErrorMessage());
                ps.setLong(14, dataset.getDatasetId());
                ps.executeUpdate();
                return dataset.getDatasetId();
            }
        }
        return dataset.getDatasetId();
    }

    /**
     * Atomically switches active dataset: deactivates any existing active dataset and activates target dataset.
     */
    public boolean activate(Long datasetId) throws SQLException {
        if (datasetId == null) return false;
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            conn.setAutoCommit(false);
            try {
                // Verify target dataset exists and is in READY state
                try (PreparedStatement check = conn.prepareStatement("SELECT status FROM datasets WHERE dataset_id = ?")) {
                    check.setLong(1, datasetId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        String status = rs.getString("status");
                        if ("FAILED".equalsIgnoreCase(status)) {
                            conn.rollback();
                            throw new IllegalArgumentException("Cannot activate a failed dataset.");
                        }
                    }
                }

                // Deactivate currently active dataset
                try (PreparedStatement deact = conn.prepareStatement("UPDATE datasets SET is_active = FALSE WHERE is_active = TRUE")) {
                    deact.executeUpdate();
                }

                // Activate requested dataset
                try (PreparedStatement act = conn.prepareStatement("UPDATE datasets SET is_active = TRUE WHERE dataset_id = ?")) {
                    act.setLong(1, datasetId);
                    int affected = act.executeUpdate();
                    if (affected == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public boolean delete(Long datasetId) throws SQLException {
        if (datasetId == null) return false;
        String sql = "DELETE FROM datasets WHERE dataset_id = ?";
        try (Connection conn = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, datasetId);
            return ps.executeUpdate() > 0;
        }
    }

    private Dataset mapRow(ResultSet rs) throws SQLException {
        return new Dataset(
                rs.getLong("dataset_id"),
                rs.getString("dataset_code"),
                rs.getString("name"),
                rs.getString("original_filename"),
                rs.getString("storage_path"),
                rs.getString("semester"),
                rs.getString("academic_year"),
                rs.getInt("total_rows"),
                rs.getInt("valid_count"),
                rs.getInt("invalid_count"),
                rs.getBoolean("is_active"),
                rs.getString("status"),
                rs.getString("validation_report"),
                rs.getString("error_message"),
                rs.getTimestamp("created_at"),
                rs.getTimestamp("updated_at")
        );
    }
}
