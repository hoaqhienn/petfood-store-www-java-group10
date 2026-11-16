package vn.edu.dhktpm19att.group10.petfood.pet;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Pet profiles owned by the signed-in customer (F11, BR21, UC18). Another owner's pet is always 404. */
@RestController
@RequestMapping("/api/v1/me/pets")
class PetProfileController {
    private static final ZoneId STORE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<String> SPECIES = Set.of("DOG", "CAT");
    private static final Set<String> FIELDS = Set.of("name", "species", "birthDate", "estimatedAgeMonths",
            "weightGrams", "preferences", "version");
    private static final int MAX_PETS = 20;
    private static final String SELECT = """
            SELECT id, name, species, birth_date, estimated_age_months, weight_grams, preferences, created_at, version
            FROM pet_profile
            """;
    private static final RowMapper<Pet> PET = (rs, row) -> new Pet(rs.getString("id"), rs.getString("name"),
            rs.getString("species"), rs.getObject("birth_date", LocalDate.class),
            (Integer) rs.getObject("estimated_age_months"), (Integer) rs.getObject("weight_grams"),
            rs.getString("preferences"), rs.getObject("created_at", OffsetDateTime.class).toInstant(),
            rs.getLong("version"));

    private final JdbcTemplate db;

    PetProfileController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Pet> list(Authentication auth) {
        long owner = activeOwner(auth, false);
        return db.query(SELECT + " WHERE owner_id = ? ORDER BY id", PET, owner);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Pet detail(Authentication auth, @PathVariable String id) {
        return find(activeOwner(auth, false), petId(id));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Pet> create(Authentication auth, @RequestBody JsonNode body) {
        long owner = activeOwner(auth, true);
        whitelist(body);
        if (body.has("version")) throw invalid("Không gửi version khi tạo mới.");
        var values = values(body, null);
        // The owner row is locked FOR UPDATE, so concurrent creates cannot pass the limit together.
        long count = db.queryForObject("SELECT count(*) FROM pet_profile WHERE owner_id = ?", Long.class, owner);
        if (count >= MAX_PETS) throw new Rejected(422, "PET_LIMIT", "Mỗi tài khoản lưu tối đa " + MAX_PETS + " thú cưng.");
        Long id = db.queryForObject("""
                INSERT INTO pet_profile (owner_id, name, species, birth_date, estimated_age_months, weight_grams,
                                         preferences)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, owner, values.name(), values.species(), values.birthDate(),
                values.estimatedAgeMonths(), values.weightGrams(), values.preferences());
        return ResponseEntity.status(HttpStatus.CREATED).body(find(owner, id));
    }

    @PatchMapping("/{id}")
    @Transactional
    public Pet update(Authentication auth, @PathVariable String id, @RequestBody JsonNode body) {
        long owner = activeOwner(auth, true);
        long petId = petId(id);
        whitelist(body);
        JsonNode version = body.get("version");
        if (version == null || !version.isIntegralNumber() || !version.canConvertToLong() || version.longValue() < 0) {
            throw invalid("Thiếu version hợp lệ.");
        }
        var current = db.query(SELECT + " WHERE id = ? AND owner_id = ? FOR UPDATE", PET, petId, owner);
        if (current.isEmpty()) throw missing();
        if (current.get(0).version() != version.longValue()) {
            throw new Rejected(409, "VERSION_CONFLICT", "Hồ sơ đã thay đổi. Hãy tải lại.");
        }
        var values = values(body, current.get(0));
        db.update("""
                UPDATE pet_profile SET name = ?, species = ?, birth_date = ?, estimated_age_months = ?,
                    weight_grams = ?, preferences = ?, version = version + 1
                WHERE id = ?
                """, values.name(), values.species(), values.birthDate(), values.estimatedAgeMonths(),
                values.weightGrams(), values.preferences(), petId);
        return find(owner, petId);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable String id) {
        long owner = activeOwner(auth, true);
        if (db.update("DELETE FROM pet_profile WHERE id = ? AND owner_id = ?", petId(id), owner) == 0) throw missing();
        return ResponseEntity.noContent().build();
    }

    /** Builds the stored values, keeping the current ones for fields a PATCH does not send. */
    private static Pet values(JsonNode body, Pet current) {
        boolean create = current == null;
        String name = current == null ? null : current.name();
        if (create || body.has("name")) {
            JsonNode value = body.get("name");
            if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().strip().length() > 60) {
                throw invalid("Tên thú cưng phải có từ 1 đến 60 ký tự.");
            }
            name = value.asText().strip();
        }
        String species = current == null ? null : current.species();
        if (create || body.has("species")) {
            JsonNode value = body.get("species");
            if (value == null || !value.isTextual() || !SPECIES.contains(value.asText())) {
                throw invalid("Loài phải là DOG hoặc CAT.");
            }
            species = value.asText();
        }
        LocalDate birthDate = current == null ? null : current.birthDate();
        if (body.has("birthDate")) {
            JsonNode value = body.get("birthDate");
            if (value.isNull()) {
                birthDate = null;
            } else {
                try {
                    birthDate = LocalDate.parse(value.asText());
                } catch (DateTimeParseException exception) {
                    throw invalid("Ngày sinh phải có dạng YYYY-MM-DD.");
                }
                if (!value.isTextual() || birthDate.isAfter(LocalDate.now(STORE_ZONE))
                        || birthDate.isBefore(LocalDate.of(1990, 1, 1))) {
                    throw invalid("Ngày sinh không hợp lệ.");
                }
            }
        }
        Integer age = current == null ? null : current.estimatedAgeMonths();
        if (body.has("estimatedAgeMonths")) age = optionalInt(body.get("estimatedAgeMonths"), 0, 360, "Tuổi ước lượng");
        Integer weight = current == null ? null : current.weightGrams();
        if (body.has("weightGrams")) weight = optionalInt(body.get("weightGrams"), 1, 200_000, "Cân nặng");
        String preferences = current == null ? null : current.preferences();
        if (body.has("preferences")) {
            JsonNode value = body.get("preferences");
            if (value.isNull() || (value.isTextual() && value.asText().isBlank())) {
                preferences = null;
            } else if (value.isTextual() && value.asText().strip().length() <= 500) {
                preferences = value.asText().strip();
            } else {
                throw invalid("Sở thích tối đa 500 ký tự.");
            }
        }
        return new Pet(null, name, species, birthDate, age, weight, preferences, null, 0);
    }

    private static Integer optionalInt(JsonNode value, int min, int max, String label) {
        if (value.isNull()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < min || value.intValue() > max) {
            throw invalid(label + " phải là số nguyên từ " + min + " đến " + max + ".");
        }
        return value.intValue();
    }

    private static void whitelist(JsonNode body) {
        if (body == null || !body.isObject()) throw invalid("Nội dung phải là object JSON.");
        var names = body.fieldNames();
        while (names.hasNext()) {
            String field = names.next();
            // ownerId or id in the body never decides ownership; the owner always comes from the Session.
            if (!FIELDS.contains(field)) throw invalid("Không được gửi trường " + field + ".");
        }
    }

    private Pet find(long owner, long id) {
        var pets = db.query(SELECT + " WHERE id = ? AND owner_id = ?", PET, id, owner);
        if (pets.isEmpty()) throw missing();
        return pets.get(0);
    }

    /**
     * Checks the account is still ACTIVE, so a locked user loses access on the next request. Writes lock the
     * account row; read-only transactions cannot take row locks in PostgreSQL.
     */
    private long activeOwner(Authentication auth, boolean lock) {
        long actor = Long.parseLong(auth.getName());
        var active = db.queryForList("""
                SELECT id FROM user_account WHERE id = ? AND status = 'ACTIVE' AND role IN ('CUSTOMER', 'ADMIN')
                """ + (lock ? " FOR UPDATE" : ""), Long.class, actor);
        if (active.isEmpty()) throw new Rejected(403, "ACCOUNT_LOCKED", "Tài khoản không thể quản lý hồ sơ thú cưng.");
        return actor;
    }

    private static long petId(String id) {
        if (!id.matches("[1-9][0-9]{0,17}")) throw missing();
        return Long.parseLong(id);
    }

    private static Rejected missing() {
        return new Rejected(404, "PET_NOT_FOUND", "Không tìm thấy hồ sơ thú cưng.");
    }

    private static Rejected invalid(String message) {
        return new Rejected(422, "INVALID_INPUT", message);
    }

    @ExceptionHandler(Rejected.class)
    ResponseEntity<?> rejected(Rejected error) {
        return error(error.status, error.code, error.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<?> malformed() {
        return error(400, "INVALID_REQUEST", "Nội dung yêu cầu không hợp lệ.");
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> unavailable() {
        return error(503, "DEPENDENCY_UNAVAILABLE", "Tạm thời không thể xử lý hồ sơ thú cưng.");
    }

    private static ResponseEntity<?> error(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message,
                "fieldErrors", List.of(), "traceId", UUID.randomUUID().toString()));
    }

    record Pet(String id, String name, String species, LocalDate birthDate, Integer estimatedAgeMonths,
            Integer weightGrams, String preferences, Instant createdAt, long version) {}

    static class Rejected extends RuntimeException {
        final int status;
        final String code;

        Rejected(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
