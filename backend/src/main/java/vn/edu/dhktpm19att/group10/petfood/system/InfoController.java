package vn.edu.dhktpm19att.group10.petfood.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InfoController {
    @GetMapping("/api/v1/info")
    public ProjectInfo info() {
        return new ProjectInfo("PetFood", "Good food. Happy pets.", "0.1.0", "scaffold");
    }

    public record ProjectInfo(String name, String tagline, String version, String stage) { }
}

