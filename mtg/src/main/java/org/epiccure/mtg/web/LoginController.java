package org.epiccure.mtg.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Staff sign-in page (templates/login.html). Spring Security still handles the POST to /login. */
@Controller
public class LoginController {

    private final boolean defaultPasswords;

    public LoginController(@Value("${app.users.volunteer-password:volunteer}") String volunteerPassword,
                           @Value("${app.users.manager-password:manager}") String managerPassword) {
        // Only show the demo-account hint while the built-in dev passwords are in use (never on Render with real ones).
        this.defaultPasswords = "volunteer".equals(volunteerPassword) && "manager".equals(managerPassword);
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("demoHint", defaultPasswords);
        return "login";
    }
}
