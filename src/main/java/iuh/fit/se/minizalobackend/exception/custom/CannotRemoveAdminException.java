package iuh.fit.se.minizalobackend.exception.custom;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class CannotRemoveAdminException extends RuntimeException {
    public CannotRemoveAdminException(String message) {
        super(message);
    }
}
