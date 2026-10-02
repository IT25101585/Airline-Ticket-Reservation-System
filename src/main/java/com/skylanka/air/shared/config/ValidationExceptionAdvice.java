package com.skylanka.air.shared.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.io.IOException;

/**
 * Several screens in this app (fleet management, marketing, complaint handling,
 * support edits, and others) validate input by throwing IllegalArgumentException /
 * IllegalStateException, or rely on a @RequestParam enum (FlightStatus, SeatStatus,
 * Role, ComplaintStatus, ...) to reject a bad value during request binding.
 *
 * Each of those controller methods is reached only from its own form, so a bad value
 * there almost always means something odd happened (stale form, hand-crafted
 * request, etc.) rather than something the page needs a bespoke message for. Without
 * this advice, any of those cases falls through to the container's default error
 * page. This advice instead sends the user back to the page they came from with a
 * plain-language explanation (shown via the shared nav banner, see the flashError
 * model attribute below), and returns a JSON 400 for API clients instead of an HTML
 * error page.
 */
@ControllerAdvice
public class ValidationExceptionAdvice {

    private static final String SESSION_KEY = "flashError";

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, ConstraintViolationException.class})
    public void handleValidation(
            Exception e,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        String message = e.getMessage() == null || e.getMessage().isBlank()
                ? "That request could not be completed. Please check the details and try again."
                : e.getMessage();

        respond(request, response, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public void handleTypeMismatch(
            MethodArgumentTypeMismatchException e,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        respond(request, response, "Please choose a valid value for '" + e.getName() + "'.");
    }

    private void respond(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + message.replace("\"", "'") + "\"}");
            return;
        }

        request.getSession().setAttribute(SESSION_KEY, message);
        String referer = request.getHeader("Referer");
        response.sendRedirect((referer == null || referer.isBlank()) ? "/" : referer);
    }

    /**
     * Surfaces the message stashed above (if any) to whichever page the user lands
     * on next, then clears it so it doesn't linger across further navigation. The
     * shared nav fragment renders it, so it shows up no matter which page it is.
     */
    @ModelAttribute("flashError")
    public String flashError(HttpSession session) {
        Object value = session.getAttribute(SESSION_KEY);
        if (value != null) {
            session.removeAttribute(SESSION_KEY);
        }
        return value == null ? null : value.toString();
    }
}
